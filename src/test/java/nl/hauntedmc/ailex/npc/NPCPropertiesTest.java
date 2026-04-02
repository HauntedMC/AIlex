package nl.hauntedmc.ailex.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NPCPropertiesTest {

    @Test
    void defaultsShouldMatchPublicConstants() {
        NPCProperties defaults = NPCProperties.defaultValues();
        assertEquals(NPCProperties.DEFAULT_PREFIX, defaults.getPrefix());
        assertEquals(NPCProperties.DEFAULT_TAB_PREFIX, defaults.getTabPrefix());
        assertEquals(NPCProperties.DEFAULT_TAB_LIST_ORDER, defaults.getTabListOrder());
        assertEquals(NPCProperties.DEFAULT_DAMAGEABLE, defaults.isDamageable());
        assertEquals(NPCProperties.DEFAULT_RESPAWN_ON_DEATH, defaults.isRespawnOnDeath());
        assertEquals(NPCProperties.DEFAULT_CHAT_ENABLED, defaults.isChatEnabled());
        assertEquals(NPCProperties.DEFAULT_LISTED_IN_TAB, defaults.isListedInTab());
        assertEquals(NPCProperties.DEFAULT_ALWAYS_USE_NAME_HOLOGRAM, defaults.isAlwaysUseNameHologram());
    }

    @Test
    void copyShouldReturnIndependentInstance() {
        NPCProperties original = new NPCProperties();
        NPCProperties copy = original.copy();

        copy.setPrefix("<gray>[Changed]");
        copy.setTabListOrder(-123);

        assertNotSame(original, copy);
        assertTrue(original.isValid());
        assertEquals(NPCProperties.DEFAULT_PREFIX, original.getPrefix());
        assertEquals(NPCProperties.DEFAULT_TAB_LIST_ORDER, original.getTabListOrder());
    }
}
