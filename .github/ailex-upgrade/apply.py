from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}: {old[:100]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


# ---------------------------------------------------------------------------
# Keep LocalKnowledgeIndex's public API source-compatible while sharing the
# learned embedding provider/cache with semantic routing.
# ---------------------------------------------------------------------------
knowledge = "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/knowledge/LocalKnowledgeIndex.java"
replace_once(
    knowledge,
    '''    /** Open-ended corpus discovery intentionally avoids pretending a vague prompt is a lexical query. */\n    public List<KnowledgeChunk> discover(AssistantSettings settings, String requesterSeed) {\n''',
    '''    /** Source-compatible discovery entry point used by the assistant orchestration. */\n    public List<KnowledgeChunk> discover(String requesterSeed, AssistantSettings settings) {\n        return discover(settings, requesterSeed);\n    }\n\n    /** Open-ended corpus discovery intentionally avoids pretending a vague prompt is a lexical query. */\n    public List<KnowledgeChunk> discover(AssistantSettings settings, String requesterSeed) {\n'''
)
replace_once(
    knowledge,
    '''    public boolean learnedSemanticAvailable() {\n        return embeddingProvider != null && embeddingProvider.available() && !semanticVectors.isEmpty();\n    }\n''',
    '''    public boolean learnedSemanticAvailable() {\n        return embeddingProvider != null && embeddingProvider.available() && !semanticVectors.isEmpty();\n    }\n\n    /** Compatibility name retained for status/diagnostic callers. */\n    public boolean learnedSemanticRetrievalAvailable() {\n        return learnedSemanticAvailable();\n    }\n'''
)


# ---------------------------------------------------------------------------
# Memory retrieval: deterministic temporal constraints + associative graph
# activation/PPR + periodic evidence-grounded consolidation.
# ---------------------------------------------------------------------------
memory = "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/memory/AssistantMemoryService.java"
replace_once(
    memory,
    '''    private final MemoryRepository repository;\n    private final MemoryTruthResolver truthResolver = new MemoryTruthResolver();\n    private final Map<String, MemoryRecord> activeRecords = new ConcurrentHashMap<>();\n''',
    '''    private final MemoryRepository repository;\n    private final MemoryTruthResolver truthResolver = new MemoryTruthResolver();\n    private final MemoryGraphRetriever graphRetriever = new MemoryGraphRetriever();\n    private final AssistantMemoryConsolidator consolidator;\n    private final Map<String, MemoryRecord> activeRecords = new ConcurrentHashMap<>();\n'''
)
replace_once(
    memory,
    '''        this.writer = Executors.newSingleThreadScheduledExecutor(runnable -> {\n            Thread thread = new Thread(runnable, "AIlex-MemoryWriter");\n            thread.setDaemon(true);\n            return thread;\n        });\n        long initialSharedSequence = repository.shared() ? repository.latestChangeSequence() : 0L;\n''',
    '''        this.writer = Executors.newSingleThreadScheduledExecutor(runnable -> {\n            Thread thread = new Thread(runnable, "AIlex-MemoryWriter");\n            thread.setDaemon(true);\n            return thread;\n        });\n        this.consolidator = new AssistantMemoryConsolidator(this);\n        long initialSharedSequence = repository.shared() ? repository.latestChangeSequence() : 0L;\n'''
)
replace_once(
    memory,
    '''        if (repository.shared()) {\n            writer.scheduleWithFixedDelay(this::refreshSharedRepositorySafely, 0L, 1L, TimeUnit.SECONDS);\n        }\n    }\n''',
    '''        if (repository.shared()) {\n            writer.scheduleWithFixedDelay(this::refreshSharedRepositorySafely, 0L, 1L, TimeUnit.SECONDS);\n        }\n        if (consolidationEnabled()) {\n            writer.scheduleWithFixedDelay(\n                    this::consolidateSafely, 60L, consolidationIntervalMinutes(), TimeUnit.MINUTES\n            );\n        }\n    }\n'''
)
replace_once(
    memory,
    '''        Set<String> queryTerms = Set.copyOf(significantWords(query));\n        String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);\n\n        List<ScoredMemory> primary = visibleCandidates(player, npc, now).stream()\n                .filter(record -> effectiveKinds.contains(record.kind()))\n''',
    '''        Set<String> queryTerms = Set.copyOf(significantWords(query));\n        String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);\n        MemoryTemporalQuery temporalQuery = MemoryTemporalQuery.parse(query, now);\n\n        List<ScoredMemory> primary = visibleCandidates(player, npc, now).stream()\n                .filter(record -> effectiveKinds.contains(record.kind()))\n                .filter(temporalQuery::matches)\n'''
)
replace_once(
    memory,
    '''        Set<String> bridgeTerms = associationBridgeTerms(primary, queryTerms);\n        List<ScoredMemory> associated = primary.stream()\n                .map(scored -> new ScoredMemory(\n                        scored.record(),\n                        scored.score() + associationBonus(scored.record(), bridgeTerms, queryTerms)\n                ))\n''',
    '''        Map<String, Double> seedScores = new java.util.LinkedHashMap<>();\n        primary.stream().limit(24).forEach(scored -> seedScores.put(scored.record().id(), Math.max(0.0D, scored.score())));\n        Map<String, Double> graphScores = graphRetrievalEnabled()\n                ? graphRetriever.graphScores(primary.stream().map(ScoredMemory::record).toList(), seedScores)\n                : Map.of();\n        Set<String> bridgeTerms = associationBridgeTerms(primary, queryTerms);\n        List<ScoredMemory> associated = primary.stream()\n                .map(scored -> new ScoredMemory(\n                        scored.record(),\n                        scored.score() + associationBonus(scored.record(), bridgeTerms, queryTerms)\n                                + graphRetrievalWeight() * graphScores.getOrDefault(scored.record().id(), 0.0D)\n                ))\n'''
)
replace_once(
    memory,
    '''    private long sharedRefreshIntervalMillis() {\n        FileConfiguration config = plugin.getConfig();\n        int seconds = config == null ? 5 : Math.clamp(config.getInt(\n                "openai.assistant.memory.storage.shared_sync_seconds", 5\n        ), 1, 60);\n        return TimeUnit.SECONDS.toMillis(seconds);\n    }\n\n    private List<MemoryRecord> visibleCandidates(String playerId, String npcId, long now) {\n''',
    '''    private long sharedRefreshIntervalMillis() {\n        FileConfiguration config = plugin.getConfig();\n        int seconds = config == null ? 5 : Math.clamp(config.getInt(\n                "openai.assistant.memory.storage.shared_sync_seconds", 5\n        ), 1, 60);\n        return TimeUnit.SECONDS.toMillis(seconds);\n    }\n\n    /** Deterministic test/admin hook; production consolidation runs on the memory writer executor. */\n    public AssistantMemoryConsolidator.ConsolidationReport consolidateNow() {\n        return consolidator.consolidate();\n    }\n\n    private void consolidateSafely() {\n        if (closed || !consolidationEnabled()) {\n            return;\n        }\n        try {\n            consolidator.consolidate();\n        } catch (RuntimeException exception) {\n            warn("Could not consolidate assistant memory: " + exception.getMessage());\n        }\n    }\n\n    private boolean consolidationEnabled() {\n        FileConfiguration config = plugin.getConfig();\n        return config == null || config.getBoolean("openai.assistant.memory.consolidation.enabled", true);\n    }\n\n    private long consolidationIntervalMinutes() {\n        FileConfiguration config = plugin.getConfig();\n        return config == null ? 15L : Math.clamp(config.getLong(\n                "openai.assistant.memory.consolidation.interval_minutes", 15L\n        ), 5L, 24L * 60L);\n    }\n\n    private boolean graphRetrievalEnabled() {\n        FileConfiguration config = plugin.getConfig();\n        return config == null || config.getBoolean("openai.assistant.memory.retrieval.graph_enabled", true);\n    }\n\n    private double graphRetrievalWeight() {\n        FileConfiguration config = plugin.getConfig();\n        return config == null ? 0.35D : Math.clamp(config.getDouble(\n                "openai.assistant.memory.retrieval.graph_weight", 0.35D\n        ), 0.0D, 2.0D);\n    }\n\n    private List<MemoryRecord> visibleCandidates(String playerId, String npcId, long now) {\n'''
)


# ---------------------------------------------------------------------------
# Assistant orchestration: async semantic need refinement, relationship
# continuity, typed action proposals and richer observability.
# ---------------------------------------------------------------------------
service = "src/main/java/nl/hauntedmc/ailex/assistant/application/AssistantService.java"
replace_once(
    service,
    '''import nl.hauntedmc.ailex.AIlexPlugin;\nimport nl.hauntedmc.ailex.assistant.application.agent.AssistantReadAgent;\n''',
    '''import nl.hauntedmc.ailex.AIlexPlugin;\nimport nl.hauntedmc.ailex.assistant.action.AssistantActionProposal;\nimport nl.hauntedmc.ailex.assistant.action.AssistantActionType;\nimport nl.hauntedmc.ailex.assistant.application.agent.AssistantReadAgent;\n'''
)
replace_once(
    service,
    '''import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;\n''',
    '''import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;\nimport nl.hauntedmc.ailex.assistant.application.routing.SemanticNeedPlanner;\n'''
)
replace_once(
    service,
    '''import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;\nimport nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;\n''',
    '''import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;\nimport nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;\nimport nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantRelationshipMemoryService;\n'''
)
replace_once(
    service,
    '''    private final AssistantMemoryService memoryService;\n    private final AssistantExperienceMemoryService experienceMemory;\n    private final AssistantReadAgent readAgent;\n''',
    '''    private final AssistantMemoryService memoryService;\n    private final AssistantExperienceMemoryService experienceMemory;\n    private final AssistantRelationshipMemoryService relationshipMemory;\n    private final AssistantReadAgent readAgent;\n    private volatile SemanticNeedPlanner semanticNeedPlanner;\n'''
)
replace_once(
    service,
    '''    private final AtomicLong agentPlannerOutputTokens = new AtomicLong();\n    private final Map<String, AssistantCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();\n''',
    '''    private final AtomicLong agentPlannerOutputTokens = new AtomicLong();\n    private final AtomicLong semanticRefinements = new AtomicLong();\n    private final Map<String, AssistantCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();\n'''
)
replace_once(
    service,
    '''        this.memoryService = plugin.getAssistantMemoryService();\n        this.experienceMemory = new AssistantExperienceMemoryService(memoryService);\n        this.readAgent = new AssistantReadAgent(plugin, knowledgeIndex, memoryService, experienceMemory);\n    }\n''',
    '''        this.memoryService = plugin.getAssistantMemoryService();\n        this.experienceMemory = new AssistantExperienceMemoryService(memoryService);\n        this.relationshipMemory = new AssistantRelationshipMemoryService(memoryService);\n        this.readAgent = new AssistantReadAgent(plugin, knowledgeIndex, memoryService, experienceMemory);\n        this.semanticNeedPlanner = new SemanticNeedPlanner(knowledgeIndex.semanticEmbeddingProvider());\n        Thread.ofVirtual().name("AIlex-SemanticRouter-Warmup").start(semanticNeedPlanner::warm);\n    }\n'''
)
replace_once(
    service,
    '''    public AssistantReply respond(PreparedRequest request) {\n        AssistantSettings.ModelProfile initialProfile = request.settings().profileFor(request.analysis().mode());\n''',
    '''    public AssistantReply respond(PreparedRequest request) {\n        request = refineRequestSemantically(request);\n        AssistantSettings.ModelProfile initialProfile = request.settings().profileFor(request.analysis().mode());\n'''
)
replace_once(
    service,
    '''        if (enrichment.context().isBlank() && !request.settings().shadowMode()\n                && request.settings().cacheStaticAnswers() && isStaticIntent(request.analysis().intent())\n                && reply.memoryCandidates().isEmpty()) {\n''',
    '''        if (enrichment.context().isBlank() && !request.settings().shadowMode()\n                && request.settings().cacheStaticAnswers() && isStaticIntent(request.analysis().intent())\n                && reply.memoryCandidates().isEmpty() && reply.actionProposals().isEmpty()) {\n'''
)
replace_once(
    service,
    '''    private List<LocalKnowledgeIndex.KnowledgeChunk> retrieveEvidence(PreparedRequest request) {\n''',
    '''    private PreparedRequest refineRequestSemantically(PreparedRequest request) {\n        if (request == null || !request.settings().enabled() || request.analysis().mode() == AssistantMode.HANDOFF\n                || !plugin.getConfig().getBoolean("openai.assistant.routing.semantic.enabled", true)) {\n            return request;\n        }\n        SemanticNeedPlanner planner = semanticNeedPlanner;\n        double minimumSimilarity = Math.clamp(plugin.getConfig().getDouble(\n                "openai.assistant.routing.semantic.minimum_similarity", 0.42D\n        ), 0.0D, 1.0D);\n        double minimumMargin = Math.clamp(plugin.getConfig().getDouble(\n                "openai.assistant.routing.semantic.minimum_margin", 0.025D\n        ), 0.0D, 0.5D);\n        SemanticNeedPlanner.Decision decision = planner.refine(\n                request.message(), request.analysis(), request.contextPlan(), request.settings(),\n                minimumSimilarity, minimumMargin\n        );\n        if (!decision.semanticallyRefined()) {\n            return request;\n        }\n        RequiredContextPlanner.Plan refinedPlan = planner.mergePlan(\n                request.contextPlan(), decision, request.settings()\n        );\n        AssistantIntentClassifier.Analysis refinedAnalysis = new AssistantIntentClassifier.Analysis(\n                decision.intent(), request.settings().resolveMode(decision.mode()), request.analysis().language()\n        );\n        String memory = request.memory();\n        UUID playerId = UUID.fromString(request.playerId());\n        if (memoryService != null && request.settings().toolAllowed("session")\n                && refinedPlan.durableMemory() && memory.isBlank()) {\n            memory = memoryContext(playerId, request.npcMemoryId(), request.message(), refinedPlan.eventMemory());\n        }\n        if (relationshipMemory != null && request.settings().toolAllowed("session")\n                && (refinedPlan.durableMemory() || decision.intent() == AssistantIntent.CONTEXT_FOLLOWUP\n                || request.dialogueContext().active())) {\n            String relationship = relationshipMemory.promptContext(playerId, request.npcMemoryId());\n            if (!relationship.isBlank()) {\n                memory = memory.isBlank()\n                        ? "[Relationship continuity]\\n" + relationship\n                        : memory + "\\n\\n[Relationship continuity]\\n" + relationship;\n            }\n        }\n        semanticRefinements.incrementAndGet();\n        return new PreparedRequest(\n                request.playerId(), request.playerName(), request.npcName(), request.npcMemoryId(), request.message(),\n                request.systemPrompt(), request.userPrompt(), refinedAnalysis, request.settings(), refinedPlan,\n                refinedPlan.knowledge(), request.snapshot(), memory, request.dialogueContext(),\n                request.canWriteSharedMemory(), request.preparedAtNanos()\n        );\n    }\n\n    private List<LocalKnowledgeIndex.KnowledgeChunk> retrieveEvidence(PreparedRequest request) {\n'''
)
replace_once(
    service,
    '''    public void reload() {\n        knowledgeIndex.reload();\n        if (memoryService != null) {\n            memoryService.reload();\n        }\n        staticReplyCache.clear();\n        circuitBreakers.clear();\n    }\n''',
    '''    public void reload() {\n        knowledgeIndex.reload();\n        semanticNeedPlanner = new SemanticNeedPlanner(knowledgeIndex.semanticEmbeddingProvider());\n        Thread.ofVirtual().name("AIlex-SemanticRouter-Warmup").start(semanticNeedPlanner::warm);\n        if (memoryService != null) {\n            memoryService.reload();\n        }\n        staticReplyCache.clear();\n        circuitBreakers.clear();\n    }\n'''
)
replace_once(
    service,
    '''                + ", agent_planner_output_tokens=" + agentPlannerOutputTokens.get();\n''',
    '''                + ", agent_planner_output_tokens=" + agentPlannerOutputTokens.get()\n                + ", semantic_refinements=" + semanticRefinements.get();\n'''
)
replace_once(
    service,
    '''            String confidence = getString(object, "confidence");\n            String handoff = getString(object, "handoff");\n            List<MemoryCandidate> memoryCandidates = new ArrayList<>();\n''',
    '''            String confidence = getString(object, "confidence");\n            String handoff = getString(object, "handoff");\n            List<MemoryCandidate> memoryCandidates = new ArrayList<>();\n'''
)
# Parse action proposals just before constructing AssistantReply.
replace_once(
    service,
    '''            return new AssistantReply(\n                    safeLines, Set.copyOf(sources), confidence, handoff, List.copyOf(memoryCandidates),\n                    Map.copyOf(claimEvidence), !safeLines.isEmpty()\n            );\n''',
    '''            List<AssistantActionProposal> actionProposals = new ArrayList<>();\n            JsonArray actions = object.getAsJsonArray("action_proposals");\n            if (actions != null) {\n                for (JsonElement element : actions) {\n                    if (!element.isJsonObject()) {\n                        return AssistantReply.invalid();\n                    }\n                    JsonObject action = element.getAsJsonObject();\n                    String type = getString(action, "type").toUpperCase(Locale.ROOT);\n                    try {\n                        AssistantActionType actionType = AssistantActionType.valueOf(type);\n                        if (actionProposals.size() < 2) {\n                            actionProposals.add(new AssistantActionProposal(actionType, getString(action, "reason")));\n                        }\n                    } catch (IllegalArgumentException exception) {\n                        return AssistantReply.invalid();\n                    }\n                }\n            }\n            return new AssistantReply(\n                    safeLines, Set.copyOf(sources), confidence, handoff, List.copyOf(memoryCandidates),\n                    List.copyOf(actionProposals), Map.copyOf(claimEvidence), !safeLines.isEmpty()\n            );\n'''
)
# Add action schema after memory_candidates.
replace_once(
    service,
    '''        memoryCandidates.add("items", memoryItem);\n        properties.add("memory_candidates", memoryCandidates);\n\n        schema.add("properties", properties);\n''',
    '''        memoryCandidates.add("items", memoryItem);\n        properties.add("memory_candidates", memoryCandidates);\n\n        JsonObject actionProposals = new JsonObject();\n        actionProposals.addProperty("type", "array");\n        JsonObject actionItem = new JsonObject();\n        actionItem.addProperty("type", "object");\n        JsonObject actionProperties = new JsonObject();\n        actionProperties.add("type", enumProperty("FOLLOW_REQUESTER", "COME_HERE", "STOP_MOVING"));\n        JsonObject actionReason = new JsonObject();\n        actionReason.addProperty("type", "string");\n        actionProperties.add("reason", actionReason);\n        actionItem.add("properties", actionProperties);\n        JsonArray actionRequired = new JsonArray();\n        actionRequired.add("type");\n        actionRequired.add("reason");\n        actionItem.add("required", actionRequired);\n        actionItem.addProperty("additionalProperties", false);\n        actionProposals.add("items", actionItem);\n        properties.add("action_proposals", actionProposals);\n\n        schema.add("properties", properties);\n'''
)
replace_once(
    service,
    '''        required.add("memory_candidates");\n        schema.add("required", required);\n''',
    '''        required.add("memory_candidates");\n        required.add("action_proposals");\n        schema.add("required", required);\n'''
)
replace_once(
    service,
    '''                + "real-world locations, precise Minecraft coordinates, reports, sanctions, inferred traits or other-player data.";\n''',
    '''                + "real-world locations, precise Minecraft coordinates, reports, sanctions, inferred traits or other-player data. "\n                + "Only when the player explicitly asks this physical NPC to follow them, come here, or stop moving, you may "\n                + "emit one matching action_proposals item. An action proposal is not authority: deterministic server code "\n                + "will independently validate it. Otherwise action_proposals must be empty.";\n'''
)


# ---------------------------------------------------------------------------
# Main-thread action execution: only validated model proposals can reach the
# existing NPC action queue, and only after response generation completes.
# ---------------------------------------------------------------------------
controller = "src/main/java/nl/hauntedmc/ailex/assistant/chat/AssistantChatController.java"
replace_once(
    controller,
    '''import nl.hauntedmc.ailex.AIlexPlugin;\nimport nl.hauntedmc.ailex.assistant.application.AssistantService;\n''',
    '''import nl.hauntedmc.ailex.AIlexPlugin;\nimport nl.hauntedmc.ailex.assistant.action.AssistantActionService;\nimport nl.hauntedmc.ailex.assistant.application.AssistantService;\n'''
)
replace_once(
    controller,
    '''    private final AssistantService assistantService;\n    private final AssistantChatConfiguration configuration;\n''',
    '''    private final AssistantService assistantService;\n    private final AssistantActionService assistantActionService;\n    private final AssistantChatConfiguration configuration;\n'''
)
replace_once(
    controller,
    '''        this.plugin = plugin;\n        this.assistantService = plugin.getAssistantService();\n        this.configuration = new AssistantChatConfiguration(plugin::getConfig);\n''',
    '''        this.plugin = plugin;\n        this.assistantService = plugin.getAssistantService();\n        this.assistantActionService = new AssistantActionService(plugin);\n        this.configuration = new AssistantChatConfiguration(plugin::getConfig);\n'''
)
replace_once(
    controller,
    '''            String response;\n            if (prepared.settings().enabled()) {\n                AssistantReply reply = assistantService.respond(prepared);\n                response = String.join("\\n", reply.lines());\n            } else {\n                response = client.getChatResponse(target.systemPrompt(), prepared.userPrompt());\n                assistantService.recordDirectResponse(prepared, response);\n            }\n''',
    '''            String response;\n            AssistantReply reply = null;\n            if (prepared.settings().enabled()) {\n                reply = assistantService.respond(prepared);\n                response = String.join("\\n", reply.lines());\n            } else {\n                response = client.getChatResponse(target.systemPrompt(), prepared.userPrompt());\n                assistantService.recordDirectResponse(prepared, response);\n            }\n'''
)
replace_once(
    controller,
    '''            Component result = FormatterUtils.serializer.deserialize(target.displayName() + ": ")\n                    .append(Component.text(response, NamedTextColor.WHITE));\n            Bukkit.getScheduler().runTask(plugin, () -> deliverTrackedResponse(requestId, source, result));\n''',
    '''            Component result = FormatterUtils.serializer.deserialize(target.displayName() + ": ")\n                    .append(Component.text(response, NamedTextColor.WHITE));\n            AssistantReply completedReply = reply;\n            Bukkit.getScheduler().runTask(plugin, () -> {\n                if (completedReply != null && target.npc() != null && !completedReply.actionProposals().isEmpty()) {\n                    assistantActionService.validateAndExecute(\n                            source, target.npc(), prepared.message(), completedReply.actionProposals()\n                    );\n                }\n                deliverTrackedResponse(requestId, source, result);\n            });\n'''
)


# ---------------------------------------------------------------------------
# Configuration for semantic routing, memory graph/consolidation, social
# utility and controlled embodied actions.
# ---------------------------------------------------------------------------
config = "src/main/resources/config.yml"
replace_once(
    config,
    '''    routing:\n      default_language: "nl"\n      allowed_languages: ["nl", "en", "de"]\n      language_detection: true\n      clarify_only_when_required: true\n''',
    '''    routing:\n      default_language: "nl"\n      allowed_languages: ["nl", "en", "de"]\n      language_detection: true\n      clarify_only_when_required: true\n      # Learned need prediction refines ambiguous requests after deterministic policy routing.\n      semantic:\n        enabled: true\n        minimum_similarity: 0.42\n        minimum_margin: 0.025\n'''
)
replace_once(
    config,
    '''    agent:\n      # A bounded planner may request additional read-only evidence. It can never execute server mutations.\n      enabled: true\n      planner_model: "gpt-5.6-luna"\n      max_tool_calls_per_round: 2\n''',
    '''    agent:\n      # A bounded planner may request additional read-only evidence. It can never execute server mutations.\n      enabled: true\n      planner_model: "gpt-5.6-luna"\n      max_tool_calls_per_round: 2\n    actions:\n      # The model can only propose these low-risk NPC actions; deterministic validation remains authoritative.\n      enabled: true\n      allowed: ["FOLLOW_REQUESTER", "COME_HERE", "STOP_MOVING"]\n'''
)
replace_once(
    config,
    '''      storage:\n        # sqlite is self-contained. mysql provides one shared identity across multiple AIlex runtimes.\n        backend: "sqlite"\n''',
    '''      retrieval:\n        # Associative graph activation is fused with lexical/salience/recency memory ranking.\n        graph_enabled: true\n        graph_weight: 0.35\n      consolidation:\n        # Evidence-grounded deterministic event→episode consolidation; no per-message LLM reflection pass.\n        enabled: true\n        interval_minutes: 15\n      storage:\n        # sqlite is self-contained. mysql provides one shared identity across multiple AIlex runtimes.\n        backend: "sqlite"\n'''
)
replace_once(
    config,
    '''      social_graph_window_seconds: 180\n      strong_pair_score: 2.5\n''',
    '''      social_graph_window_seconds: 180\n      strong_pair_score: 2.5\n      utility:\n        threshold: 0.25\n        helpful_weight: 1.25\n        privacy_cost: 1.20\n        error_cost: 0.75\n        repetition_cost: 0.85\n'''
)

print("AIlex lifelong runtime wiring applied successfully")
