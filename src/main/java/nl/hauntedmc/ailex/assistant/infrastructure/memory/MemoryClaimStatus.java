package nl.hauntedmc.ailex.assistant.infrastructure.memory;

/** Epistemic state of a claim after deterministic temporal/source resolution. */
public enum MemoryClaimStatus {
    ACTIVE,
    DISPUTED,
    SUPERSEDED,
    RETRACTED
}
