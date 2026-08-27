package nl.hauntedmc.ailex.assistant.infrastructure.memory;

/** Evidence-grounded procedural experience categories used for strategy learning and evaluation. */
public enum ExperienceType {
    FAILED_ANSWER,
    CORRECTION,
    SUCCESSFUL_TOOL_PATH,
    RETRIEVAL_FAILURE,
    AMBIGUOUS_INTENT,
    UNHELPFUL_INTERRUPTION,
    USER_FEEDBACK,
    HANDOFF_SUCCESS
}
