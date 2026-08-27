package nl.hauntedmc.ailex.assistant.infrastructure.memory;

/** Derived lifecycle state used for consolidation/retention decisions; it is not a new source of factual authority. */
public enum MemoryLifecycleStage {
    BUFFERED,
    CONSOLIDATED,
    MATURE,
    DECAYING
}
