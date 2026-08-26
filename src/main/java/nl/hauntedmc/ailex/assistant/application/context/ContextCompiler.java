package nl.hauntedmc.ailex.assistant.application.context;

import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles independently sourced assistant context into a bounded prompt.
 *
 * <p>The current request is always retained. Active dialogue and trusted live state outrank durable memory and
 * retrieved knowledge. Source ceilings scale with the route budget instead of imposing tiny global constants, so a
 * complex grounded request can use materially more context without making ordinary chat expensive.</p>
 */
public final class ContextCompiler {

    private static final int CHARACTERS_PER_TOKEN_ESTIMATE = 4;

    public CompiledContext compile(
            AssistantMode mode,
            int maximumInputTokens,
            String basePrompt,
            AssistantDialogueContext dialogue,
            String liveContext,
            String durableMemory,
            List<ContextSource> evidence,
            String historicalChat
    ) {
        int budget = Math.max(512, maximumInputTokens);
        StringBuilder output = new StringBuilder();
        Map<String, Integer> tokensBySource = new LinkedHashMap<>();

        int requestCap = Math.min(budget, Math.max(1_000, budget / 4));
        int used = appendRequired(output, tokensBySource, "request", basePrompt, requestCap);
        int remaining = Math.max(0, budget - used);

        if (dialogue != null && dialogue.active() && remaining > 0) {
            int allocated = Math.min(remaining, dialogueCap(mode, budget));
            used += append(output, tokensBySource, "dialogue", "Active player-assistant dialogue",
                    dialogueText(dialogue), allocated, true);
            remaining = Math.max(0, budget - used);
        }
        if (!blank(liveContext) && remaining > 0) {
            int allocated = Math.min(remaining, liveCap(mode, budget));
            used += append(output, tokensBySource, "live", "Trusted live Minecraft context", liveContext,
                    allocated, false);
            remaining = Math.max(0, budget - used);
        }
        if (!blank(durableMemory) && remaining > 0) {
            int allocated = Math.min(remaining, memoryCap(mode, budget));
            used += append(output, tokensBySource, "memory", "Relevant saved assistant memory", durableMemory,
                    allocated, false);
            remaining = Math.max(0, budget - used);
        }
        if (evidence != null && !evidence.isEmpty() && remaining > 0) {
            int evidenceBudget = Math.min(remaining, evidenceCap(mode, budget));
            for (ContextSource source : evidence) {
                if (evidenceBudget <= 0 || remaining <= 0) {
                    break;
                }
                int allocated = Math.min(evidenceBudget, remaining);
                int consumed = append(output, tokensBySource, "evidence:" + source.id(),
                        "Trusted knowledge source " + source.id() + " — " + source.title(),
                        source.text(), allocated, false);
                evidenceBudget -= consumed;
                used += consumed;
                remaining = Math.max(0, budget - used);
            }
        }
        if (!blank(historicalChat) && remaining > 0) {
            used += append(output, tokensBySource, "history", "Relevant recent untrusted chat history", historicalChat,
                    remaining, true);
        }

        String prompt = output.toString().trim();
        return new CompiledContext(prompt, estimateTokens(prompt), Map.copyOf(tokensBySource));
    }

    private int dialogueCap(AssistantMode mode, int budget) {
        return switch (mode) {
            case FAST, HANDOFF -> Math.max(700, budget / 4);
            case GROUNDED -> Math.max(1_600, budget / 3);
            case DELIBERATE -> Math.max(2_400, budget / 3);
        };
    }

    private int liveCap(AssistantMode mode, int budget) {
        return switch (mode) {
            case FAST, HANDOFF -> Math.max(900, budget / 3);
            case GROUNDED -> Math.max(2_400, budget * 2 / 5);
            case DELIBERATE -> Math.max(4_000, budget * 2 / 5);
        };
    }

    private int memoryCap(AssistantMode mode, int budget) {
        return switch (mode) {
            case FAST, HANDOFF -> Math.max(700, budget / 4);
            case GROUNDED -> Math.max(1_800, budget / 3);
            case DELIBERATE -> Math.max(3_000, budget / 3);
        };
    }

    private int evidenceCap(AssistantMode mode, int budget) {
        return switch (mode) {
            case FAST, HANDOFF -> Math.max(900, budget * 2 / 5);
            case GROUNDED -> Math.max(3_000, budget * 3 / 5);
            case DELIBERATE -> Math.max(6_000, budget * 3 / 5);
        };
    }

    private int appendRequired(
            StringBuilder output,
            Map<String, Integer> tokensBySource,
            String sourceId,
            String value,
            int maximumTokens
    ) {
        String clipped = clip(value, maximumTokens, false);
        appendRaw(output, clipped);
        int tokens = estimateTokens(clipped);
        tokensBySource.put(sourceId, tokens);
        return tokens;
    }

    private int append(
            StringBuilder output,
            Map<String, Integer> tokensBySource,
            String sourceId,
            String heading,
            String value,
            int maximumTokens,
            boolean keepTail
    ) {
        if (maximumTokens <= 0 || blank(value)) {
            return 0;
        }
        int headingTokens = estimateTokens(heading) + 4;
        if (maximumTokens <= headingTokens + 4) {
            return 0;
        }
        String clipped = clip(value, maximumTokens - headingTokens, keepTail);
        if (clipped.isBlank()) {
            return 0;
        }
        String section = "[" + heading + "]\n" + clipped;
        appendRaw(output, section);
        int tokens = estimateTokens(section);
        tokensBySource.put(sourceId, tokens);
        return tokens;
    }

    private void appendRaw(StringBuilder output, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!output.isEmpty()) {
            output.append("\n\n");
        }
        output.append(value.trim());
    }

    private String dialogueText(AssistantDialogueContext dialogue) {
        StringBuilder value = new StringBuilder("pending_answer=").append(dialogue.pendingAnswer());
        if (dialogue.previousIntent() != null) {
            value.append(" | previous_intent=")
                    .append(dialogue.previousIntent().name().toLowerCase(java.util.Locale.ROOT));
        }
        if (!dialogue.recentTurns().isBlank()) {
            value.append("\n").append(dialogue.recentTurns());
            return value.toString();
        }
        if (!dialogue.previousUserMessage().isBlank()) {
            value.append("\nprevious_user=").append(dialogue.previousUserMessage());
        }
        if (!dialogue.previousAssistantMessage().isBlank()) {
            value.append("\nprevious_assistant=").append(dialogue.previousAssistantMessage());
        }
        return value.toString();
    }

    private String clip(String value, int maximumTokens, boolean keepTail) {
        String normalized = value == null ? "" : value.trim();
        if (maximumTokens <= 0 || normalized.isBlank()) {
            return "";
        }
        int maximumCharacters = Math.max(1, maximumTokens * CHARACTERS_PER_TOKEN_ESTIMATE);
        if (normalized.length() <= maximumCharacters) {
            return normalized;
        }
        if (keepTail) {
            int start = Math.max(0, normalized.length() - maximumCharacters + 1);
            return "…" + normalized.substring(start);
        }
        return normalized.substring(0, Math.max(0, maximumCharacters - 1)) + "…";
    }

    public static int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(1, (value.length() + CHARACTERS_PER_TOKEN_ESTIMATE - 1)
                / CHARACTERS_PER_TOKEN_ESTIMATE);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ContextSource(String id, String title, String text) {
        public ContextSource {
            id = id == null ? "unknown" : id.replaceAll("\\s+", " ").trim();
            title = title == null ? "" : title.replaceAll("\\s+", " ").trim();
            text = text == null ? "" : text.trim();
        }
    }

    public record CompiledContext(String prompt, int estimatedTokens, Map<String, Integer> tokensBySource) {
    }
}
