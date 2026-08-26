package nl.hauntedmc.ailex.listener.citizens;

import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.event.SpawnReason;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.lifecycle.NpcManager;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CitizensListenersTest {

    @Test
    void spawnListenerShouldPostInitializeMatchingNpc() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager manager = mock(NpcManager.class);
        NPC npc = mock(NPC.class);
        UUID npcUuid = UUID.randomUUID();

        HashMap<Integer, NPC> registry = new HashMap<>();
        registry.put(1, npc);

        net.citizensnpcs.api.npc.NPC citizensNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        NPCSpawnEvent event = mock(NPCSpawnEvent.class);

        when(plugin.getNpcManager()).thenReturn(manager);
        when(manager.ownsCitizensNpc(npcUuid)).thenReturn(true);
        when(manager.getNPCRegistry()).thenReturn(registry);
        when(event.getReason()).thenReturn(SpawnReason.PLUGIN);
        when(event.getNPC()).thenReturn(citizensNpc);
        when(citizensNpc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getCitizensEntityID()).thenReturn(npcUuid);

        new NPCSpawnListener(plugin).onEntitySpawn(event);
        verify(npc).postInitializeNPC();
    }

    @Test
    void spawnListenerShouldIgnoreNpcNotOwnedByAIlex() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager manager = mock(NpcManager.class);
        NPCSpawnEvent event = mock(NPCSpawnEvent.class);
        net.citizensnpcs.api.npc.NPC citizensNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        UUID externalNpcId = UUID.randomUUID();

        when(plugin.getNpcManager()).thenReturn(manager);
        when(event.getReason()).thenReturn(SpawnReason.PLUGIN);
        when(event.getNPC()).thenReturn(citizensNpc);
        when(citizensNpc.getUniqueId()).thenReturn(externalNpcId);
        when(manager.ownsCitizensNpc(externalNpcId)).thenReturn(false);

        new NPCSpawnListener(plugin).onEntitySpawn(event);

        verify(manager, never()).getNPCRegistry();
    }

    @Test
    void deathListenerShouldResetMatchingNpcState() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager manager = mock(NpcManager.class);
        NPC npc = mock(NPC.class);
        UUID npcUuid = UUID.randomUUID();

        HashMap<Integer, NPC> registry = new HashMap<>();
        registry.put(1, npc);

        net.citizensnpcs.api.npc.NPC citizensNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        NPCDeathEvent event = mock(NPCDeathEvent.class);

        when(plugin.getNpcManager()).thenReturn(manager);
        when(manager.ownsCitizensNpc(npcUuid)).thenReturn(true);
        when(manager.getNPCRegistry()).thenReturn(registry);
        when(event.getNPC()).thenReturn(citizensNpc);
        when(citizensNpc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getCitizensEntityID()).thenReturn(npcUuid);
        when(npc.isRespawnOnDeath()).thenReturn(true);

        new NPCDeathListener(plugin).onEntityDeath(event);
        verify(npc).clearActionQueue();
        verify(npc).cancelCurrentAction();
        verify(npc).respawn();
    }

    @Test
    void deathListenerShouldSkipRespawnWhenDisabled() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager manager = mock(NpcManager.class);
        NPC npc = mock(NPC.class);
        UUID npcUuid = UUID.randomUUID();

        HashMap<Integer, NPC> registry = new HashMap<>();
        registry.put(1, npc);

        net.citizensnpcs.api.npc.NPC citizensNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        NPCDeathEvent event = mock(NPCDeathEvent.class);

        when(plugin.getNpcManager()).thenReturn(manager);
        when(manager.ownsCitizensNpc(npcUuid)).thenReturn(true);
        when(manager.getNPCRegistry()).thenReturn(registry);
        when(event.getNPC()).thenReturn(citizensNpc);
        when(citizensNpc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getCitizensEntityID()).thenReturn(npcUuid);
        when(npc.isRespawnOnDeath()).thenReturn(false);

        new NPCDeathListener(plugin).onEntityDeath(event);
        verify(npc).clearActionQueue();
        verify(npc).cancelCurrentAction();
        verify(npc, never()).respawn();
    }
}
