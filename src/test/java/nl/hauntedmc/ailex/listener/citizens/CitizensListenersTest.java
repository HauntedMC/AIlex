package nl.hauntedmc.ailex.listener.citizens;

import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.event.SpawnReason;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCHandler;
import nl.hauntedmc.ailex.util.LoggerUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CitizensListenersTest {

    @Test
    void spawnListenerShouldPostInitializeMatchingNpc() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler handler = mock(NPCHandler.class);
        NPC npc = mock(NPC.class);
        UUID npcUuid = UUID.randomUUID();

        HashMap<Integer, NPC> registry = new HashMap<>();
        registry.put(1, npc);

        net.citizensnpcs.api.npc.NPC citizensNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        NPCSpawnEvent event = mock(NPCSpawnEvent.class);

        when(plugin.getNPCHandler()).thenReturn(handler);
        when(handler.getNPCRegistry()).thenReturn(registry);
        when(event.getReason()).thenReturn(SpawnReason.PLUGIN);
        when(event.getNPC()).thenReturn(citizensNpc);
        when(citizensNpc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getCitizensEntityID()).thenReturn(npcUuid);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            new NPCSpawnListener(plugin).onEntitySpawn(event);
            verify(npc).postInitializeNPC();
            mockedLogger.verify(() -> LoggerUtils.logInfo(org.mockito.ArgumentMatchers.contains("NPC loaded")));
        }
    }

    @Test
    void deathListenerShouldResetMatchingNpcState() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler handler = mock(NPCHandler.class);
        NPC npc = mock(NPC.class);
        UUID npcUuid = UUID.randomUUID();

        HashMap<Integer, NPC> registry = new HashMap<>();
        registry.put(1, npc);

        net.citizensnpcs.api.npc.NPC citizensNpc = mock(net.citizensnpcs.api.npc.NPC.class);
        NPCDeathEvent event = mock(NPCDeathEvent.class);

        when(plugin.getNPCHandler()).thenReturn(handler);
        when(handler.getNPCRegistry()).thenReturn(registry);
        when(event.getNPC()).thenReturn(citizensNpc);
        when(citizensNpc.getUniqueId()).thenReturn(npcUuid);
        when(npc.getCitizensEntityID()).thenReturn(npcUuid);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            new NPCDeathListener(plugin).onEntityDeath(event);
            verify(npc).clearActionQueue();
            verify(npc).cancelCurrentAction();
            verify(npc).respawn();
            mockedLogger.verify(() -> LoggerUtils.logInfo(org.mockito.ArgumentMatchers.contains("NPC died")));
        }
    }
}
