package nl.hauntedmc.ailex.assistant.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.action.AssistantActionProposal;
import nl.hauntedmc.ailex.assistant.action.AssistantActionType;
import nl.hauntedmc.ailex.assistant.application.agent.AssistantReadAgent;
import nl.hauntedmc.ailex.assistant.application.agent.AssistantReadAgent.AgentEnrichment;
import nl.hauntedmc.ailex.assistant.application.context.AssistantLiveCapturePolicy;
import nl.hauntedmc.ailex.assistant.application.context.ContextCompiler;
import nl.hauntedmc.ailex.assistant.application.context.RequiredContextPlanner;
import nl.hauntedmc.ailex.assistant.application.inference.AssistantGenerationPolicy;
import nl.hauntedmc.ailex.assistant.application.inference.AssistantGroundingPolicy;
import nl.hauntedmc.ailex.assistant.application.prompt.AssistantPromptComposer;
import nl.hauntedmc.ailex.assistant.application.reliability.AssistantCircuitBreaker;
import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.application.routing.SemanticNeedPlanner;
import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantReply;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.LocalKnowledgeIndex;
import nl.hauntedmc.ailex.assistant.infrastructure.live.PaperLiveContextEnricher;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantRelationshipMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryCandidate;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryTopicView;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryRecord;
import nl.hauntedmc.ailex.infrastructure.openai.OpenAiResponsesClient;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Read-only cognitive assistant orchestration. Main-thread preparation freezes trusted Minecraft state; asynchronous
 * generation combines retrieval, a bounded model-driven read loop, claim-level grounding and verified experience.
 */
public final class AssistantService {

    private final AIlexPlugin plugin;
    private final LocalKnowledgeIndex knowledgeIndex;
    private final AssistantMemoryService memoryService;
    private final AssistantExperienceMemoryService experienceMemory;
    private final AssistantRelationshipMemoryService relationshipMemory;
    private final AssistantReadAgent readAgent;
    private volatile SemanticNeedPlanner semanticNeedPlanner;
    private final ContextCompiler contextCompiler = new ContextCompiler();
    private final AssistantPromptComposer promptComposer = new AssistantPromptComposer();
    private final MemoryTopicView memoryTopicView = new MemoryTopicView();
    private final RequiredContextPlanner contextPlanner = new RequiredContextPlanner();
    private final AtomicLong replies = new AtomicLong();
    private final AtomicLong verifiedReplies = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong agentPlannerCalls = new AtomicLong();
    private final AtomicLong agentToolCalls = new AtomicLong();
    private final AtomicLong agentPlannerInputTokens = new AtomicLong();
    private final AtomicLong agentPlannerOutputTokens = new AtomicLong();
    private final AtomicLong semanticRefinements = new AtomicLong();
    private final Map<String, AssistantCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, AssistantReply> staticReplyCache = new ConcurrentHashMap<>();

    public AssistantService(AIlexPlugin plugin) {
        this.plugin = plugin;
        this.knowledgeIndex = new LocalKnowledgeIndex(plugin);
        this.memoryService = plugin.getAssistantMemoryService();
        this.experienceMemory = new AssistantExperienceMemoryService(memoryService);
        this.relationshipMemory = new AssistantRelationshipMemoryService(memoryService);
        this.readAgent = new AssistantReadAgent(plugin, knowledgeIndex, memoryService, experienceMemory);
        this.semanticNeedPlanner = new SemanticNeedPlanner(knowledgeIndex.semanticEmbeddingProvider());
        Thread.ofVirtual().name("AIlex-SemanticRouter-Warmup").start(semanticNeedPlanner::warm);
    }

    public PreparedRequest prepare(Player player, NPC npc, String message, String systemPrompt, String userPrompt) {
        return prepare(player, npc, message, systemPrompt, userPrompt, "", AssistantDialogueContext.empty());
    }

    public PreparedRequest prepare(
            Player player,
            NPC npc,
            String message,
            String systemPrompt,
            String userPrompt,
            String trustedLiveMetadata
    ) {
        return prepare(player, npc, message, systemPrompt, userPrompt, trustedLiveMetadata, AssistantDialogueContext.empty());
    }

    /** Captures trusted context while Bukkit access is safe on the server thread. */
    public PreparedRequest prepare(
            Player player,
            NPC npc,
            String message,
            String systemPrompt,
            String userPrompt,
            String trustedLiveMetadata,
            AssistantDialogueContext dialogueContext
    ) {
        if (player == null) {
            throw new IllegalArgumentException("player is required");
        }
        AssistantSettings settings = AssistantSettings.from(plugin.getConfig());
        AssistantDialogueContext dialogue = dialogueContext == null ? AssistantDialogueContext.empty() : dialogueContext;
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(message, dialogue);
        String language = settings.languageDetection()
                ? AssistantIntentClassifier.detectLanguage(message, settings.defaultLanguage(), settings.allowedLanguages())
                : settings.defaultLanguage();

        UUID playerId = player.getUniqueId();
        if (memoryService != null && settings.toolAllowed("session")) {
            memoryService.observe(playerId, message);
            memoryService.rememberExplicitLanguagePreference(playerId, message);
            String preferredLanguage = memoryService.preferredLanguage(playerId);
            if (!preferredLanguage.isBlank()) {
                language = preferredLanguage;
            }
        }
        analysis = new AssistantIntentClassifier.Analysis(
                analysis.intent(), settings.resolveMode(analysis.mode()), language
        );

        RequiredContextPlanner.Plan plan = contextPlanner.plan(analysis.intent(), analysis.mode(), message, settings);
        boolean retrieveKnowledge = plan.knowledge();
        Set<RequiredContextPlanner.LiveSource> captureSources = AssistantLiveCapturePolicy.captureSources(
                plan, analysis.intent(), analysis.mode(), settings, readAgent.enabled()
        );
        LiveSnapshot snapshot = captureSources.isEmpty()
                ? LiveSnapshot.empty()
                : LiveSnapshot.capture(player, npc, message, captureSources);
        if (analysis.intent() == AssistantIntent.LIVE_STATE
                && trustedLiveMetadata != null && !trustedLiveMetadata.isBlank()) {
            snapshot = snapshot.withContext(trustedLiveMetadata, captureSources);
        }

        String npcMemoryId = npc == null ? "0" : String.valueOf(npc.getId());
        String memory = "";
        if (memoryService != null && settings.toolAllowed("session") && plan.durableMemory()) {
            memory = memoryContext(playerId, npcMemoryId, message, plan.eventMemory());
        }

        PreparedRequest prepared = new PreparedRequest(
                playerId.toString(), player.getName(), npc == null ? "AIlex" : npc.getName(), npcMemoryId,
                message == null ? "" : message, systemPrompt == null ? "" : systemPrompt,
                userPrompt == null ? "" : userPrompt, analysis, settings, plan, retrieveKnowledge, snapshot, memory,
                dialogue, canWriteSharedMemory(player), System.nanoTime()
        );
        logPrepared(prepared);
        return prepared;
    }

    /** Performs retrieval-aware generation with bounded information seeking and one quality escalation when affordable. */
    public AssistantReply respond(PreparedRequest request) {
        request = refineRequestSemantically(request);
        AssistantSettings.ModelProfile initialProfile = request.settings().profileFor(request.analysis().mode());
        if (!request.settings().enabled()) {
            return complete(request, initialProfile, AssistantReply.unavailable(), 0, 0, "assistant-disabled");
        }
        if (!request.settings().readOnlyTools() || request.settings().maxModelCalls() < 1) {
            return complete(request, initialProfile, fallbackFor(request, "policy"), 0, 0, "policy-fallback");
        }
        if (request.analysis().mode() == AssistantMode.HANDOFF) {
            return complete(request, initialProfile, fallbackFor(request, "policy"), 0, 0, "safety-handoff");
        }

        AssistantCircuitBreaker initialBreaker = circuitBreakers.computeIfAbsent(
                initialProfile.model(), ignored -> new AssistantCircuitBreaker()
        );
        if (!initialBreaker.allowsRequest(request.settings().circuitBreakerEnabled())) {
            fallbacks.incrementAndGet();
            return complete(request, initialProfile, fallbackFor(request, "upstream-unavailable"), 0, 0, "circuit-open");
        }
        OpenAiResponsesClient client = plugin.getOpenAiResponsesClient();
        if (client == null) {
            return complete(request, initialProfile, AssistantReply.unavailable(), 0, 0, "client-unavailable");
        }

        List<LocalKnowledgeIndex.KnowledgeChunk> evidence = retrieveEvidence(request);
        int plannerBudget = Math.min(
                request.settings().maxToolRounds(), Math.max(0, request.settings().maxModelCalls() - 1)
        );
        AgentEnrichment enrichment = readAgent.enrich(
                request, evidence, plannerBudget, remainingDuration(request)
        );
        agentPlannerCalls.addAndGet(enrichment.modelCalls());
        agentToolCalls.addAndGet(enrichment.toolCalls());
        agentPlannerInputTokens.addAndGet(enrichment.plannerInputTokens());
        agentPlannerOutputTokens.addAndGet(enrichment.plannerOutputTokens());

        String staticKey = cacheKey(request, initialProfile, evidence);
        if (enrichment.context().isBlank() && request.settings().cacheStaticAnswers()
                && isStaticIntent(request.analysis().intent())) {
            AssistantReply cached = staticReplyCache.get(staticKey);
            if (cached != null) {
                return complete(request, initialProfile, cached, enrichment.modelCalls(), evidence.size(), "cache-hit");
            }
        }

        String prompt = buildPrompt(request, evidence, enrichment.context());
        boolean structured = AssistantGenerationPolicy.useStructuredOutput(
                request.settings().structuredOutput(), request.analysis().mode(), request.analysis().intent(), request.message()
        );
        int modelCalls = enrichment.modelCalls();
        int availableCalls = Math.max(0, request.settings().maxModelCalls() - modelCalls);
        int primaryCallBudget = structured ? Math.min(2, availableCalls) : Math.min(1, availableCalls);
        GenerationAttempt primary = generate(client, request, initialProfile, prompt, structured, primaryCallBudget);
        AssistantReply reply = primary.reply();
        modelCalls += primary.modelCalls();
        AssistantSettings.ModelProfile completedProfile = initialProfile;
        boolean acceptable = isAcceptable(reply, request, evidence, enrichment);

        if (!acceptable && AssistantGenerationPolicy.mayEscalate(
                request.analysis().mode(), modelCalls, request.settings().maxModelCalls(), remainingDuration(request).toMillis()
        )) {
            AssistantSettings.ModelProfile escalationProfile = request.settings().deliberateProfile();
            AssistantCircuitBreaker escalationBreaker = circuitBreakers.computeIfAbsent(
                    escalationProfile.model(), ignored -> new AssistantCircuitBreaker()
            );
            if (escalationBreaker.allowsRequest(request.settings().circuitBreakerEnabled())) {
                GenerationAttempt escalation = generate(
                        client, request, escalationProfile,
                        prompt + "\n\n[Escalation]\nRe-evaluate every factual line against the supplied evidence. "
                                + "Remove unsupported claims and return exact claim_evidence mappings.",
                        true, 1
                );
                modelCalls += escalation.modelCalls();
                if (isAcceptable(escalation.reply(), request, evidence, enrichment)) {
                    reply = escalation.reply();
                    acceptable = true;
                    completedProfile = escalationProfile;
                    escalationBreaker.recordSuccess();
                } else {
                    escalationBreaker.recordFailure(request.settings().circuitBreakerEnabled());
                }
            }
        }

        if (!acceptable) {
            initialBreaker.recordFailure(request.settings().circuitBreakerEnabled());
            fallbacks.incrementAndGet();
            recordExperience(request, enrichment, reply, "unverified");
            return complete(request, completedProfile, fallbackFor(request, "unverified"), modelCalls, evidence.size(),
                    "unverified");
        }

        // The deadline prevents starting more provider/tool work. Once an acceptable answer already exists, throwing it
        // away cannot reduce latency and only turns a successful request into a needless player-facing failure.
        initialBreaker.recordSuccess();
        replies.incrementAndGet();
        persistCandidates(request, reply);
        recordExperience(request, enrichment, reply, "accepted");
        if (memoryService != null) {
            memoryService.reconsolidateVerifiedEvidence(reply.coveredEvidenceIds());
        }
        if (request.analysis().mode() != AssistantMode.FAST) {
            verifiedReplies.incrementAndGet();
        }
        if (enrichment.context().isBlank() && !request.settings().shadowMode()
                && request.settings().cacheStaticAnswers() && isStaticIntent(request.analysis().intent())
                && reply.memoryCandidates().isEmpty() && reply.actionProposals().isEmpty()) {
            if (staticReplyCache.size() >= 256) {
                staticReplyCache.clear();
            }
            staticReplyCache.put(staticKey, reply);
        }
        if (request.settings().shadowMode()) {
            logOutcome(request, completedProfile, reply, modelCalls, evidence.size(), "shadow");
            return AssistantReply.invalid();
        }
        return complete(
                request, completedProfile, reply, modelCalls, evidence.size(),
                completedProfile == initialProfile ? "accepted" : "accepted-escalated"
        );
    }

    private PreparedRequest refineRequestSemantically(PreparedRequest request) {
        if (request == null || !request.settings().enabled() || request.analysis().mode() == AssistantMode.HANDOFF
                || !plugin.getConfig().getBoolean("openai.assistant.routing.semantic.enabled", true)) {
            return request;
        }
        SemanticNeedPlanner planner = semanticNeedPlanner;
        double minimumSimilarity = Math.clamp(plugin.getConfig().getDouble(
                "openai.assistant.routing.semantic.minimum_similarity", 0.42D
        ), 0.0D, 1.0D);
        double minimumMargin = Math.clamp(plugin.getConfig().getDouble(
                "openai.assistant.routing.semantic.minimum_margin", 0.025D
        ), 0.0D, 0.5D);
        SemanticNeedPlanner.Decision decision = planner.refine(
                request.message(), request.analysis(), request.contextPlan(), request.settings(),
                minimumSimilarity, minimumMargin
        );
        if (!decision.semanticallyRefined()) {
            return request;
        }
        RequiredContextPlanner.Plan refinedPlan = planner.mergePlan(
                request.contextPlan(), decision, request.settings()
        );
        AssistantIntentClassifier.Analysis refinedAnalysis = new AssistantIntentClassifier.Analysis(
                decision.intent(), request.settings().resolveMode(decision.mode()), request.analysis().language()
        );
        String memory = request.memory();
        UUID playerId = UUID.fromString(request.playerId());
        if (memoryService != null && request.settings().toolAllowed("session")
                && refinedPlan.durableMemory() && memory.isBlank()) {
            memory = memoryContext(playerId, request.npcMemoryId(), request.message(), refinedPlan.eventMemory());
        }
        if (relationshipMemory != null && request.settings().toolAllowed("session")
                && (refinedPlan.durableMemory() || decision.intent() == AssistantIntent.CONTEXT_FOLLOWUP
                || request.dialogueContext().active())) {
            String relationship = relationshipMemory.promptContext(playerId, request.npcMemoryId());
            if (!relationship.isBlank()) {
                memory = memory.isBlank()
                        ? "[Relationship continuity]\n" + relationship
                        : memory + "\n\n[Relationship continuity]\n" + relationship;
            }
        }
        semanticRefinements.incrementAndGet();
        return new PreparedRequest(
                request.playerId(), request.playerName(), request.npcName(), request.npcMemoryId(), request.message(),
                request.systemPrompt(), request.userPrompt(), refinedAnalysis, request.settings(), refinedPlan,
                refinedPlan.knowledge(), request.snapshot(), memory, request.dialogueContext(),
                request.canWriteSharedMemory(), request.preparedAtNanos()
        );
    }

    private List<LocalKnowledgeIndex.KnowledgeChunk> retrieveEvidence(PreparedRequest request) {
        if (!request.retrieveKnowledge()) {
            return List.of();
        }
        if (request.analysis().intent() == AssistantIntent.KNOWLEDGE_DISCOVERY) {
            return knowledgeIndex.discover(request.playerId() + '|' + request.message(), request.settings());
        }
        List<LocalKnowledgeIndex.KnowledgeChunk> evidence = knowledgeIndex.search(request.message(), request.settings());
        if (evidence.isEmpty() && request.analysis().intent() == AssistantIntent.SERVER_FACT
                && isBroadServerQuestion(request.message())) {
            return knowledgeIndex.discover(request.playerId() + '|' + request.message(), request.settings());
        }
        return evidence;
    }

    private boolean isBroadServerQuestion(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return text.length() <= 100 && (text.contains("server") || text.contains("haunted"))
                && (text.contains("weet") || text.contains("know") || text.contains("vertel") || text.contains("tell"));
    }

    private GenerationAttempt generate(
            OpenAiResponsesClient client,
            PreparedRequest request,
            AssistantSettings.ModelProfile profile,
            String prompt,
            boolean structured,
            int maximumCalls
    ) {
        if (!structured) {
            if (maximumCalls < 1 || remainingDuration(request).compareTo(Duration.ofSeconds(1)) < 0) {
                return new GenerationAttempt(AssistantReply.invalid(), 0);
            }
            String raw = client.getChatResponse(buildSystemPrompt(request), prompt, requestOptions(request, profile));
            return new GenerationAttempt(AssistantReply.fromPlainText(raw), 1);
        }

        AssistantReply reply = AssistantReply.invalid();
        int calls = 0;
        for (int attempt = 0; attempt < maximumCalls
                && !reply.valid() && remainingDuration(request).compareTo(Duration.ofSeconds(1)) >= 0; attempt++) {
            String retry = attempt == 0 ? "" : "\n\nThe previous output was invalid. Return only JSON matching the schema.";
            calls++;
            reply = parseStructuredReply(client.getStructuredChatResponse(
                    buildSystemPrompt(request), prompt + retry, responseSchema(), requestOptions(request, profile)
            ), request);
        }
        return new GenerationAttempt(reply, calls);
    }

    public void reload() {
        knowledgeIndex.reload();
        semanticNeedPlanner = new SemanticNeedPlanner(knowledgeIndex.semanticEmbeddingProvider());
        Thread.ofVirtual().name("AIlex-SemanticRouter-Warmup").start(semanticNeedPlanner::warm);
        if (memoryService != null) {
            memoryService.reload();
        }
        staticReplyCache.clear();
        circuitBreakers.clear();
    }

    public String status() {
        return "replies=" + replies.get()
                + ", verified=" + verifiedReplies.get()
                + ", fallbacks=" + fallbacks.get()
                + ", knowledge_chunks=" + knowledgeIndex.size()
                + ", semantic_embeddings=" + knowledgeIndex.learnedSemanticRetrievalAvailable()
                + ", shared_memory=" + (memoryService != null && memoryService.sharedRepository())
                + ", agent_planner_calls=" + agentPlannerCalls.get()
                + ", agent_tool_calls=" + agentToolCalls.get()
                + ", agent_planner_input_tokens=" + agentPlannerInputTokens.get()
                + ", agent_planner_output_tokens=" + agentPlannerOutputTokens.get()
                + ", semantic_refinements=" + semanticRefinements.get();
    }

    public void recordDirectResponse(PreparedRequest request, String response) {
        logOutcome(
                request, request.settings().profileFor(request.analysis().mode()), AssistantReply.fromPlainText(response),
                1, 0, "direct-client"
        );
    }

    private void persistCandidates(PreparedRequest request, AssistantReply reply) {
        if (request.settings().shadowMode() || memoryService == null || reply.memoryCandidates().isEmpty()) {
            return;
        }
        UUID playerId = UUID.fromString(request.playerId());
        reply.memoryCandidates().forEach(candidate -> memoryService.rememberCandidate(
                playerId, request.playerName(), candidate, request.message(), request.canWriteSharedMemory()
        ));
    }

    private void recordExperience(
            PreparedRequest request,
            AgentEnrichment enrichment,
            AssistantReply reply,
            String outcome
    ) {
        if (experienceMemory == null || request.settings().shadowMode()) {
            return;
        }
        Set<String> evidenceIds = new HashSet<>(reply.evidenceIds());
        evidenceIds.addAll(enrichment.evidenceIds());
        String intent = request.analysis().intent().name().toLowerCase(Locale.ROOT);
        if ("unverified".equals(outcome)) {
            experienceMemory.recordVerifiedOutcome(
                    request.npcMemoryId(), request.analysis().intent(), "grounding-" + intent,
                    "When a similar " + intent + " request lacks verifiable evidence, retrieve more evidence or abstain.",
                    "unverified", evidenceIds
            );
            return;
        }
        Set<String> usedToolEvidence = new HashSet<>(enrichment.evidenceIds());
        usedToolEvidence.retainAll(reply.coveredEvidenceIds());
        if (!usedToolEvidence.isEmpty()) {
            String tools = enrichment.usedTools().stream().sorted().collect(Collectors.joining(","));
            experienceMemory.recordVerifiedOutcome(
                    request.npcMemoryId(), request.analysis().intent(), "tool-route-" + intent,
                    "Useful read tools for this type of request: " + tools + ". Re-check current evidence before reuse.",
                    "accepted", usedToolEvidence
            );
        }
        if (looksLikeCorrection(request.message()) && !reply.coveredEvidenceIds().isEmpty()) {
            experienceMemory.recordVerifiedOutcome(
                    request.npcMemoryId(), request.analysis().intent(), "correction-" + intent,
                    "A correction occurred for this request type; resolve current evidence and temporal memory before "
                            + "repeating an older claim.",
                    "correction-verified", reply.coveredEvidenceIds()
            );
        }
    }

    private boolean looksLikeCorrection(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return text.contains("klopt niet") || text.contains("je hebt het fout") || text.contains("correctie")
                || text.contains("eigenlijk") || text.contains("that's wrong") || text.contains("you're wrong")
                || text.contains("correction") || text.contains("actually");
    }

    private String memoryContext(UUID playerId, String npcId, String query, boolean includeEvents) {
        Set<MemoryKind> semanticKinds = Set.of(
                MemoryKind.PREFERENCE, MemoryKind.FACT, MemoryKind.OPINION, MemoryKind.INTEREST,
                MemoryKind.GOAL, MemoryKind.RELATIONSHIP
        );
        List<MemoryRecord> semantic = memoryService.search(playerId, npcId, query, semanticKinds, 36);
        List<MemoryRecord> events = includeEvents
                ? memoryService.search(playerId, npcId, query, Set.of(MemoryKind.EVENT, MemoryKind.EPISODE), 12).stream()
                        .filter(record -> !record.tags().contains("experience"))
                        .toList()
                : List.of();
        StringBuilder output = new StringBuilder();
        if (!semantic.isEmpty()) {
            output.append("Topic-structured semantic memory (every item preserves its exact evidence id):\n")
                    .append(memoryTopicView.render(semantic, 5_500));
        }
        if (!events.isEmpty()) {
            if (!output.isEmpty()) {
                output.append('\n');
            }
            output.append("Relevant episodic memory:\n");
            for (MemoryRecord record : events) {
                output.append("- evidence_id=memory.").append(record.id()).append(' ').append(record.value());
                if (record.occurredAt() > 0L) {
                    output.append(" @").append(Instant.ofEpochMilli(record.occurredAt()));
                }
                output.append('\n');
            }
        }
        return output.toString().trim();
    }

    private AssistantReply complete(
            PreparedRequest request,
            AssistantSettings.ModelProfile profile,
            AssistantReply reply,
            int modelCalls,
            int retrievedChunks,
            String outcome
    ) {
        logOutcome(request, profile, reply, modelCalls, retrievedChunks, outcome);
        return reply;
    }

    private void logPrepared(PreparedRequest request) {
        if (!request.settings().diagnosticLogging()) {
            return;
        }
        AssistantSettings.ModelProfile profile = request.settings().profileFor(request.analysis().mode());
        String liveSources = request.contextPlan().liveSources().stream()
                .map(source -> source.name().toLowerCase(Locale.ROOT)).sorted().collect(Collectors.joining(","));
        LoggerUtils.logInfo("[AIlex assistant] route " + requesterField(request)
                + " npc=" + sanitizeLogField(request.npcName())
                + " intent=" + request.analysis().intent().name().toLowerCase(Locale.ROOT)
                + " mode=" + request.analysis().mode().name().toLowerCase(Locale.ROOT)
                + " language=" + request.analysis().language()
                + " model=" + sanitizeLogField(profile.model())
                + " effort=" + profile.reasoningEffort()
                + " retrieval=" + request.retrieveKnowledge()
                + " live_plan=" + (liveSources.isBlank() ? "none" : liveSources)
                + " frozen_live_sources=" + request.snapshot().sourceIds().size()
                + " memory=" + !request.memory().isBlank()
                + " event_memory=" + request.contextPlan().eventMemory()
                + " dialogue=" + request.dialogueContext().active()
                + " agent=" + readAgent.enabled()
                + " deadline_s=" + request.settings().totalDeadlineSeconds());
    }

    private void logOutcome(
            PreparedRequest request,
            AssistantSettings.ModelProfile profile,
            AssistantReply reply,
            int modelCalls,
            int retrievedChunks,
            String outcome
    ) {
        if (!request.settings().diagnosticLogging()) {
            return;
        }
        long latencyMillis = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - request.preparedAtNanos()));
        int responseCharacters = reply.lines().stream().mapToInt(String::length).sum();
        String evidenceIds = reply.evidenceIds().stream().sorted().map(this::sanitizeLogField)
                .collect(Collectors.joining(","));
        StringBuilder log = new StringBuilder("[AIlex assistant] complete ")
                .append(requesterField(request))
                .append(" npc=").append(sanitizeLogField(request.npcName()))
                .append(" intent=").append(request.analysis().intent().name().toLowerCase(Locale.ROOT))
                .append(" mode=").append(request.analysis().mode().name().toLowerCase(Locale.ROOT))
                .append(" language=").append(request.analysis().language())
                .append(" model=").append(sanitizeLogField(profile.model()))
                .append(" outcome=").append(sanitizeLogField(outcome))
                .append(" response_type=").append(responseType(reply))
                .append(" model_calls=").append(modelCalls)
                .append(" retrieved_chunks=").append(retrievedChunks)
                .append(" evidence_ids=").append(evidenceIds.isBlank() ? "none" : evidenceIds)
                .append(" claim_evidence_lines=").append(reply.claimEvidence().size())
                .append(" confidence=").append(sanitizeLogField(reply.confidence().isBlank() ? "none" : reply.confidence()))
                .append(" handoff=").append(sanitizeLogField(reply.handoff().isBlank() ? "none" : reply.handoff()))
                .append(" memory_candidates=").append(reply.memoryCandidates().size())
                .append(" lines=").append(reply.lines().size())
                .append(" response_chars=").append(responseCharacters)
                .append(" latency_ms=").append(latencyMillis);
        if (request.settings().logResponsePreview() && !reply.lines().isEmpty()) {
            log.append(" response_preview=\"")
                    .append(responsePreview(reply, request.settings().maxResponsePreviewCharacters())).append('"');
        }
        LoggerUtils.logInfo(log.toString());
    }

    private String buildSystemPrompt(PreparedRequest request) {
        return promptComposer.systemPrompt(request);
    }

    private String buildPrompt(
            PreparedRequest request,
            List<LocalKnowledgeIndex.KnowledgeChunk> evidence,
            String agentContext
    ) {
        String basePrompt = responseInstruction(request) + "\n\n" + request.userPrompt();
        List<ContextCompiler.ContextSource> sources = evidence.stream()
                .map(chunk -> new ContextCompiler.ContextSource(
                        chunk.id(), chunk.title() + (chunk.category().isBlank() ? "" : " [" + chunk.category() + "]"),
                        chunk.text()
                ))
                .toList();
        LiveSnapshot directSnapshot = request.snapshot().filtered(request.contextPlan().liveSources());
        String durableContext = request.memory();
        if (agentContext != null && !agentContext.isBlank()) {
            durableContext = durableContext.isBlank()
                    ? "Model-requested read-only observations:\n" + agentContext
                    : durableContext + "\n\nModel-requested read-only observations:\n" + agentContext;
        }
        ContextCompiler.CompiledContext compiled = contextCompiler.compile(
                request.analysis().mode(), request.settings().maxInputTokens(request.analysis().mode()), basePrompt,
                request.dialogueContext(), directSnapshot.isBlank() ? "" : directSnapshot.asEvidence(), durableContext,
                sources, ""
        );
        if (request.settings().diagnosticLogging()) {
            String sourcesLog = compiled.tokensBySource().entrySet().stream()
                    .map(entry -> sanitizeLogField(entry.getKey()) + ':' + entry.getValue())
                    .collect(Collectors.joining(","));
            LoggerUtils.logInfo("[AIlex context] " + requesterField(request)
                    + " intent=" + request.analysis().intent().name().toLowerCase(Locale.ROOT)
                    + " budget_tokens=" + request.settings().maxInputTokens(request.analysis().mode())
                    + " estimated_tokens=" + compiled.estimatedTokens()
                    + " sources=" + (sourcesLog.isBlank() ? "none" : sourcesLog));
        }
        return compiled.prompt();
    }

    private String responseInstruction(PreparedRequest request) {
        return promptComposer.turnInstruction(request);
    }

    private AssistantReply parseStructuredReply(String raw, PreparedRequest request) {
        if (raw == null || raw.isBlank()) {
            return AssistantReply.invalid();
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) {
                return AssistantReply.invalid();
            }
            JsonObject object = parsed.getAsJsonObject();
            JsonArray lines = object.getAsJsonArray("lines");
            if (lines == null || lines.isEmpty()) {
                return AssistantReply.invalid();
            }
            List<String> safeLines = new ArrayList<>();
            for (JsonElement line : lines) {
                if (!line.isJsonPrimitive()) {
                    return AssistantReply.invalid();
                }
                String normalized = normalizeLine(line.getAsString(), request.settings().maxLineCharacters());
                if (!normalized.isBlank()) {
                    safeLines.add(normalized);
                }
                if (safeLines.size() >= request.settings().maxLines(request.analysis().mode())) {
                    break;
                }
            }

            Set<String> sources = new HashSet<>();
            JsonArray evidenceIds = object.getAsJsonArray("evidence_ids");
            if (evidenceIds != null) {
                for (JsonElement source : evidenceIds) {
                    if (source.isJsonPrimitive()) {
                        String id = source.getAsString().trim();
                        if (!id.isBlank()) {
                            sources.add(id);
                        }
                    }
                }
            }

            Map<Integer, Set<String>> claimEvidence = new HashMap<>();
            JsonArray claimEvidenceArray = object.getAsJsonArray("claim_evidence");
            if (claimEvidenceArray != null) {
                for (JsonElement element : claimEvidenceArray) {
                    if (!element.isJsonObject()) {
                        return AssistantReply.invalid();
                    }
                    JsonObject claim = element.getAsJsonObject();
                    if (!claim.has("line_index") || !claim.get("line_index").isJsonPrimitive()) {
                        return AssistantReply.invalid();
                    }
                    int lineIndex = claim.get("line_index").getAsInt();
                    Set<String> ids = new HashSet<>();
                    JsonArray idsArray = claim.getAsJsonArray("evidence_ids");
                    if (idsArray != null) {
                        for (JsonElement idElement : idsArray) {
                            if (idElement.isJsonPrimitive() && !idElement.getAsString().isBlank()) {
                                ids.add(idElement.getAsString().trim());
                            }
                        }
                    }
                    if (!ids.isEmpty()) {
                        claimEvidence.computeIfAbsent(lineIndex, ignored -> new HashSet<>()).addAll(ids);
                        sources.addAll(ids);
                    }
                }
            }

            String confidence = getString(object, "confidence");
            String handoff = getString(object, "handoff");
            List<MemoryCandidate> memoryCandidates = new ArrayList<>();
            JsonArray candidates = object.getAsJsonArray("memory_candidates");
            if (candidates != null) {
                for (JsonElement element : candidates) {
                    if (!element.isJsonObject()) {
                        return AssistantReply.invalid();
                    }
                    JsonObject candidate = element.getAsJsonObject();
                    MemoryCandidate memoryCandidate = new MemoryCandidate(
                            getString(candidate, "scope"), getString(candidate, "kind"), getString(candidate, "key"),
                            getString(candidate, "value"), getString(candidate, "operation")
                    );
                    if (!memoryCandidate.key().isBlank() && memoryCandidates.size() < 12) {
                        memoryCandidates.add(memoryCandidate);
                    }
                }
            }
            List<AssistantActionProposal> actionProposals = new ArrayList<>();
            JsonArray actions = object.getAsJsonArray("action_proposals");
            if (actions != null) {
                for (JsonElement element : actions) {
                    if (!element.isJsonObject()) {
                        return AssistantReply.invalid();
                    }
                    JsonObject action = element.getAsJsonObject();
                    String type = getString(action, "type").toUpperCase(Locale.ROOT);
                    try {
                        AssistantActionType actionType = AssistantActionType.valueOf(type);
                        if (actionProposals.size() < 2) {
                            actionProposals.add(new AssistantActionProposal(actionType, getString(action, "reason")));
                        }
                    } catch (IllegalArgumentException exception) {
                        return AssistantReply.invalid();
                    }
                }
            }
            return new AssistantReply(
                    safeLines, Set.copyOf(sources), confidence, handoff, List.copyOf(memoryCandidates),
                    List.copyOf(actionProposals), Map.copyOf(claimEvidence), !safeLines.isEmpty()
            );
        } catch (RuntimeException ignored) {
            return AssistantReply.invalid();
        }
    }

    private boolean isAcceptable(
            AssistantReply reply,
            PreparedRequest request,
            List<LocalKnowledgeIndex.KnowledgeChunk> evidence,
            AgentEnrichment enrichment
    ) {
        if (!reply.valid() || reply.lines().isEmpty()) {
            return false;
        }
        if (request.analysis().mode() == AssistantMode.FAST || !request.settings().verificationEnabled()) {
            return true;
        }
        Set<String> allowed = new HashSet<>();
        evidence.forEach(chunk -> allowed.add(chunk.id()));
        allowed.addAll(request.snapshot().filtered(request.contextPlan().liveSources()).sourceIds());
        allowed.addAll(memoryEvidenceIds(request.memory()));
        allowed.addAll(enrichment.evidenceIds());
        if (!reply.evidenceIds().isEmpty() && !allowed.containsAll(reply.evidenceIds())) {
            return false;
        }
        for (Map.Entry<Integer, Set<String>> entry : reply.claimEvidence().entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= reply.lines().size() || entry.getValue().isEmpty()
                    || !allowed.containsAll(entry.getValue())) {
                return false;
            }
        }

        boolean groundingRequired = AssistantGroundingPolicy.requiresGrounding(request.analysis().intent());
        if (groundingRequired && !AssistantGroundingPolicy.hasRequiredEvidence(request.analysis().intent(), allowed)) {
            return false;
        }
        if (groundingRequired && (reply.evidenceIds().isEmpty() || reply.claimEvidence().isEmpty()
                || !reply.allLinesGrounded())) {
            return false;
        }
        if (groundingRequired && !reply.coveredEvidenceIds().containsAll(reply.evidenceIds())) {
            return false;
        }
        return confidenceRank(reply.confidence()) >= confidenceRank(request.settings().minimumConfidence())
                || !reply.handoff().isBlank();
    }

    private Set<String> memoryEvidenceIds(String context) {
        Set<String> ids = new HashSet<>();
        if (context == null || context.isBlank()) {
            return Set.of();
        }
        for (String token : context.split("\\s+")) {
            if (token.startsWith("evidence_id=memory.")) {
                ids.add(token.substring("evidence_id=".length()).replaceAll("[^A-Za-z0-9._-]+$", ""));
            }
        }
        return Set.copyOf(ids);
    }

    private int confidenceRank(String confidence) {
        return switch (confidence == null ? "" : confidence.toLowerCase(Locale.ROOT)) {
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private AssistantReply fallbackFor(PreparedRequest request, String reason) {
        String text;
        if (request.analysis().intent() == AssistantIntent.SAFETY) {
            text = "Daar kan ik niet mee helpen, maar ik kan wel veilig helpen met Minecraft of de serverregels.";
        } else if (request.analysis().intent() == AssistantIntent.SUPPORT) {
            text = "Dat kan ik niet verifiëren of afhandelen. Gebruik /help of neem contact op met de officiële Support.";
        } else if (request.analysis().intent() == AssistantIntent.KNOWLEDGE_DISCOVERY) {
            text = "Ik kon nu geen betrouwbaar serverfeit ophalen; vraag me gerust naar Survival, Creative, events, ranks of commands.";
        } else if (request.analysis().intent() == AssistantIntent.CONVERSATION
                || request.analysis().intent() == AssistantIntent.CONTEXT_FOLLOWUP) {
            text = "Sorry, ik kreeg daar geen bruikbaar antwoord op. Kun je het nog eens kort zeggen?";
        } else if ("nl".equals(request.analysis().language())) {
            text = "Dat kan ik nu niet betrouwbaar verifiëren. Kijk in /help of vraag een stafflid om de actuele info.";
        } else {
            text = "I can't verify that reliably right now. Please check /help or ask staff for current information.";
        }
        return AssistantReply.fromPlainText(text).withHandoff(reason);
    }

    private boolean isStaticIntent(AssistantIntent intent) {
        return intent == AssistantIntent.SERVER_FACT || intent == AssistantIntent.GAMEPLAY_HELP;
    }

    private OpenAiResponsesClient.RequestOptions requestOptions(
            PreparedRequest request,
            AssistantSettings.ModelProfile profile
    ) {
        String npcCacheIdentity = request.npcName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        return new OpenAiResponsesClient.RequestOptions(
                profile.model(), profile.maxOutputTokens(), profile.reasoningEffort(), remainingDuration(request),
                safetyIdentifier(request.playerId()), "ailex:" + npcCacheIdentity + ':' + profile.model(), "low"
        );
    }

    private Duration remainingDuration(PreparedRequest request) {
        long elapsedNanos = Math.max(0L, System.nanoTime() - request.preparedAtNanos());
        long deadlineNanos = TimeUnit.SECONDS.toNanos(request.settings().totalDeadlineSeconds());
        return Duration.ofNanos(Math.max(0L, deadlineNanos - elapsedNanos));
    }

    private String safetyIdentifier(String playerId) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(playerId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "ailex-" + java.util.HexFormat.of().formatHex(digest, 0, 24);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String cacheKey(
            PreparedRequest request,
            AssistantSettings.ModelProfile profile,
            List<LocalKnowledgeIndex.KnowledgeChunk> evidence
    ) {
        String evidenceFingerprint = evidence.stream()
                .map(chunk -> chunk.id() + ':' + Integer.toHexString(chunk.text().hashCode()))
                .collect(Collectors.joining(","));
        LiveSnapshot directSnapshot = request.snapshot().filtered(request.contextPlan().liveSources());
        return request.analysis().intent() + "|" + request.analysis().language() + "|" + request.npcName()
                + "|" + profile.model() + "|" + Integer.toHexString(request.systemPrompt().hashCode())
                + "|" + Integer.toHexString(request.userPrompt().hashCode())
                + "|" + Integer.toHexString(request.memory().hashCode())
                + "|" + Integer.toHexString(directSnapshot.asEvidence().hashCode())
                + "|" + Integer.toHexString(evidenceFingerprint.hashCode())
                + "|" + request.message().trim().toLowerCase(Locale.ROOT);
    }

    private JsonObject responseSchema() {
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.addProperty("name", "ailex_assistant_reply");
        format.addProperty("strict", true);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject lines = new JsonObject();
        lines.addProperty("type", "array");
        JsonObject lineItems = new JsonObject();
        lineItems.addProperty("type", "string");
        lines.add("items", lineItems);
        properties.add("lines", lines);
        properties.add("confidence", enumProperty("high", "medium", "low"));

        JsonObject evidenceIds = stringArrayProperty();
        properties.add("evidence_ids", evidenceIds);

        JsonObject claimEvidence = new JsonObject();
        claimEvidence.addProperty("type", "array");
        JsonObject claimItem = new JsonObject();
        claimItem.addProperty("type", "object");
        JsonObject claimProperties = new JsonObject();
        JsonObject lineIndex = new JsonObject();
        lineIndex.addProperty("type", "integer");
        lineIndex.addProperty("minimum", 0);
        claimProperties.add("line_index", lineIndex);
        claimProperties.add("evidence_ids", stringArrayProperty());
        claimItem.add("properties", claimProperties);
        JsonArray claimRequired = new JsonArray();
        claimRequired.add("line_index");
        claimRequired.add("evidence_ids");
        claimItem.add("required", claimRequired);
        claimItem.addProperty("additionalProperties", false);
        claimEvidence.add("items", claimItem);
        properties.add("claim_evidence", claimEvidence);

        JsonObject handoff = new JsonObject();
        handoff.addProperty("type", "string");
        properties.add("handoff", handoff);

        JsonObject memoryCandidates = new JsonObject();
        memoryCandidates.addProperty("type", "array");
        JsonObject memoryItem = new JsonObject();
        memoryItem.addProperty("type", "object");
        JsonObject memoryProperties = new JsonObject();
        memoryProperties.add("scope", enumProperty("player", "shared"));
        memoryProperties.add("kind", enumProperty("preference", "fact", "opinion", "interest", "goal"));
        JsonObject key = new JsonObject();
        key.addProperty("type", "string");
        memoryProperties.add("key", key);
        JsonObject value = new JsonObject();
        value.addProperty("type", "string");
        memoryProperties.add("value", value);
        memoryProperties.add("operation", enumProperty("upsert", "forget"));
        memoryItem.add("properties", memoryProperties);
        JsonArray memoryRequired = new JsonArray();
        memoryRequired.add("scope");
        memoryRequired.add("kind");
        memoryRequired.add("key");
        memoryRequired.add("value");
        memoryRequired.add("operation");
        memoryItem.add("required", memoryRequired);
        memoryItem.addProperty("additionalProperties", false);
        memoryCandidates.add("items", memoryItem);
        properties.add("memory_candidates", memoryCandidates);

        JsonObject actionProposals = new JsonObject();
        actionProposals.addProperty("type", "array");
        JsonObject actionItem = new JsonObject();
        actionItem.addProperty("type", "object");
        JsonObject actionProperties = new JsonObject();
        actionProperties.add("type", enumProperty("FOLLOW_REQUESTER", "COME_HERE", "STOP_MOVING"));
        JsonObject actionReason = new JsonObject();
        actionReason.addProperty("type", "string");
        actionProperties.add("reason", actionReason);
        actionItem.add("properties", actionProperties);
        JsonArray actionRequired = new JsonArray();
        actionRequired.add("type");
        actionRequired.add("reason");
        actionItem.add("required", actionRequired);
        actionItem.addProperty("additionalProperties", false);
        actionProposals.add("items", actionItem);
        properties.add("action_proposals", actionProposals);

        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("lines");
        required.add("confidence");
        required.add("evidence_ids");
        required.add("claim_evidence");
        required.add("handoff");
        required.add("memory_candidates");
        required.add("action_proposals");
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        format.add("schema", schema);
        return format;
    }

    private JsonObject stringArrayProperty() {
        JsonObject array = new JsonObject();
        array.addProperty("type", "array");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        array.add("items", items);
        return array;
    }

    private JsonObject enumProperty(String... values) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        JsonArray allowed = new JsonArray();
        for (String value : values) {
            allowed.add(value);
        }
        property.add("enum", allowed);
        return property;
    }

    private String normalizeLine(String value, int maxCharacters) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxCharacters) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxCharacters - 1)) + "…";
    }

    private String getString(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString().trim() : "";
    }

    private boolean canWriteSharedMemory(Player player) {
        String permission = plugin.getConfig().getString(
                "openai.assistant.memory.shared_write_permission", "ailex.admin"
        );
        return permission == null || permission.isBlank() || player.hasPermission(permission.trim());
    }

    private String requesterField(PreparedRequest request) {
        return request.settings().logRequesterName()
                ? "requester=" + sanitizeLogField(request.playerName()) : "requester=hidden";
    }

    private String responseType(AssistantReply reply) {
        if (!reply.valid()) {
            return "invalid";
        }
        return reply.handoff().isBlank() ? "answer" : "handoff";
    }

    private String responsePreview(AssistantReply reply, int maxCharacters) {
        String preview = sanitizeLogField(String.join(" / ", reply.lines()));
        return preview.length() <= maxCharacters ? preview : preview.substring(0, maxCharacters - 1) + "…";
    }

    private String sanitizeLogField(String value) {
        return (value == null ? "" : value).replaceAll("\\s+", " ").trim()
                .replace('<', '‹').replace('>', '›').replace('"', '\'');
    }

    private record GenerationAttempt(AssistantReply reply, int modelCalls) {
    }

    /** Immutable prepared request safe to move to an asynchronous model worker. */
    public record PreparedRequest(
            String playerId,
            String playerName,
            String npcName,
            String npcMemoryId,
            String message,
            String systemPrompt,
            String userPrompt,
            AssistantIntentClassifier.Analysis analysis,
            AssistantSettings settings,
            RequiredContextPlanner.Plan contextPlan,
            boolean retrieveKnowledge,
            LiveSnapshot snapshot,
            String memory,
            AssistantDialogueContext dialogueContext,
            boolean canWriteSharedMemory,
            long preparedAtNanos
    ) {
    }

    /** Safe live context with explicit provenance IDs used for response verification and read-tool inspection. */
    public record LiveSnapshot(List<String> values, Set<String> sourceIds) {

        private static LiveSnapshot capture(
                Player player,
                NPC npc,
                String message,
                Set<RequiredContextPlanner.LiveSource> requested
        ) {
            if (requested == null || requested.isEmpty()) {
                return empty();
            }
            String metadata = PaperLiveContextEnricher.collect(player, npc, message, requested);
            return fromMetadata(metadata, requested);
        }

        private static LiveSnapshot empty() {
            return new LiveSnapshot(List.of(), Set.of());
        }

        private static LiveSnapshot fromMetadata(
                String metadata,
                Set<RequiredContextPlanner.LiveSource> requested
        ) {
            if (metadata == null || metadata.isBlank()) {
                return empty();
            }
            List<String> relevant = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (String rawPart : metadata.split("\\s*\\|\\s*")) {
                String part = rawPart.replaceAll("\\s+", " ").trim();
                if (part.isBlank()) {
                    continue;
                }
                int separator = part.indexOf('=');
                String key = separator < 0 ? part : part.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                if (requested == null || requested.isEmpty() || metadataKeyAllowed(key, requested)) {
                    relevant.add(part);
                    String sourceId = sourceIdForKey(key);
                    if (!sourceId.isBlank()) {
                        ids.add(sourceId);
                    }
                }
            }
            return relevant.isEmpty()
                    ? empty()
                    : new LiveSnapshot(List.copyOf(relevant.stream().distinct().toList()), Set.copyOf(ids));
        }

        private LiveSnapshot withContext(String metadata, Set<RequiredContextPlanner.LiveSource> requested) {
            LiveSnapshot additional = fromMetadata(metadata, requested);
            if (additional.isBlank()) {
                return this;
            }
            List<String> enriched = new ArrayList<>(values);
            enriched.addAll(additional.values());
            Set<String> ids = new HashSet<>(sourceIds);
            ids.addAll(additional.sourceIds());
            ids.add("live.context");
            return new LiveSnapshot(List.copyOf(enriched.stream().distinct().toList()), Set.copyOf(ids));
        }

        public LiveSnapshot filtered(Set<RequiredContextPlanner.LiveSource> requested) {
            if (requested == null || requested.isEmpty() || values.isEmpty()) {
                return empty();
            }
            List<String> relevant = values.stream().filter(value -> {
                int separator = value.indexOf('=');
                String key = separator < 0 ? value : value.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                return metadataKeyAllowed(key, requested);
            }).toList();
            Set<String> ids = relevant.stream().map(value -> {
                int separator = value.indexOf('=');
                String key = separator < 0 ? value : value.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                return sourceIdForKey(key);
            }).filter(id -> !id.isBlank()).collect(Collectors.toUnmodifiableSet());
            return relevant.isEmpty() ? empty() : new LiveSnapshot(List.copyOf(relevant), ids);
        }

        private static String sourceIdForKey(String key) {
            if (key.startsWith("target_")) {
                return "live.target";
            }
            if (key.startsWith("player_inventory_") || key.startsWith("player_armor")
                    || key.startsWith("player_selected_hotbar")) {
                return "live.inventory";
            }
            if (key.startsWith("player_biome") || key.startsWith("player_position")
                    || key.startsWith("player_facing") || key.startsWith("player_light")
                    || key.startsWith("player_block") || key.startsWith("block_below")
                    || key.startsWith("world_") || key.equals("weather")) {
                return "live.world";
            }
            if (key.startsWith("server_")) {
                return "live.server";
            }
            if (key.startsWith("nearby_")) {
                return "live.nearby";
            }
            if (key.startsWith("bot_") || key.startsWith("npc_")) {
                return "live.npc";
            }
            if (key.startsWith("player_")) {
                return "live.requester";
            }
            return "";
        }

        private static boolean metadataKeyAllowed(
                String key,
                Set<RequiredContextPlanner.LiveSource> requested
        ) {
            if (key.startsWith("target_")) {
                return requested.contains(RequiredContextPlanner.LiveSource.TARGET);
            }
            if (key.startsWith("player_inventory_") || key.startsWith("player_armor")
                    || key.startsWith("player_selected_hotbar")) {
                return requested.contains(RequiredContextPlanner.LiveSource.INVENTORY)
                        || requested.contains(RequiredContextPlanner.LiveSource.REQUESTER);
            }
            if (key.startsWith("player_biome") || key.startsWith("player_position")
                    || key.startsWith("player_facing") || key.startsWith("player_light")
                    || key.startsWith("player_block") || key.startsWith("block_below")) {
                return requested.contains(RequiredContextPlanner.LiveSource.WORLD);
            }
            if (key.startsWith("player_")) {
                return requested.contains(RequiredContextPlanner.LiveSource.REQUESTER)
                        || requested.contains(RequiredContextPlanner.LiveSource.INVENTORY)
                        || requested.contains(RequiredContextPlanner.LiveSource.WORLD);
            }
            if (key.startsWith("world_") || key.equals("weather")) {
                return requested.contains(RequiredContextPlanner.LiveSource.WORLD);
            }
            if (key.startsWith("server_")) {
                return requested.contains(RequiredContextPlanner.LiveSource.SERVER);
            }
            if (key.startsWith("nearby_")) {
                return requested.contains(RequiredContextPlanner.LiveSource.NEARBY);
            }
            if (key.startsWith("bot_") || key.startsWith("npc_")) {
                return requested.contains(RequiredContextPlanner.LiveSource.NPC);
            }
            return false;
        }

        public boolean isBlank() {
            return values.isEmpty();
        }

        private String asEvidence() {
            if (values.isEmpty()) {
                return "";
            }
            String ids = sourceIds.stream().sorted().collect(Collectors.joining(","));
            return (ids.isBlank() ? "" : "evidence_ids=" + ids + "\n") + String.join(" | ", values);
        }
    }
}
