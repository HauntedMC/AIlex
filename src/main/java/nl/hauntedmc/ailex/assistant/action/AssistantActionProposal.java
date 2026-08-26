package nl.hauntedmc.ailex.assistant.action;

/** A non-authoritative model proposal. Deterministic validation must approve it before any Bukkit action is queued. */
public record AssistantActionProposal(AssistantActionType type, String reason) {
    public AssistantActionProposal {
        type = type == null ? AssistantActionType.STOP_MOVING : type;
        reason = reason == null ? "" : reason.replaceAll("\\s+", " ").trim();
    }
}
