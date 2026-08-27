from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


root = Path(__file__).resolve().parents[2]

# Put explicit player-memory ingestion at the central assistant request boundary, so direct messages, standalone mode and
# accepted implicit follow-ups all receive the same deterministic memory behavior.
path = root / "src/main/java/nl/hauntedmc/ailex/assistant/application/AssistantService.java"
text = path.read_text()
text = replace_once(
    text,
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;\n",
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;\n"
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.ExplicitPlayerMemoryService;\n",
    "AssistantService explicit-memory import",
)
text = replace_once(
    text,
    "    private final AssistantRelationshipMemoryService relationshipMemory;\n",
    "    private final AssistantRelationshipMemoryService relationshipMemory;\n"
    "    private final ExplicitPlayerMemoryService explicitPlayerMemory;\n",
    "AssistantService explicit-memory field",
)
text = replace_once(
    text,
    "        this.relationshipMemory = new AssistantRelationshipMemoryService(memoryService);\n",
    "        this.relationshipMemory = new AssistantRelationshipMemoryService(memoryService);\n"
    "        this.explicitPlayerMemory = new ExplicitPlayerMemoryService(memoryService);\n",
    "AssistantService explicit-memory constructor",
)
text = replace_once(
    text,
    """        UUID playerId = player.getUniqueId();
        if (memoryService != null && settings.toolAllowed("session")) {
            memoryService.observe(playerId, message);
""",
    """        UUID playerId = player.getUniqueId();
        if (memoryService != null && settings.toolAllowed("session")) {
            ExplicitPlayerMemoryService.Result explicitResult = explicitPlayerMemory.observe(
                    playerId, player.getName(), message
            );
            if (settings.diagnosticLogging() && explicitResult.proposed() > 0) {
                LoggerUtils.logInfo("[AIlex memory] explicit requester=" + sanitizeLogField(player.getName())
                        + " operation=" + (explicitResult.forget() ? "forget" : "upsert")
                        + " proposed=" + explicitResult.proposed()
                        + " accepted=" + explicitResult.accepted());
            }
            memoryService.observe(playerId, message);
""",
    "AssistantService explicit-memory prepare",
)
path.write_text(text)

# Treat a tiny set of unmistakable reactions as active-dialogue continuations even when the prior assistant line was not a
# question. This fixes the production 'zucht' dead-air case without making ordinary arbitrary server chat implicit follow-up.
path = root / "src/main/java/nl/hauntedmc/ailex/assistant/runtime/AssistantConversationManager.java"
text = path.read_text()
text = replace_once(
    text,
    """        if (normalized.length() > 320) {
            return false;
        }
        if (startsLikeSubstantiveFollowUp(normalized)
""",
    """        if (normalized.length() > 320) {
            return false;
        }
        if (isSocialReactionFollowUp(normalized)) {
            return true;
        }
        if (startsLikeSubstantiveFollowUp(normalized)
""",
    "ConversationManager social-reaction gate",
)
text = replace_once(
    text,
    """    private boolean previousAssistantAskedQuestion(Snapshot snapshot) {
""",
    """    private boolean isSocialReactionFollowUp(String text) {
        String stripped = text.replaceAll("[?!.,]+$", "").trim();
        return SetLike.SOCIAL_REACTIONS.contains(stripped);
    }

    private boolean previousAssistantAskedQuestion(Snapshot snapshot) {
""",
    "ConversationManager social-reaction method",
)
text = replace_once(
    text,
    """        private static final Set<String> TERSE = Set.of(
                "ja", "nee", "ok", "oke", "oké", "waarom", "hoezo", "wacht", "huh", "yes", "no", "okay",
                "why", "wait", "hmm"
        );
""",
    """        private static final Set<String> TERSE = Set.of(
                "ja", "nee", "ok", "oke", "oké", "waarom", "hoezo", "wacht", "huh", "yes", "no", "okay",
                "why", "wait", "hmm"
        );
        private static final Set<String> SOCIAL_REACTIONS = Set.of(
                "zucht", "pff", "pfff", "ugh", "sigh"
        );
""",
    "ConversationManager social-reaction vocabulary",
)
path.write_text(text)
