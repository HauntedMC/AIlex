package nl.hauntedmc.ailex.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPCRegistry;

import nl.hauntedmc.ailex.config.DataHandler;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void loadShouldCleanupManagedAndLegacyCitizensNpcsBeforeSpawning() {
        NPCHandler handler = new NPCHandler();
        NPCRegistry citizensRegistry = mock(NPCRegistry.class);
        net.citizensnpcs.api.npc.NPC managedNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        net.citizensnpcs.api.npc.NPC legacyNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        net.citizensnpcs.api.npc.NPC unmanagedNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        MetadataStore managedMetadata = mock(MetadataStore.class);
        MetadataStore legacyMetadata = mock(MetadataStore.class);
        MetadataStore unmanagedMetadata = mock(MetadataStore.class);
        NPCProperties properties = new NPCProperties("[AI]", "", 0, true, true, true, true, false);
        NPCData npcData = new NPCData(42, "Alex", new Location(null, 0, 0, 0), "class", properties);
        String shouldSaveKey = "should-save";

        when(citizensRegistry.iterator()).thenReturn(List.of(managedNpc, legacyNpc, unmanagedNpc).iterator());
        when(managedNpc.data()).thenReturn(managedMetadata);
        when(legacyNpc.data()).thenReturn(legacyMetadata);
        when(unmanagedNpc.data()).thenReturn(unmanagedMetadata);
        when(legacyNpc.getName()).thenReturn("[AI] Alex");

        when(managedMetadata.get("ailex.managed", false)).thenReturn(true);
        when(managedMetadata.get("ailex.internal-id", Integer.MIN_VALUE)).thenReturn(Integer.MIN_VALUE);
        when(managedMetadata.get(shouldSaveKey, true)).thenReturn(true);

        when(legacyMetadata.get("ailex.managed", false)).thenReturn(false);
        when(legacyMetadata.get("ailex.internal-id", Integer.MIN_VALUE)).thenReturn(Integer.MIN_VALUE);
        when(legacyMetadata.get(shouldSaveKey, true)).thenReturn(false);

        when(unmanagedMetadata.get("ailex.managed", false)).thenReturn(false);
        when(unmanagedMetadata.get("ailex.internal-id", Integer.MIN_VALUE)).thenReturn(Integer.MIN_VALUE);
        when(unmanagedMetadata.get(shouldSaveKey, true)).thenReturn(true);

        try (MockedStatic<DataHandler> mockedDataHandler = org.mockito.Mockito.mockStatic(DataHandler.class);
             MockedStatic<CitizensAPI> mockedCitizens = org.mockito.Mockito.mockStatic(CitizensAPI.class)) {
            mockedDataHandler.when(DataHandler::loadNPCs).thenReturn(Map.of(42, npcData));
            mockedCitizens.when(CitizensAPI::getNPCRegistry).thenReturn(citizensRegistry);

            handler.loadNPCs();

            verify(citizensRegistry).deregister(managedNpc);
            verify(citizensRegistry).deregister(legacyNpc);
            verify(citizensRegistry, never()).deregister(unmanagedNpc);
            verify(citizensRegistry).saveToStore();
        }
    }

}
