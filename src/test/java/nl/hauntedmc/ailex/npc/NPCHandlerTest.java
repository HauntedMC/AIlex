package nl.hauntedmc.ailex.npc;

import nl.hauntedmc.ailex.config.DataHandler;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NPCHandlerTest {

    @Test
    void shouldThrowWhenSavingOrRemovingMissingNpc() {
        NPCHandler handler = new NPCHandler();

        assertThrows(IllegalArgumentException.class, () -> handler.saveNPC(1));
        assertThrows(IllegalArgumentException.class, () -> handler.removeNPC(1));
    }

    @Test
    void clearRegistryShouldRemoveAllEntries() {
        NPCHandler handler = new NPCHandler();
        handler.getNPCRegistry().put(1, mock(NPC.class));

        handler.clearNPCRegistry();

        assertEquals(0, handler.getNPCRegistry().size());
    }

    @Test
    void unloadAllShouldSaveAndRemoveEachNpc() {
        NPCHandler handler = new NPCHandler();
        NPC npc = mock(NPC.class);
        when(npc.getNPCData()).thenReturn(new NPCData(1, "npc", new Location(null, 0, 0, 0), "class"));
        handler.getNPCRegistry().put(1, npc);

        try (MockedStatic<DataHandler> mockedDataHandler = org.mockito.Mockito.mockStatic(DataHandler.class)) {
            assertDoesNotThrow(handler::unloadAllNPCs);
            verify(npc).remove();
            mockedDataHandler.verify(() -> DataHandler.saveNPC(npc.getNPCData()));
        }
    }
}
