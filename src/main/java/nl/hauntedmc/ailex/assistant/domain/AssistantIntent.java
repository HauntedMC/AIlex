package nl.hauntedmc.ailex.assistant.domain;

/** A deliberately small, auditable set of player-facing request types. */
public enum AssistantIntent {
    CONVERSATION,
    CONTEXT_FOLLOWUP,
    MEMORY_RECALL,
    EVENT_RECALL,
    SERVER_FACT,
    KNOWLEDGE_DISCOVERY,
    LIVE_STATE,
    GAMEPLAY_HELP,
    SUPPORT,
    SAFETY
}
