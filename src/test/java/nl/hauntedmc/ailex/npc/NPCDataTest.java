package nl.hauntedmc.ailex.npc;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NPCDataTest {

    @Test
    void shouldExposeMutableFields() {
        NPCData data = new NPCData(1, "Alpha", new Location(null, 1, 2, 3), "npc.class");

        data.setName("Beta");
        data.setSpawnLocation(new Location(null, 4, 5, 6));

        assertEquals(1, data.getId());
        assertEquals("Beta", data.getName());
        assertEquals(4, data.getSpawnLocation().getX(), 0.0001);
        assertEquals("npc.class", data.getNpcClass());
    }

    @Test
    void isValidShouldRejectInvalidStates() {
        assertTrue(new NPCData(1, "A", new Location(null, 0, 0, 0), "class").isValid());
        assertFalse(new NPCData(-1, "A", new Location(null, 0, 0, 0), "class").isValid());
        assertFalse(new NPCData(1, "", new Location(null, 0, 0, 0), "class").isValid());
        assertFalse(new NPCData(1, "A", null, "class").isValid());
        assertFalse(new NPCData(1, "A", new Location(null, 0, 0, 0), "").isValid());
    }
}
