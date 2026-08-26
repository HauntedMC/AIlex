package nl.hauntedmc.ailex.assistant.application.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.application.prompt.AssistantPromptComposer;
import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.LocalKnowledgeIndex;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bounded model-driven read loop. The planner may request additional information but never executes writes or receives
 * arbitrary plugin access. Every tool is registered explicitly and executes deterministic Java over already-authorized
 * memory, reviewed knowledge or a frozen safe Paper snapshot.
 */
public final class AssistantReadAgent {

    private static final int MAX_OBSERVATION_CHARACTERS = 4_000;
    private final JavaPlugin plugin;
    private final OpenAiToolPlanningClient plannerClient;
    private final AssistantToolRegistry toolRegistry;
    private final AssistantPromptComposer promptComposer = new AssistantPromptComposer();

    public AssistantReadAgent(
            JavaPlugin plugin,
            LocalKnowledgeIndex knowledgeIndex,
            AssistantMemoryService memoryService,
            AssistantExperienceMemoryService experienceMemory
    ) {
        this(plugin, knowledgeIndex, memoryService, experienceMemory, new OpenAiToolPlanningClient(plugin));
    }

    /** Test seam for deterministic planner/tool-loop regression coverage. */
    AssistantReadAgent(
            JavaPlugin plugin,
            LocalKnowledgeIndex knowledgeIndex,
            AssistantMemoryService memoryService,
            AssistantExperienceMemoryService experienceMemory,
            OpenAiToolPlanningClient plannerClient
    ) {
        this.plugin = plugin;
        this.plannerClient = plannerClient;
        this.toolRegistry = new AssistantToolRegistry(knowledgeIndex, memoryService, experienceMemory);
    }

    public AgentEnrichment enrich(
            AssistantService.PreparedRequest request,
            List<LocalKnowledgeIndex.KnowledgeChunk> initialEvidence,
            int maximumPlannerCalls,
            Duration remaining
    ) {
        if (!enabled() || request == null || maximumPlannerCalls <= 0 || !shouldRun(request, initialEvidence)) {
            return AgentEnrichment.empty();
        }
        List<JsonObject> tools = toolRegistry.definitions(request.settings());
        if (tools.isEmpty()) {
            return AgentEnrichment.empty();
        }
        List<JsonElement> history = new ArrayList<>();
        history.add(OpenAiToolPlanningClient.userMessage(plannerPrompt(request, initialEvidence)));
        StringBuilder context = new StringBuilder();
        Set<String> evidenceIds = new HashSet<>();
        Set<String> usedTools = new HashSet<>();
        Set<String> callFingerprints = new HashSet<>();
        int modelCalls = 0;
        int toolCalls = 0;
        int plannerInputTokens = 0;
        int plannerOutputTokens = 0;
        int maximumPerRound = maxToolCallsPerRound();
        long startedAtNanos = System.nanoTime();

        for (int round = 0; round < maximumPlannerCalls; round++) {
            long elapsedNanos = Math.max(0L, System.nanoTime() - startedAtNanos);
            Duration currentRemaining = remaining == null ? Duration.ZERO
                    : remaining.minusNanos(Math.min(remaining.toNanos(), elapsedNanos));
            Duration roundTimeout = remainingForRound(currentRemaining, modelCalls);
            if (roundTimeout.compareTo(Duration.ofSeconds(1)) < 0) {
                break;
            }
            OpenAiToolPlanningClient.PlanningResponse plan = plannerClient.plan(history, tools, roundTimeout);
            modelCalls++;
            plannerInputTokens += plan.inputTokens();
            plannerOutputTokens += plan.outputTokens();
            if (!plan.success() || plan.calls().isEmpty()) {
                break;
            }
            int callsThisRound = 0;
            for (OpenAiToolPlanningClient.FunctionCall call : plan.calls()) {
                if (callsThisRound >= maximumPerRound) {
                    break;
                }
                String fingerprint = call.name() + '|' + clean(call.arguments()).toLowerCase(Locale.ROOT);
                if (!callFingerprints.add(fingerprint)) {
                    history.add(OpenAiToolPlanningClient.functionCallInput(call));
                    history.add(OpenAiToolPlanningClient.functionOutput(
                            call.callId(), "Equivalent tool call already executed; use existing evidence or choose a different query."
                    ));
                    continue;
                }
                AssistantTool.ToolResult observation = toolRegistry.execute(request, call);
                history.add(OpenAiToolPlanningClient.functionCallInput(call));
                history.add(OpenAiToolPlanningClient.functionOutput(call.callId(), observation.output()));
                toolCalls++;
                callsThisRound++;
                usedTools.add(call.name());
                evidenceIds.addAll(observation.evidenceIds());
                if (!observation.output().isBlank()) {
                    if (!context.isEmpty()) {
                        context.append("\n\n");
                    }
                    context.append("[Read tool ").append(call.name()).append("]\n")
                            .append(observation.output());
                }
            }
            if (callsThisRound == 0) {
                break;
            }
        }
        return new AgentEnrichment(
                clip(context.toString()), Set.copyOf(evidenceIds), Set.copyOf(usedTools), modelCalls, toolCalls,
                plannerInputTokens, plannerOutputTokens
        );
    }

    public boolean enabled() {
        FileConfiguration config = plugin.getConfig();
        return config == null || config.getBoolean("openai.assistant.agent.enabled", true);
    }

    public Set<String> availableTools(AssistantService.PreparedRequest request) {
        return request == null ? Set.of() : toolRegistry.availableNames(request.settings());
    }

    /**
     * Information-gain gate: deterministic retrieval/live capture always gets first chance. A planner call is spent only
     * when a required source is missing, or when a temporal request needs history rather than the active memory view.
     */
    private boolean shouldRun(
            AssistantService.PreparedRequest request,
            List<LocalKnowledgeIndex.KnowledgeChunk> initialEvidence
    ) {
        if (request.analysis().mode() == AssistantMode.FAST || request.analysis().mode() == AssistantMode.HANDOFF) {
            return false;
        }
        boolean hasKnowledge = initialEvidence != null && !initialEvidence.isEmpty();
        boolean hasMemory = request.memory() != null && !request.memory().isBlank();
        boolean hasDirectLive = !request.snapshot().filtered(request.contextPlan().liveSources()).isBlank();
        boolean temporalMemoryRequest = requiresTemporalMemory(request.message());
        AssistantIntent intent = request.analysis().intent();
        return switch (intent) {
            case MEMORY_RECALL, EVENT_RECALL -> temporalMemoryRequest || !hasMemory;
            case LIVE_STATE -> !hasDirectLive;
            case SERVER_FACT -> !hasKnowledge;
            case CONTEXT_FOLLOWUP -> temporalMemoryRequest || (!hasMemory && !hasDirectLive && !hasKnowledge);
            case GAMEPLAY_HELP -> false;
            default -> false;
        };
    }

    private boolean requiresTemporalMemory(String message) {
        String normalized = clean(message).toLowerCase(Locale.ROOT);
        return normalized.matches(".*\\b(vorige|vroeger|eerder|toen|gisteren|vorige week|vorige maand|"
                + "last time|previous|previously|earlier|before|yesterday|last week|last month|when did|wanneer)\\b.*");
    }

    private String plannerPrompt(
            AssistantService.PreparedRequest request,
            List<LocalKnowledgeIndex.KnowledgeChunk> initialEvidence
    ) {
        String evidence = initialEvidence == null ? "none" : initialEvidence.stream()
                .map(LocalKnowledgeIndex.KnowledgeChunk::id)
                .limit(12)
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
        boolean memoryPresent = request.memory() != null && !request.memory().isBlank();
        Set<String> liveIds = request.snapshot().filtered(request.contextPlan().liveSources()).sourceIds();
        return "request=" + clean(request.message())
                + "
route=" + request.analysis().intent().name().toLowerCase(Locale.ROOT)
                + " mode=" + request.analysis().mode().name().toLowerCase(Locale.ROOT)
                + "
reviewed_evidence=" + evidence
                + " memory_present=" + memoryPresent
                + " live_evidence=" + (liveIds.isEmpty() ? "none" : String.join(",", liveIds))
                + "
" + promptComposer.plannerContract();
    }

    private Duration remainingForRound(Duration totalRemaining, int callsAlreadyMade) {
        if (totalRemaining == null || totalRemaining.isNegative() || totalRemaining.isZero()) {
            return Duration.ZERO;
        }
        long reserveMillis = 2_000L + callsAlreadyMade * 250L;
        return totalRemaining.minusMillis(Math.min(totalRemaining.toMillis(), reserveMillis));
    }

    private int maxToolCallsPerRound() {
        FileConfiguration config = plugin.getConfig();
        return config == null ? 2 : Math.clamp(config.getInt(
                "openai.assistant.agent.max_tool_calls_per_round", 2
        ), 1, 4);
    }

    private String clip(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= MAX_OBSERVATION_CHARACTERS
                ? normalized : normalized.substring(0, MAX_OBSERVATION_CHARACTERS - 1) + "…";
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public record AgentEnrichment(
            String context,
            Set<String> evidenceIds,
            Set<String> usedTools,
            int modelCalls,
            int toolCalls,
            int plannerInputTokens,
            int plannerOutputTokens
    ) {
        public AgentEnrichment {
            context = context == null ? "" : context.trim();
            evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
            usedTools = usedTools == null ? Set.of() : Set.copyOf(usedTools);
            modelCalls = Math.max(0, modelCalls);
            toolCalls = Math.max(0, toolCalls);
            plannerInputTokens = Math.max(0, plannerInputTokens);
            plannerOutputTokens = Math.max(0, plannerOutputTokens);
        }

        public static AgentEnrichment empty() {
            return new AgentEnrichment("", Set.of(), Set.of(), 0, 0, 0, 0);
        }

        public boolean usedToolsSuccessfully() {
            return toolCalls > 0 && !evidenceIds.isEmpty();
        }
    }
}
