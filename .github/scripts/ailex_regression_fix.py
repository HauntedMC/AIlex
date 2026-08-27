from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"missing patch anchor in {path}: {old[:80]!r}")
    file.write_text(text.replace(old, new, 1))


# Retrieval: freshness may rank relevant evidence, but it must never create relevance by itself.
replace_once(
    "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/knowledge/LocalKnowledgeIndex.java",
    """            double semantic = semanticScores.getOrDefault(chunk.id(), 0.0D);\n            double rrf = reciprocalRank(lexicalRanks.get(chunk.id())) + reciprocalRank(semanticRanks.get(chunk.id()));\n            double combined = lexical + semantic * SEMANTIC_WEIGHT + rrf * RRF_WEIGHT\n                    + freshnessWeight(chunk) * 0.30D;\n            if (combined > 0.0D && eligible(chunk, settings)) {\n""",
    """            double semantic = semanticScores.getOrDefault(chunk.id(), 0.0D);\n            if (lexical <= 0.0D && semantic <= 0.0D) {\n                continue;\n            }\n            double rrf = reciprocalRank(lexicalRanks.get(chunk.id())) + reciprocalRank(semanticRanks.get(chunk.id()));\n            double combined = lexical + semantic * SEMANTIC_WEIGHT + rrf * RRF_WEIGHT\n                    + freshnessWeight(chunk) * 0.30D;\n            if (combined > 0.0D && eligible(chunk, settings)) {\n""",
)

# FAST must keep the minimum deterministic snapshot; only grounded/deliberate agent work freezes a larger safe ceiling.
replace_once(
    "src/main/java/nl/hauntedmc/ailex/assistant/application/context/AssistantLiveCapturePolicy.java",
    """        if (!agentEnabled || mode == AssistantMode.HANDOFF || settings == null) {\n""",
    """        if (!agentEnabled || mode == AssistantMode.FAST || mode == AssistantMode.HANDOFF || settings == null) {\n""",
)

# Make the vanilla-vs-server grounding boundary explicit in the stable prompt.
replace_once(
    "src/main/java/nl/hauntedmc/ailex/assistant/application/prompt/AssistantPromptComposer.java",
    """            - Use only evidence actually supplied to this turn for custom, current or remembered facts. Never invent evidence IDs.\n""",
    """            - Stable vanilla Minecraft knowledge may use model knowledge. Custom HauntedMC, current-state and remembered\n              claims require evidence actually supplied to this turn. Never invent evidence IDs.\n""",
)

# Relationship counters are stable-key state, not a broad semantic relationship query.
replace_once(
    "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/memory/AssistantEventMemoryService.java",
    '                playerId, npcId, "interaction relationship", Set.of(MemoryKind.RELATIONSHIP), 16\n',
    '                playerId, npcId, "interaction count", Set.of(MemoryKind.RELATIONSHIP), 16\n',
)

# Consolidation topic selection must be deterministic and prefer a specific topic over generic bookkeeping tags.
replace_once(
    "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/memory/AssistantMemoryConsolidator.java",
    """    private String primaryTopic(MemoryRecord record) {\n        for (String tag : record.tags()) {\n            if (!Set.of(\"event\", \"session\", \"join\", \"quit\", \"world\").contains(tag)\n                    && !tag.startsWith(\"world:\")) {\n                return safeTopic(tag);\n            }\n        }\n        String key = record.key();\n        int separator = key.indexOf('.');\n        return safeTopic(separator > 0 ? key.substring(0, separator) : key);\n    }\n""",
    """    private String primaryTopic(MemoryRecord record) {\n        String specificTag = record.tags().stream()\n                .filter(tag -> !Set.of(\"event\", \"session\", \"join\", \"quit\", \"world\", \"project\").contains(tag))\n                .filter(tag -> !tag.startsWith(\"world:\"))\n                .map(this::safeTopic)\n                .filter(tag -> !tag.isBlank())\n                .sorted()\n                .findFirst()\n                .orElse(\"\");\n        if (!specificTag.isBlank()) {\n            return specificTag;\n        }\n        String key = record.key();\n        int separator = key.indexOf('.');\n        return safeTopic(separator > 0 ? key.substring(0, separator) : key);\n    }\n""",
)

# Action construction is injected behind a package-private seam so policy tests do not need the legacy global ConfigHandler.
path = "src/main/java/nl/hauntedmc/ailex/assistant/action/AssistantActionService.java"
replace_once(
    path,
    """import nl.hauntedmc.ailex.ai.action.ActionContext;\n""",
    """import nl.hauntedmc.ailex.ai.action.ActionContext;\nimport nl.hauntedmc.ailex.ai.action.Actionable;\n""",
)
replace_once(
    path,
    """import java.util.Set;\n""",
    """import java.util.Set;\nimport java.util.function.Function;\n""",
)
replace_once(
    path,
    """    private final JavaPlugin plugin;\n\n    public AssistantActionService(JavaPlugin plugin) {\n        this.plugin = plugin;\n    }\n""",
    """    private final JavaPlugin plugin;\n    private final Function<ActionContext, Actionable> followActionFactory;\n    private final Function<ActionContext, Actionable> moveHereActionFactory;\n\n    public AssistantActionService(JavaPlugin plugin) {\n        this(plugin, FollowPlayerAction::new, MoveHereAction::new);\n    }\n\n    /** Test seam that keeps action-policy tests independent from the legacy global movement configuration. */\n    AssistantActionService(\n            JavaPlugin plugin,\n            Function<ActionContext, Actionable> followActionFactory,\n            Function<ActionContext, Actionable> moveHereActionFactory\n    ) {\n        this.plugin = plugin;\n        this.followActionFactory = followActionFactory;\n        this.moveHereActionFactory = moveHereActionFactory;\n    }\n""",
)
replace_once(
    path,
    """                npc.queueAction(new FollowPlayerAction(context));\n                return true;\n""",
    """                Actionable action = followActionFactory.apply(context);\n                if (action == null) {\n                    return false;\n                }\n                npc.queueAction(action);\n                return true;\n""",
)
replace_once(
    path,
    """                npc.queueAction(new MoveHereAction(context));\n                return true;\n""",
    """                Actionable action = moveHereActionFactory.apply(context);\n                if (action == null) {\n                    return false;\n                }\n                npc.queueAction(action);\n                return true;\n""",
)

# Update action test to exercise the deterministic boundary without instantiating the legacy configured movement class.
path = "src/test/java/nl/hauntedmc/ailex/assistant/action/AssistantActionServiceTest.java"
replace_once(
    path,
    """import nl.hauntedmc.ailex.ai.action.move.FollowPlayerAction;\n""",
    """import nl.hauntedmc.ailex.ai.action.Actionable;\n""",
)
replace_once(
    path,
    """        JavaPlugin plugin = plugin();\n        AssistantActionService service = new AssistantActionService(plugin);\n        Player requester = mock(Player.class);\n""",
    """        JavaPlugin plugin = plugin();\n        Actionable followAction = mock(Actionable.class);\n        AssistantActionService service = new AssistantActionService(\n                plugin, ignored -> followAction, ignored -> mock(Actionable.class)\n        );\n        Player requester = mock(Player.class);\n""",
)
replace_once(
    path,
    """        verify(npc).queueAction(any(FollowPlayerAction.class));\n""",
    """        verify(npc).queueAction(followAction);\n""",
)

# Prompt tests assert the semantic contract, not obsolete wording/capitalization.
path = "src/test/java/nl/hauntedmc/ailex/assistant/AssistantServiceTest.java"
replace_once(
    path,
    """        assertTrue(systemPrompt.getValue().contains(\"Use general Minecraft knowledge when appropriate\"));\n        assertTrue(systemPrompt.getValue().contains(\"Never invent custom or time-sensitive HauntedMC facts\"));\n""",
    """        String policyPrompt = systemPrompt.getValue().toLowerCase(java.util.Locale.ROOT);\n        assertTrue(policyPrompt.contains(\"stable vanilla minecraft knowledge\"));\n        assertTrue(policyPrompt.contains(\"custom hauntedmc\"));\n""",
)

replace_once(
    "src/test/java/nl/hauntedmc/ailex/assistant/application/prompt/AssistantPromptComposerTest.java",
    """        assertTrue(prompt.contains(\"Procedural experience\"));\n""",
    """        assertTrue(prompt.contains(\"procedural experience\"));\n""",
)
