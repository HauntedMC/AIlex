package nl.hauntedmc.ailex.entity;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FakePlayerTest {

    @Test
    void removeShouldDestroyAndDeregisterWhenNpcIsNotSpawned() {
        NPCRegistry registry = mock(NPCRegistry.class);
        NPC citizensNpc = mock(NPC.class);
        SkinTrait skinTrait = mock(SkinTrait.class);
        MetadataStore metadataStore = mock(MetadataStore.class);

        when(registry.createNPC(EntityType.PLAYER, "AIlex")).thenReturn(citizensNpc);
        when(citizensNpc.getOrAddTrait(SkinTrait.class)).thenReturn(skinTrait);
        when(citizensNpc.data()).thenReturn(metadataStore);
        when(citizensNpc.getId()).thenReturn(17);
        when(citizensNpc.isSpawned()).thenReturn(false);
        when(registry.getById(17)).thenReturn(citizensNpc);

        try (MockedStatic<CitizensAPI> mockedCitizensApi = org.mockito.Mockito.mockStatic(CitizensAPI.class)) {
            mockedCitizensApi.when(CitizensAPI::getNPCRegistry).thenReturn(registry);

            FakePlayer fakePlayer = new FakePlayer("AIlex");
            fakePlayer.remove();

            verify(metadataStore).setPersistent("should-save", false);
            verify(metadataStore).setPersistent("ailex.managed", true);
            verify(citizensNpc, never()).despawn(DespawnReason.PLUGIN);
            verify(citizensNpc).destroy();
            verify(registry).deregister(citizensNpc);
            verify(registry).saveToStore();
        }
    }

    @Test
    void removeShouldBeIdempotent() {
        NPCRegistry registry = mock(NPCRegistry.class);
        NPC citizensNpc = mock(NPC.class);
        SkinTrait skinTrait = mock(SkinTrait.class);
        MetadataStore metadataStore = mock(MetadataStore.class);

        when(registry.createNPC(EntityType.PLAYER, "AIlex")).thenReturn(citizensNpc);
        when(citizensNpc.getOrAddTrait(SkinTrait.class)).thenReturn(skinTrait);
        when(citizensNpc.data()).thenReturn(metadataStore);
        when(citizensNpc.getId()).thenReturn(17);
        when(citizensNpc.isSpawned()).thenReturn(true);
        when(registry.getById(17)).thenReturn(citizensNpc);

        try (MockedStatic<CitizensAPI> mockedCitizensApi = org.mockito.Mockito.mockStatic(CitizensAPI.class)) {
            mockedCitizensApi.when(CitizensAPI::getNPCRegistry).thenReturn(registry);

            FakePlayer fakePlayer = new FakePlayer("AIlex");
            fakePlayer.remove();
            fakePlayer.remove();

            verify(citizensNpc, times(1)).despawn(DespawnReason.PLUGIN);
            verify(citizensNpc, times(1)).destroy();
            verify(registry, times(1)).deregister(citizensNpc);
            verify(registry, times(1)).saveToStore();
        }
    }
}
