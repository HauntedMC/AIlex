package nl.hauntedmc.ailex.npc;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NPCDataTest {

    @Test
    void shouldExposeMutableFields() {
        NPCProperties properties = new NPCProperties(
                "<gray>[Bot]",
                "<green>◆",
                -1234,
                false,
                false,
                false,
                false,
                true
        );
        NPCData data = new NPCData(1, "Alpha", new Location(null, 1, 2, 3), "npc.class", properties);

        data.setName("Beta");
        data.setSpawnLocation(new Location(null, 4, 5, 6));
        data.getProperties().setTabListOrder(-777);

        assertEquals(1, data.getId());
        assertEquals("Beta", data.getName());
        assertEquals(4, data.getSpawnLocation().getX(), 0.0001);
        assertEquals("npc.class", data.getNpcClass());
        assertEquals("<gray>[Bot]", data.getProperties().getPrefix());
        assertEquals(-777, data.getProperties().getTabListOrder());
        assertTrue(data.getProperties().isAlwaysUseNameHologram());
    }

    @Test
    void isValidShouldRejectInvalidStates() {
        assertTrue(new NPCData(1, "A", new Location(null, 0, 0, 0), "class").isValid());
        assertFalse(new NPCData(-1, "A", new Location(null, 0, 0, 0), "class").isValid());
        assertFalse(new NPCData(1, "", new Location(null, 0, 0, 0), "class").isValid());
        assertFalse(new NPCData(1, "A", null, "class").isValid());
        assertFalse(new NPCData(1, "A", new Location(null, 0, 0, 0), "").isValid());
    }

    @Test
    void defaultConstructorShouldAssignDefaultProperties() {
        NPCData data = new NPCData(10, "Unit", new Location(null, 0, 1, 2), "class");
        assertEquals(NPCProperties.DEFAULT_PREFIX, data.getProperties().getPrefix());
        assertEquals(NPCProperties.DEFAULT_TAB_LIST_ORDER, data.getProperties().getTabListOrder());
    }
}
