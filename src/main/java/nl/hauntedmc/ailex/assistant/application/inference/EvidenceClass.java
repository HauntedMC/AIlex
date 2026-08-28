package nl.hauntedmc.ailex.assistant.application.inference;

/** Deterministic provenance family for one model-citable evidence identifier. */
public enum EvidenceClass {
    LIVE_RUNTIME,
    REVIEWED_KNOWLEDGE,
    AUTHORITATIVE_ABSENCE,
    AUTHORITATIVE_MEMORY_ABSENCE,
    PLAYER_MEMORY,
    SHARED_MEMORY,
    EVENT_MEMORY,
    NEGATIVE_OBSERVATION,
    UNKNOWN
}
