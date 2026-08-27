package nl.hauntedmc.ailex.assistant.application.context;

import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles independently sourced assistant context into a bounded prompt.
 *
 * <p>The current request is always retained. Active source families reserve enough budget to remain represented before
 * larger earlier sections can consume the window. Low-authority history is rendered early and current trusted evidence
 * remains near the end so relevant support is not silently starved or buried in a long prompt.</p>
 */
public final class ContextCompiler {

    public static final String DIALOGUE_HEADING = "Active player-assistant dialogue";
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
        Map<String, Integer> tokensBySource = new LinkedHashMap<>();
        List<RenderedSection> trustedSections = new ArrayList<>();

        int requestCap = Math.min(budget, Math.max(1_000, budget / 4));
        String request = clip(basePrompt, requestCap, false);
        int used = estimateTokens(request);
        tokensBySource.put("request", used);
        int remaining = Math.max(0, budget - used);

        boolean hasDialogue = dialogue != null && dialogue.active();
        boolean hasLive = !blank(liveContext);
        boolean hasMemory = !blank(durableMemory);
        boolean hasEvidence = evidence != null && !evidence.isEmpty();
        int liveReserve = hasLive ? sourceReserve(liveCap(mode, budget), budget, 8, 600) : 0;
        int memoryReserve = hasMemory ? sourceReserve(memoryCap(mode, budget), budget, 10, 500) : 0;
        int evidenceReserve = hasEvidence ? sourceReserve(evidenceCap(mode, budget), budget, 5, 800) : 0;

        RenderedSection dialogueSection = RenderedSection.empty();
        if (hasDialogue && remaining > 0) {
            int available = Math.max(0, remaining - liveReserve - memoryReserve - evidenceReserve);
            int allocated = Math.min(available, dialogueCap(mode, budget));
            dialogueSection = render("dialogue", DIALOGUE_HEADING, dialogueText(dialogue), allocated, true);
            used += dialogueSection.tokens();
            remaining = Math.max(0, budget - used);
            record(tokensBySource, dialogueSection);
        }

        RenderedSection liveSection = RenderedSection.empty();
        if (hasLive && remaining > 0) {
            int available = Math.max(liveReserve, remaining - memoryReserve - evidenceReserve);
            int allocated = Math.min(remaining, Math.min(available, liveCap(mode, budget)));
            liveSection = render(
                    "live", "Trusted live Minecraft context", liveContext, allocated, false
            );
            used += liveSection.tokens();
            remaining = Math.max(0, budget - used);
            record(tokensBySource, liveSection);
        }

        RenderedSection memorySection = RenderedSection.empty();
        if (hasMemory && remaining > 0) {
            int available = Math.max(memoryReserve, remaining - evidenceReserve);
            int allocated = Math.min(remaining, Math.min(available, memoryCap(mode, budget)));
            memorySection = render(
                    "memory", "Relevant saved assistant memory", durableMemory, allocated, false
            );
            used += memorySection.tokens();
            remaining = Math.max(0, budget - used);
            record(tokensBySource, memorySection);
        }

        if (hasEvidence && remaining > 0) {
            int evidenceBudget = Math.min(remaining, evidenceCap(mode, budget));
            for (ContextSource source : evidence) {
                if (evidenceBudget <= 0 || remaining <= 0) {
                    break;
                }
                int allocated = Math.min(evidenceBudget, remaining);
                RenderedSection section = render(
                        "evidence:" + source.id(),
                        "Trusted knowledge source " + source.id() + " — " + source.title(),
                        source.text(), allocated, false
                );
                evidenceBudget -= section.tokens();
                used += section.tokens();
                remaining = Math.max(0, budget - used);
                record(tokensBySource, section);
                if (!section.text().isBlank()) {
                    trustedSections.add(section);
                }
            }
        }

        RenderedSection historySection = RenderedSection.empty();
        if (!blank(historicalChat) && remaining > 0) {
            historySection = render(
                    "history", "Relevant recent untrusted chat history", historicalChat, remaining, true
            );
            record(tokensBySource, historySection);
        }

        // Render low-authority historical material early; keep current trusted evidence close to the final user request tail.
        StringBuilder output = new StringBuilder();
        appendRaw(output, request);
        appendRaw(output, historySection.text());
        appendRaw(output, dialogueSection.text());
        appendRaw(output, memorySection.text());
        trustedSections.forEach(section -> appendRaw(output, section.text()));
        appendRaw(output, liveSection.text());

        String prompt = output.toString().trim();
        return new CompiledContext(prompt, estimateTokens(prompt), Map.copyOf(tokensBySource));
    }

    private int sourceReserve(int cap, int budget, int divisor, int minimum) {
        return Math.min(cap, Math.max(minimum, budget / divisor));
    }

    private int dialogueCap(AssistantMode mode, int budget) {
        return switch (mode) {
            case FAST, HANDOFF -> Math.max(900, budget / 3);
            case GROUNDED -> Math.max(1_800, budget / 3);
            case DELIBERATE -> Math.max(2_800, budget / 3);
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
            case FAST, HANDOFF -> Math.max(800, budget / 4);
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

    private RenderedSection render(
            String sourceId,
            String heading,
            String value,
            int maximumTokens,
            boolean keepTail
    ) {
        if (maximumTokens <= 0 || blank(value)) {
            return RenderedSection.empty();
        }
        int headingTokens = estimateTokens(heading) + 4;
        if (maximumTokens <= headingTokens + 4) {
            return RenderedSection.empty();
        }
        String clipped = clip(value, maximumTokens - headingTokens, keepTail);
        if (clipped.isBlank()) {
            return RenderedSection.empty();
        }
        String section = "[" + heading + "]\n" + clipped;
        return new RenderedSection(sourceId, section, estimateTokens(section));
    }

    private void record(Map<String, Integer> tokensBySource, RenderedSection section) {
        if (section != null && !section.sourceId().isBlank() && section.tokens() > 0) {
            tokensBySource.put(section.sourceId(), section.tokens());
        }
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
            value.append("\nprevious_intent=")
                    .append(dialogue.previousIntent().name().toLowerCase(java.util.Locale.ROOT));
        }
        if (!dialogue.recentTurns().isBlank()) {
            value.append('\n').append(dialogue.recentTurns());
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

    private record RenderedSection(String sourceId, String text, int tokens) {
        private static RenderedSection empty() {
            return new RenderedSection("", "", 0);
        }
    }
}
