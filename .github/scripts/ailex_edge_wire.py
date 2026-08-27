from pathlib import Path
import re


def read(path):
    return Path(path).read_text()


def write(path, value):
    Path(path).write_text(value)


def once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"missing patch anchor: {label}")
    return text.replace(old, new, 1)


# AssistantService: first-class prompt composer, topic context and verified reconsolidation.
path = "src/main/java/nl/hauntedmc/ailex/assistant/application/AssistantService.java"
text = read(path)
text = once(
    text,
    "import nl.hauntedmc.ailex.assistant.application.inference.AssistantGroundingPolicy;\n",
    "import nl.hauntedmc.ailex.assistant.application.inference.AssistantGroundingPolicy;\n"
    "import nl.hauntedmc.ailex.assistant.application.prompt.AssistantPromptComposer;\n",
    "assistant prompt import",
)
text = once(
    text,
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;\n",
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;\n"
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryTopicView;\n",
    "topic view import",
)
text = once(
    text,
    "    private final ContextCompiler contextCompiler = new ContextCompiler();\n",
    "    private final ContextCompiler contextCompiler = new ContextCompiler();\n"
    "    private final AssistantPromptComposer promptComposer = new AssistantPromptComposer();\n"
    "    private final MemoryTopicView memoryTopicView = new MemoryTopicView();\n",
    "prompt fields",
)
text, count = re.subn(
    r"    private String buildSystemPrompt\(PreparedRequest request\) \{.*?\n    \}\n\n    private String buildPrompt",
    "    private String buildSystemPrompt(PreparedRequest request) {\n"
    "        return promptComposer.systemPrompt(request);\n"
    "    }\n\n"
    "    private String buildPrompt",
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit("could not replace buildSystemPrompt")
text, count = re.subn(
    r"    private String responseInstruction\(PreparedRequest request\) \{.*?\n    \}\n\n    private AssistantReply parseStructuredReply",
    "    private String responseInstruction(PreparedRequest request) {\n"
    "        return promptComposer.turnInstruction(request);\n"
    "    }\n\n"
    "    private AssistantReply parseStructuredReply",
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit("could not replace responseInstruction")
text = once(
    text,
    '        persistCandidates(request, reply);\n        recordExperience(request, enrichment, reply, "accepted");\n',
    '        persistCandidates(request, reply);\n'
    '        recordExperience(request, enrichment, reply, "accepted");\n'
    "        if (memoryService != null) {\n"
    "            memoryService.reconsolidateVerifiedEvidence(reply.coveredEvidenceIds());\n"
    "        }\n",
    "verified reconsolidation",
)
old_semantic = '''        if (!semantic.isEmpty()) {
            output.append("Semantic memory (ranked and associatively expanded for this player and request):\\n");
            for (MemoryRecord record : semantic) {
                output.append("- evidence_id=memory.").append(record.id())
                        .append(" scope=").append(record.scope().name().toLowerCase(Locale.ROOT))
                        .append(" kind=").append(record.kind().name().toLowerCase(Locale.ROOT))
                        .append(" key=").append(record.key()).append(" value=").append(record.value())
                        .append(" confidence=").append(String.format(Locale.ROOT, "%.2f", record.confidence()))
                        .append('\\n');
            }
        }
'''
new_semantic = '''        if (!semantic.isEmpty()) {
            output.append("Topic-structured semantic memory (every item preserves its exact evidence id):\\n")
                    .append(memoryTopicView.render(semantic, 5_500));
        }
'''
text = once(text, old_semantic, new_semantic, "topic memory rendering")
write(path, text)

# Read agent: compact planner contract + duplicate-call suppression.
path = "src/main/java/nl/hauntedmc/ailex/assistant/application/agent/AssistantReadAgent.java"
text = read(path)
text = once(
    text,
    "import nl.hauntedmc.ailex.assistant.domain.AssistantMode;\n",
    "import nl.hauntedmc.ailex.assistant.domain.AssistantMode;\n"
    "import nl.hauntedmc.ailex.assistant.application.prompt.AssistantPromptComposer;\n",
    "read agent prompt import",
)
text = once(
    text,
    "    private final AssistantToolRegistry toolRegistry;\n",
    "    private final AssistantToolRegistry toolRegistry;\n"
    "    private final AssistantPromptComposer promptComposer = new AssistantPromptComposer();\n",
    "read agent prompt field",
)
text = once(
    text,
    "        Set<String> usedTools = new HashSet<>();\n",
    "        Set<String> usedTools = new HashSet<>();\n"
    "        Set<String> callFingerprints = new HashSet<>();\n",
    "tool fingerprint set",
)
old = '''                AssistantTool.ToolResult observation = toolRegistry.execute(request, call);
                history.add(OpenAiToolPlanningClient.functionCallInput(call));
'''
new = '''                String fingerprint = call.name() + '|' + clean(call.arguments()).toLowerCase(Locale.ROOT);
                if (!callFingerprints.add(fingerprint)) {
                    history.add(OpenAiToolPlanningClient.functionCallInput(call));
                    history.add(OpenAiToolPlanningClient.functionOutput(
                            call.callId(), "Equivalent tool call already executed; use existing evidence or choose a different query."
                    ));
                    continue;
                }
                AssistantTool.ToolResult observation = toolRegistry.execute(request, call);
                history.add(OpenAiToolPlanningClient.functionCallInput(call));
'''
text = once(text, old, new, "duplicate tool suppression")
text, count = re.subn(
    r"    private String plannerPrompt\(\n            AssistantService\.PreparedRequest request,\n            List<LocalKnowledgeIndex\.KnowledgeChunk> initialEvidence\n    \) \{.*?\n    \}\n\n    private Duration remainingForRound",
    '''    private String plannerPrompt(
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
                + "\\nroute=" + request.analysis().intent().name().toLowerCase(Locale.ROOT)
                + " mode=" + request.analysis().mode().name().toLowerCase(Locale.ROOT)
                + "\\nreviewed_evidence=" + evidence
                + " memory_present=" + memoryPresent
                + " live_evidence=" + (liveIds.isEmpty() ? "none" : String.join(",", liveIds))
                + "\\n" + promptComposer.plannerContract();
    }

    private Duration remainingForRound''',
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit("could not replace plannerPrompt")
write(path, text)

# Tool descriptions: selection policy belongs with tools rather than repeated planner prose.
path = "src/main/java/nl/hauntedmc/ailex/assistant/application/agent/AssistantToolRegistry.java"
text = read(path)
replacements = {
    "Search scoped durable player/shared/NPC memory by meaning.":
        "Use for explicit player-owned facts, preferences, goals or remembered context not already supplied. Search by the smallest discriminative concept; do not use for current live state or official server rules.",
    "Inspect historical versions of one semantic memory key.":
        "Use for corrections, what changed, what was true earlier, or conflicting remembered values. Prefer a stable semantic key; this is historical evidence, not current-state inspection.",
    "Recall verified procedural lessons from prior AIlex outcomes.":
        "Strategy-only recall of externally verified prior AIlex outcomes. Use to choose a better retrieval/response approach; never cite this tool as factual evidence about a player or server.",
    "Search reviewed HauntedMC/server knowledge with a focused query.":
        "Use for HauntedMC-specific commands, rules, ranks, systems and reviewed server facts. Query narrowly. Reviewed knowledge outranks player-learned shared claims but not live runtime state for current-state questions.",
    "Read one source family from the frozen safe Minecraft snapshot.":
        "Use for current requester/world/server/NPC state when the answer depends on what is true now. The snapshot is frozen on the Paper thread; inspect only the source family materially needed.",
}
for old, replacement in replacements.items():
    text = once(text, old, replacement, f"tool description {old[:20]}")
old_chunk = '''            output.append("evidence_id=").append(chunk.id()).append(" title=").append(chunk.title())
                    .append(" authority=").append(chunk.authority()).append('\\n').append(chunk.text()).append('\\n');
'''
new_chunk = '''            output.append("evidence_id=").append(chunk.id()).append(" title=").append(chunk.title())
                    .append(" authority=").append(chunk.authority())
                    .append(" updated=").append(chunk.updated().isBlank() ? "unknown" : chunk.updated())
                    .append(" source=").append(chunk.source().isBlank() ? "reviewed-local" : chunk.source())
                    .append('\\n').append(chunk.text()).append('\\n');
'''
text = once(text, old_chunk, new_chunk, "tool knowledge provenance")
write(path, text)

# Local knowledge: actually consume YAML front matter; stable ids/provenance/freshness affect ranking.
path = "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/knowledge/LocalKnowledgeIndex.java"
text = read(path)
text = once(
    text,
    "    private final JavaPlugin plugin;\n",
    "    private final JavaPlugin plugin;\n"
    "    private final KnowledgeDocumentParser documentParser = new KnowledgeDocumentParser();\n",
    "knowledge parser field",
)
text = once(
    text,
    "            double combined = lexical + semantic * SEMANTIC_WEIGHT + rrf * RRF_WEIGHT;\n",
    "            double combined = lexical + semantic * SEMANTIC_WEIGHT + rrf * RRF_WEIGHT\n"
    "                    + freshnessWeight(chunk) * 0.30D;\n",
    "knowledge freshness rank",
)
text = once(
    text,
    '            case "official" -> 1.25D;\n',
    '            case "operator-confirmed" -> 1.32D;\n'
    '            case "official" -> 1.25D;\n',
    "operator authority",
)
marker = "    private double cosine(double[] left, double[] right) {"
freshness = '''    private double freshnessWeight(KnowledgeChunk chunk) {
        if (chunk == null || chunk.updated().isBlank()) {
            return 0.35D;
        }
        try {
            long days = Math.max(0L, java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.parse(chunk.updated()), LocalDate.now()
            ));
            boolean volatileCategory = chunk.category().contains("current") || chunk.category().contains("event")
                    || chunk.category().contains("status");
            double halfLife = volatileCategory ? 45.0D : 365.0D;
            return 1.0D / (1.0D + days / halfLife);
        } catch (DateTimeParseException ignored) {
            return 0.35D;
        }
    }

'''
text = once(text, marker, freshness + marker, "knowledge freshness method")
text, count = re.subn(
    r"    private List<KnowledgeChunk> parseDocument\(String source, String content\) \{.*?\n    \}\n\n    private Map<String, String> metadata",
    '''    private List<KnowledgeChunk> parseDocument(String source, String content) {
        return documentParser.parse(source, content).stream()
                .map(section -> new KnowledgeChunk(
                        section.id(), section.title(), section.aliases(), section.text(), section.expired(),
                        section.category(), section.authority(), section.source(), section.updated()
                ))
                .toList();
    }

    private Map<String, String> metadata''',
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit("could not replace parseDocument")
text, count = re.subn(
    r"    public record KnowledgeChunk\(\n            String id,\n            String title,\n            List<String> aliases,\n            String text,\n            boolean expired,\n            String category,\n            String authority\n    \) \{.*?\n    \}\n\n    private record QueryFeatures",
    '''    public record KnowledgeChunk(
            String id,
            String title,
            List<String> aliases,
            String text,
            boolean expired,
            String category,
            String authority,
            String source,
            String updated
    ) {
        public KnowledgeChunk {
            id = id == null ? "" : id;
            title = title == null ? "" : title;
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            text = text == null ? "" : text;
            category = category == null ? "" : category;
            authority = authority == null ? "" : authority;
            source = source == null ? "" : source;
            updated = updated == null ? "" : updated;
        }

        /** Source-compatible constructor retained for deterministic tests/integrations. */
        public KnowledgeChunk(
                String id,
                String title,
                List<String> aliases,
                String text,
                boolean expired,
                String category,
                String authority
        ) {
            this(id, title, aliases, text, expired, category, authority, "", "");
        }
    }

    private record QueryFeatures''',
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit("could not extend KnowledgeChunk")
write(path, text)

# Memory service: scheduled retention + verified-use reconsolidation.
path = "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/memory/AssistantMemoryService.java"
text = read(path)
text = once(
    text,
    "    private final MemoryTruthResolver truthResolver = new MemoryTruthResolver();\n",
    "    private final MemoryTruthResolver truthResolver = new MemoryTruthResolver();\n"
    "    private final MemoryRetentionPolicy retentionPolicy = new MemoryRetentionPolicy();\n",
    "retention field",
)
anchor = '''        if (consolidationEnabled()) {
            writer.scheduleWithFixedDelay(
                    this::consolidateSafely, 60L, consolidationIntervalMinutes(), TimeUnit.MINUTES
            );
        }
'''
replacement = anchor + '''        if (retentionEnabled()) {
            writer.scheduleWithFixedDelay(
                    this::maintainRetentionSafely, 5L, retentionIntervalMinutes(), TimeUnit.MINUTES
            );
        }
'''
text = once(text, anchor, replacement, "retention schedule")
insertion_anchor = "    private boolean consolidationEnabled() {"
methods = '''    /** Reactivates only evidence that was actually used in a validated answer; factual confidence is unchanged. */
    public synchronized void reconsolidateVerifiedEvidence(Set<String> evidenceIds) {
        if (closed || evidenceIds == null || evidenceIds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<String> ids = evidenceIds.stream()
                .filter(id -> id != null && id.startsWith("memory."))
                .map(id -> id.substring("memory.".length()))
                .collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        activeRecords.values().stream()
                .filter(record -> ids.contains(record.id()))
                .map(record -> retentionPolicy.reconsolidateVerifiedUse(record, now))
                .filter(java.util.Objects::nonNull)
                .forEach(this::store);
    }

    private void maintainRetentionSafely() {
        if (closed || !retentionEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            List<MemoryRecord> snapshot = activeSnapshot();
            for (MemoryRecord record : snapshot) {
                if (retentionPolicy.shouldExpire(record, now, snapshot)
                        && activeRecords.remove(record.identityKey(), record)) {
                    unindexRecord(record);
                    repository.upsert(record.expireAt(now));
                }
            }
        } catch (RuntimeException exception) {
            warn("Could not maintain assistant memory retention: " + exception.getMessage());
        }
    }

    private boolean retentionEnabled() {
        FileConfiguration config = plugin.getConfig();
        return config == null || config.getBoolean("openai.assistant.memory.retention.enabled", true);
    }

    private long retentionIntervalMinutes() {
        FileConfiguration config = plugin.getConfig();
        return config == null ? 30L : Math.clamp(config.getLong(
                "openai.assistant.memory.retention.interval_minutes", 30L
        ), 5L, 24L * 60L);
    }

'''
text = once(text, insertion_anchor, methods + insertion_anchor, "retention methods")
write(path, text)

# Proactive service: all social goals + private persistent follow-up path.
path = "src/main/java/nl/hauntedmc/ailex/assistant/proactive/ProactiveChatService.java"
text = read(path)
text = once(
    text,
    "import org.bukkit.entity.Player;\n",
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;\n\nimport org.bukkit.entity.Player;\n",
    "proactive memory import",
)
text = once(
    text,
    "    private final SocialConversationGraph socialGraph = new SocialConversationGraph();\n",
    "    private final SocialConversationGraph socialGraph = new SocialConversationGraph();\n"
    "    private final ProactiveGoalService goalService;\n",
    "goal service field",
)
old_ctor = '''    public ProactiveChatService(Supplier<ProactiveChatSettings> settingsSupplier) {
        this(settingsSupplier, System::currentTimeMillis);
    }

    ProactiveChatService(Supplier<ProactiveChatSettings> settingsSupplier, LongSupplier currentTimeMillis) {
        this.settingsSupplier = settingsSupplier;
        this.currentTimeMillis = currentTimeMillis;
        long now = currentTimeMillis.getAsLong();
        this.lastBotMessageMillis = new AtomicLong(now);
        this.lastPlayerMessageMillis = new AtomicLong(now);
    }
'''
new_ctor = '''    public ProactiveChatService(Supplier<ProactiveChatSettings> settingsSupplier) {
        this(settingsSupplier, System::currentTimeMillis, null);
    }

    public ProactiveChatService(
            Supplier<ProactiveChatSettings> settingsSupplier,
            AssistantMemoryService memoryService
    ) {
        this(settingsSupplier, System::currentTimeMillis, memoryService);
    }

    ProactiveChatService(Supplier<ProactiveChatSettings> settingsSupplier, LongSupplier currentTimeMillis) {
        this(settingsSupplier, currentTimeMillis, null);
    }

    ProactiveChatService(
            Supplier<ProactiveChatSettings> settingsSupplier,
            LongSupplier currentTimeMillis,
            AssistantMemoryService memoryService
    ) {
        this.settingsSupplier = settingsSupplier;
        this.currentTimeMillis = currentTimeMillis;
        this.goalService = new ProactiveGoalService(memoryService);
        long now = currentTimeMillis.getAsLong();
        this.lastBotMessageMillis = new AtomicLong(now);
        this.lastPlayerMessageMillis = new AtomicLong(now);
    }
'''
text = once(text, old_ctor, new_ctor, "proactive constructors")
old_decision = '''        InterventionDecision decision = ProactiveInterventionPolicy.evaluateQuestion(
                source, message, players, activeConversation, socialGraph, now, questions
        );
        socialGraph.observe(source, message, players, now, questions.socialGraphWindowMillis());

        if (!settings.enabled()) {
            return;
        }
'''
new_decision = '''        InterventionDecision decision = ProactiveInterventionPolicy.evaluateQuestion(
                source, message, players, activeConversation, socialGraph, now, questions
        );
        SocialConversationGraph.ThreadView thread = socialGraph.threadView(
                source.getUniqueId(), now, questions.socialGraphWindowMillis()
        );
        java.util.Optional<ProactiveChatTrigger> goalTrigger = goalService.chatTrigger(
                source, message, thread, activeConversation
        );
        InterventionDecision goalDecision = goalTrigger
                .map(trigger -> ProactiveInterventionPolicy.evaluateGoal(
                        trigger.goal(), source, activeConversation, trigger.privateOnly(), socialGraph, now, questions
                ))
                .orElse(new InterventionDecision(CommunityGoal.SILENCE, 0, 1, 0, 0, -1, false));
        socialGraph.observe(source, message, players, now, questions.socialGraphWindowMillis());

        if (!settings.enabled()) {
            return;
        }
        if (goalTrigger.isPresent() && settings.goals().enabled(goalTrigger.get().goal()) && goalDecision.speak()
                && passesProbability(settings.goals().probability())
                && submitIfOffCooldown(source, goalTrigger.get(), goalDecision.goal(), now, settings, consumer)) {
            return;
        }
'''
text = once(text, old_decision, new_decision, "proactive goal evaluation")
text = text.replace(
    "CommunityGoal.SUPPORT_CONVERSATION,\n                        now,",
    "CommunityGoal.CELEBRATE,\n                        now,",
    1,
)
scheduled_anchor = "    public void recordBotResponse() {"
scheduled_methods = '''    public void checkForScheduledGoals(
            Collection<? extends Player> onlinePlayers,
            TriggerConsumer consumer
    ) {
        long now = currentTimeMillis.getAsLong();
        ProactiveChatSettings settings = settingsSupplier.get();
        if (!settings.enabled() || !settings.goals().enabled(CommunityGoal.FOLLOW_UP)
                || onlinePlayers == null || onlinePlayers.isEmpty()) {
            return;
        }
        for (Player player : onlinePlayers) {
            java.util.Optional<ProactiveChatTrigger> candidate = goalService.followUp(player, now);
            if (candidate.isEmpty()) {
                continue;
            }
            ProactiveChatTrigger trigger = candidate.get();
            InterventionDecision decision = ProactiveInterventionPolicy.evaluateGoal(
                    CommunityGoal.FOLLOW_UP, player, false, true, socialGraph, now, settings.questions()
            );
            if (decision.speak() && passesProbability(settings.goals().followUpProbability())
                    && submitIfOffCooldown(player, trigger, CommunityGoal.FOLLOW_UP, now, settings, consumer)) {
                return;
            }
        }
    }

    public void recordDeliveredGoal(Player player, ProactiveChatTrigger trigger) {
        if (trigger != null && trigger.goal() == CommunityGoal.FOLLOW_UP) {
            goalService.recordFollowUpDelivered(player, currentTimeMillis.getAsLong());
        }
    }

'''
text = once(text, scheduled_anchor, scheduled_methods + scheduled_anchor, "scheduled social goals")
write(path, text)

# Proactive settings: add goal controls while preserving old constructor source compatibility.
path = "src/main/java/nl/hauntedmc/ailex/assistant/proactive/ProactiveChatSettings.java"
text = read(path)
text = text.replace("import java.util.List;\n", "import java.util.EnumSet;\nimport java.util.List;\nimport java.util.Set;\n")
text = once(
    text,
    "        CollectiveSettings collective,\n        IdleSettings idle\n) {",
    "        CollectiveSettings collective,\n        IdleSettings idle,\n        GoalSettings goals\n) {",
    "goal settings record field",
)
text = once(
    text,
    '''                new IdleSettings(
                        config.getBoolean(PATH + ".idle.enabled", true),
                        secondsToMillis(config.getLong(PATH + ".idle.silence_seconds", 30L * 60L)),
                        secondsToMillis(config.getLong(PATH + ".idle.check_interval_seconds", 60L)),
                        probability(config.getDouble(PATH + ".idle.probability", 0.02D)),
                        Math.clamp(config.getInt(PATH + ".idle.minimum_online_players", 1), 1, 100),
                        secondsToMillis(config.getLong(PATH + ".idle.recent_chat_window_seconds", 600L))
                )
        );''',
    '''                new IdleSettings(
                        config.getBoolean(PATH + ".idle.enabled", true),
                        secondsToMillis(config.getLong(PATH + ".idle.silence_seconds", 30L * 60L)),
                        secondsToMillis(config.getLong(PATH + ".idle.check_interval_seconds", 60L)),
                        probability(config.getDouble(PATH + ".idle.probability", 0.02D)),
                        Math.clamp(config.getInt(PATH + ".idle.minimum_online_players", 1), 1, 100),
                        secondsToMillis(config.getLong(PATH + ".idle.recent_chat_window_seconds", 600L))
                ),
                GoalSettings.from(config)
        );''',
    "goal settings factory",
)
text = once(
    text,
    "                new IdleSettings(false, 1_800_000L, 60_000L, 0.0D, 1, 600_000L)\n        );",
    "                new IdleSettings(false, 1_800_000L, 60_000L, 0.0D, 1, 600_000L),\n"
    "                GoalSettings.disabled()\n"
    "        );",
    "disabled goal settings",
)
insert = '''
    /** Source-compatible constructor retained for existing tests/integrations. */
    public ProactiveChatSettings(
            boolean enabled,
            String responseVisibility,
            long cooldownMillis,
            JoinSettings join,
            QuestionSettings questions,
            CollectiveSettings collective,
            IdleSettings idle
    ) {
        this(enabled, responseVisibility, cooldownMillis, join, questions, collective, idle, GoalSettings.defaults());
    }

'''
text = once(
    text,
    "    private static ProactiveChatSettings disabled() {",
    insert + "    private static ProactiveChatSettings disabled() {",
    "compat settings ctor",
)
goal_record = '''
    public record GoalSettings(
            boolean enabled,
            Set<CommunityGoal> enabledGoals,
            double probability,
            double followUpProbability
    ) {
        static GoalSettings from(FileConfiguration config) {
            Set<CommunityGoal> goals = EnumSet.noneOf(CommunityGoal.class);
            List<String> configured = config.getStringList(PATH + ".goals.enabled_goals");
            if (configured.isEmpty()) {
                goals.addAll(defaultGoals());
            } else {
                for (String value : configured) {
                    try {
                        goals.add(CommunityGoal.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_')));
                    } catch (IllegalArgumentException ignored) {
                        // Invalid config values are ignored fail-closed.
                    }
                }
            }
            goals.remove(CommunityGoal.SILENCE);
            return new GoalSettings(
                    config.getBoolean(PATH + ".goals.enabled", true),
                    Set.copyOf(goals),
                    probability(config.getDouble(PATH + ".goals.probability", 0.30D)),
                    probability(config.getDouble(PATH + ".goals.follow_up_probability", 0.12D))
            );
        }

        static GoalSettings defaults() {
            return new GoalSettings(true, defaultGoals(), 0.30D, 0.12D);
        }

        static GoalSettings disabled() {
            return new GoalSettings(false, Set.of(), 0.0D, 0.0D);
        }

        public boolean enabled(CommunityGoal goal) {
            return enabled && goal != null && enabledGoals.contains(goal);
        }

        private static Set<CommunityGoal> defaultGoals() {
            return Set.of(
                    CommunityGoal.HELP_NEW_PLAYER, CommunityGoal.WELCOME, CommunityGoal.CELEBRATE,
                    CommunityGoal.SUPPORT_CONVERSATION, CommunityGoal.CONNECT, CommunityGoal.DEFUSE,
                    CommunityGoal.FOLLOW_UP, CommunityGoal.INFORM
            );
        }
    }
'''
text = once(text, "\n    public record IdleSettings(", goal_record + "\n    public record IdleSettings(", "goal settings type")
write(path, text)

# Chat controller: memory-aware proactive service, private followups, action outcome learning.
path = "src/main/java/nl/hauntedmc/ailex/assistant/chat/AssistantChatController.java"
text = read(path)
text = once(
    text,
    "import nl.hauntedmc.ailex.assistant.application.AssistantService;\n",
    "import nl.hauntedmc.ailex.assistant.application.AssistantService;\n"
    "import nl.hauntedmc.ailex.assistant.action.AssistantActionOutcomeRecorder;\n"
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;\n",
    "controller outcome imports",
)
text = once(
    text,
    "    private final ProactiveChatService proactiveChatService;\n",
    "    private final ProactiveChatService proactiveChatService;\n"
    "    private final AssistantActionOutcomeRecorder actionOutcomeRecorder;\n",
    "outcome recorder field",
)
text = once(
    text,
    "        this.proactiveChatService = new ProactiveChatService(() -> ProactiveChatSettings.from(plugin.getConfig()));\n",
    "        this.proactiveChatService = new ProactiveChatService(\n"
    "                () -> ProactiveChatSettings.from(plugin.getConfig()), plugin.getAssistantMemoryService()\n"
    "        );\n"
    "        this.actionOutcomeRecorder = new AssistantActionOutcomeRecorder(\n"
    "                plugin.getAssistantEventMemoryService(),\n"
    "                new AssistantExperienceMemoryService(plugin.getAssistantMemoryService())\n"
    "        );\n",
    "controller services",
)
old_tick = '''                () -> proactiveChatService.checkForIdleConversation(
                        new ArrayList<>(Bukkit.getOnlinePlayers()),
                        this::submitProactiveResponse
                ),
'''
new_tick = '''                () -> {
                    ArrayList<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                    proactiveChatService.checkForIdleConversation(online, this::submitProactiveResponse);
                    proactiveChatService.checkForScheduledGoals(online, this::submitProactiveResponse);
                },
'''
text = once(text, old_tick, new_tick, "scheduled goals tick")
old_action = '''                    assistantActionService.validateAndExecute(
                            source, target.npc(), prepared.message(), completedReply.actionProposals()
                    );
'''
new_action = '''                    var actionResult = assistantActionService.validateAndExecute(
                            source, target.npc(), prepared.message(), completedReply.actionProposals()
                    );
                    actionOutcomeRecorder.record(
                            source, String.valueOf(target.id()), prepared.message(), actionResult
                    );
'''
text = once(text, old_action, new_action, "action outcome recorder")
old_prompt = '''        String prompt = "Proactieve aanleiding: \\\"" + trigger.context() + "\\\"\\n"
                + trigger.instruction() + " Antwoord als " + target.name()
'''
new_prompt = '''        String prompt = "Proactief doel=" + trigger.goal().name().toLowerCase(Locale.ROOT)
                + " aanleiding: \\\"" + trigger.context() + "\\\"\\n"
                + trigger.instruction() + " Antwoord als " + target.name()
'''
text = once(text, old_prompt, new_prompt, "proactive goal prompt")
text = once(
    text,
    '                deliverResponse(contextPlayer, result, proactiveChatService.responseVisibility());\n'
    '                requestTracer.transition(requestId, AssistantRequestTracer.State.COMPLETED, "delivered");\n',
    '                deliverResponse(\n'
    '                        contextPlayer, result, trigger.privateOnly() ? "requester" : proactiveChatService.responseVisibility()\n'
    '                );\n'
    '                proactiveChatService.recordDeliveredGoal(contextPlayer, trigger);\n'
    '                requestTracer.transition(requestId, AssistantRequestTracer.State.COMPLETED, "delivered");\n',
    "private proactive delivery",
)
write(path, text)

# Config: real two-step information seeking, retention and all community goals; remove duplicated fact prompt.
path = "src/main/resources/config.yml"
text = read(path)
text = text.replace("    total_deadline_seconds: 15\n", "    total_deadline_seconds: 18\n")
text = text.replace("    max_model_calls: 3\n", "    max_model_calls: 4\n")
text = text.replace("    max_tool_rounds: 1\n", "    max_tool_rounds: 2\n")
retention_anchor = "      consolidation:\n        enabled: true\n        interval_minutes: 15\n"
if retention_anchor in text:
    text = text.replace(
        retention_anchor,
        retention_anchor + "      retention:\n        enabled: true\n        interval_minutes: 30\n",
        1,
    )
elif "      retention:\n" not in text:
    text = text.replace(
        "      storage:\n",
        "      retention:\n        enabled: true\n        interval_minutes: 30\n      storage:\n",
        1,
    )
goals_block = '''    goals:
      enabled: true
      enabled_goals: ["HELP_NEW_PLAYER", "WELCOME", "CELEBRATE", "SUPPORT_CONVERSATION", "CONNECT", "DEFUSE", "FOLLOW_UP", "INFORM"]
      probability: 0.30
      # Follow-ups are requester-only and derive only from explicit non-sensitive player goals.
      follow_up_probability: 0.12
'''
if "    goals:\n      enabled: true\n      enabled_goals:" not in text:
    text = text.replace("    collective:\n", goals_block + "    collective:\n", 1)
text = re.sub(
    r'      system_prompt: ".*?"\n',
    '      system_prompt: "Je bent AIlex: een slimme, vriendelijke en nuchtere HauntedMC-communitygenoot. Help direct, natuurlijk en zonder helpdesktoon; gebruik droge humor alleen als de speler zelf informeel is."\n',
    text,
    count=1,
)
text = re.sub(
    r'        systemPrompt: ".*?"\n',
    '        systemPrompt: "Je bent een oplettende HauntedMC Minecraft Bot: kalm, concreet en vriendelijk. Help als ervaren servergenoot met gameplay en servergebruik; behoud gangbare Engelse Minecraft-termen en forceer geen slang."\n',
    text,
    count=1,
)
text, count = re.subn(
    r"    prompt: \|-\n(?:      .*\n)+?  chat_context:",
    "    prompt: |-\n"
    "      Gebruik de gereviewde bestanden in knowledge/ als bron voor HauntedMC-specifieke feiten.\n"
    "      Geef live Paper-state voor actuele toestand voorrang en verzin nooit ontbrekende commands, prijzen,\n"
    "      rewards, warps, ranks, resetdatums of beschikbaarheid. Bij onvoldoende bewijs: verifieer of onthoud je.\n"
    "  chat_context:",
    text,
    count=1,
)
if count != 1:
    raise SystemExit("could not simplify inline knowledge prompt")
write(path, text)
