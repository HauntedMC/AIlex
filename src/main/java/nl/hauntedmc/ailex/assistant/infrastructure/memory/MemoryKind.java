package nl.hauntedmc.ailex.assistant.infrastructure.memory;

/** Semantic class of a memory record; scope and kind are intentionally orthogonal. */
public enum MemoryKind {
    PREFERENCE,
    FACT,
    OPINION,
    INTEREST,
    GOAL,
    RELATIONSHIP,
    EPISODE,
    EVENT
}
