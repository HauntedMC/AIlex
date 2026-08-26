package nl.hauntedmc.ailex.assistant.infrastructure.memory;

/** Logical ownership boundary for one durable or episodic memory record. */
public enum MemoryScope {
    GLOBAL,
    PLAYER,
    NPC,
    PLAYER_NPC,
    WORLD,
    SESSION,
    EVENT
}
