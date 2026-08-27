package nl.hauntedmc.ailex.assistant.action;

/** Whitelisted low-risk embodied capabilities the model may propose but never execute directly. */
public enum AssistantActionType {
    FOLLOW_REQUESTER,
    COME_HERE,
    STOP_MOVING
}
