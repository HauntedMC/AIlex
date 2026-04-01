package nl.hauntedmc.ailex.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NPCStateTest {

    @Test
    void shouldExposeExpectedStates() {
        assertEquals("IDLE", NPCState.IDLE.name());
        assertEquals("BUSY", NPCState.BUSY.name());
    }
}
