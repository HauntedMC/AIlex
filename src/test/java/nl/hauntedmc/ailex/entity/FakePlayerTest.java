package nl.hauntedmc.ailex.entity;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;

import org.bukkit.entity.Entity;
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
    void removeShouldDeregisterAndPersistWhenNpcExistsInRegistry() {
        NPCRegistry registry = mock(NPCRegistry.class);
        NPC citizensNpc = mock(NPC.class);
        SkinTrait skinTrait = mock(SkinTrait.class);
        MetadataStore metadataStore = mock(MetadataStore.class);
        Entity bukkitEntity = mock(Entity.class);

        when(registry.createNPC(EntityType.PLAYER, "AIlex")).thenReturn(citizensNpc);
        when(citizensNpc.getOrAddTrait(SkinTrait.class)).thenReturn(skinTrait);
        when(citizensNpc.data()).thenReturn(metadataStore);
        when(citizensNpc.getId()).thenReturn(17);
        when(registry.getById(17)).thenReturn(citizensNpc);
        when(citizensNpc.getEntity()).thenReturn(bukkitEntity);
        when(bukkitEntity.isValid()).thenReturn(true);

        try (MockedStatic<CitizensAPI> mockedCitizensApi = org.mockito.Mockito.mockStatic(CitizensAPI.class)) {
            mockedCitizensApi.when(CitizensAPI::getNPCRegistry).thenReturn(registry);

            FakePlayer fakePlayer = new FakePlayer("AIlex");
            fakePlayer.remove();

            verify(metadataStore).setPersistent("should-save", false);
            verify(metadataStore).setPersistent("ailex.managed", true);
            verify(citizensNpc, never()).despawn(DespawnReason.PLUGIN);
            verify(citizensNpc, never()).destroy();
            verify(registry).deregister(citizensNpc);
            verify(bukkitEntity).remove();
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
        when(registry.getById(17)).thenReturn(citizensNpc);

        try (MockedStatic<CitizensAPI> mockedCitizensApi = org.mockito.Mockito.mockStatic(CitizensAPI.class)) {
            mockedCitizensApi.when(CitizensAPI::getNPCRegistry).thenReturn(registry);

            FakePlayer fakePlayer = new FakePlayer("AIlex");
            fakePlayer.remove();
            fakePlayer.remove();

            verify(registry, times(1)).deregister(citizensNpc);
            verify(registry, times(1)).saveToStore();
        }
    }

    @Test
    void removeShouldFallbackToDirectDespawnWhenNpcMissingFromRegistry() {
        NPCRegistry registry = mock(NPCRegistry.class);
        NPC citizensNpc = mock(NPC.class);
        SkinTrait skinTrait = mock(SkinTrait.class);
        MetadataStore metadataStore = mock(MetadataStore.class);

        when(registry.createNPC(EntityType.PLAYER, "AIlex")).thenReturn(citizensNpc);
        when(citizensNpc.getOrAddTrait(SkinTrait.class)).thenReturn(skinTrait);
        when(citizensNpc.data()).thenReturn(metadataStore);
        when(citizensNpc.getId()).thenReturn(17);
        when(registry.getById(17)).thenReturn(null);
        when(citizensNpc.isSpawned()).thenReturn(true);

        try (MockedStatic<CitizensAPI> mockedCitizensApi = org.mockito.Mockito.mockStatic(CitizensAPI.class)) {
            mockedCitizensApi.when(CitizensAPI::getNPCRegistry).thenReturn(registry);

            FakePlayer fakePlayer = new FakePlayer("AIlex");
            fakePlayer.remove();

            verify(citizensNpc).despawn(DespawnReason.REMOVAL);
            verify(registry, never()).deregister(citizensNpc);
            verify(registry).saveToStore();
        }
    }
}
