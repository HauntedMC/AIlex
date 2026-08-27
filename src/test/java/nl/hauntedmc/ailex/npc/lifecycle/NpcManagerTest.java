package nl.hauntedmc.ailex.npc.lifecycle;

import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCData;
import nl.hauntedmc.ailex.npc.NPCProperties;
import nl.hauntedmc.ailex.npc.impl.AilexNPC;

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

class NpcManagerTest {

    @Test
    void shouldThrowWhenSavingOrRemovingMissingNpc() {
        NpcManager handler = new NpcManager();

        assertThrows(IllegalArgumentException.class, () -> handler.saveNPC(1));
        assertThrows(IllegalArgumentException.class, () -> handler.removeNPC(1));
    }

    @Test
    void invalidNpcDataMustNeverPoisonTheRuntimeRegistry() {
        NpcManager handler = new NpcManager();
        NPCData invalid = new NPCData(7, "Haunty", null, AilexNPC.class.getName(), NPCProperties.defaultValues());

        assertThrows(IllegalArgumentException.class, () -> handler.createNPC(AilexNPC.class, invalid));
        assertEquals(0, handler.getNPCRegistry().size());
    }

    @Test
    void clearRegistryShouldRemoveAllEntries() {
        NpcManager handler = new NpcManager();
        handler.getNPCRegistry().put(1, mock(NPC.class));

        handler.clearNPCRegistry();

        assertEquals(0, handler.getNPCRegistry().size());
    }

    @Test
    void unloadAllShouldSaveAndRemoveEachNpc() {
        NpcManager handler = new NpcManager();
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
    void loadShouldCleanupOnlyManagedCitizensNpcsBeforeSpawning() {
        NpcManager handler = new NpcManager();
        NPCRegistry citizensRegistry = mock(NPCRegistry.class);
        net.citizensnpcs.api.npc.NPC managedNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        net.citizensnpcs.api.npc.NPC externalNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        net.citizensnpcs.api.npc.NPC unmanagedNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        MetadataStore managedMetadata = mock(MetadataStore.class);
        MetadataStore externalMetadata = mock(MetadataStore.class);
        MetadataStore unmanagedMetadata = mock(MetadataStore.class);
        NPCData npcData = new NPCData(42, "Alex", new Location(null, 0, 0, 0), "class");

        when(citizensRegistry.iterator()).thenReturn(List.of(managedNpc, externalNpc, unmanagedNpc).iterator());
        when(managedNpc.data()).thenReturn(managedMetadata);
        when(externalNpc.data()).thenReturn(externalMetadata);
        when(unmanagedNpc.data()).thenReturn(unmanagedMetadata);

        when(managedMetadata.get("ailex.managed", false)).thenReturn(true);
        when(managedMetadata.get("ailex.internal-id", Integer.MIN_VALUE)).thenReturn(Integer.MIN_VALUE);

        when(externalMetadata.get("ailex.managed", false)).thenReturn(false);
        when(externalMetadata.get("ailex.internal-id", Integer.MIN_VALUE)).thenReturn(Integer.MIN_VALUE);

        when(unmanagedMetadata.get("ailex.managed", false)).thenReturn(false);
        when(unmanagedMetadata.get("ailex.internal-id", Integer.MIN_VALUE)).thenReturn(Integer.MIN_VALUE);

        try (MockedStatic<DataHandler> mockedDataHandler = org.mockito.Mockito.mockStatic(DataHandler.class);
             MockedStatic<CitizensAPI> mockedCitizens = org.mockito.Mockito.mockStatic(CitizensAPI.class)) {
            mockedDataHandler.when(DataHandler::loadNPCs).thenReturn(Map.of(42, npcData));
            mockedCitizens.when(CitizensAPI::getNPCRegistry).thenReturn(citizensRegistry);

            handler.loadNPCs();

            verify(citizensRegistry).deregister(managedNpc);
            verify(citizensRegistry, never()).deregister(externalNpc);
            verify(citizensRegistry, never()).deregister(unmanagedNpc);
            verify(citizensRegistry).saveToStore();
        }
    }

}
