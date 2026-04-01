package nl.hauntedmc.ailex.listener.player;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCHandler;
import nl.hauntedmc.ailex.util.PacketUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerListenersTest {

    @Test
    void joinListenerShouldSendAddPacketForAllNpcs() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler npcHandler = mock(NPCHandler.class);
        NPC npcA = mock(NPC.class);
        NPC npcB = mock(NPC.class);
        Player player = mock(Player.class);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);

        HashMap<Integer, NPC> registry = new HashMap<>();
        registry.put(1, npcA);
        registry.put(2, npcB);

        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(npcHandler.getNPCRegistry()).thenReturn(registry);
        when(event.getPlayer()).thenReturn(player);

        try (MockedStatic<PacketUtils> mockedPackets = org.mockito.Mockito.mockStatic(PacketUtils.class)) {
            new PlayerJoinListener(plugin).onPlayerJoin(event);
            mockedPackets.verify(() -> PacketUtils.sendPlayerInfoAddPacket(player, npcA));
            mockedPackets.verify(() -> PacketUtils.sendPlayerInfoAddPacket(player, npcB));
        }
    }

    @Test
    void leaveListenerShouldSendRemovePacketForAllNpcs() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler npcHandler = mock(NPCHandler.class);
        NPC npcA = mock(NPC.class);
        NPC npcB = mock(NPC.class);
        Player player = mock(Player.class);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);

        HashMap<Integer, NPC> registry = new HashMap<>();
        registry.put(1, npcA);
        registry.put(2, npcB);

        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(npcHandler.getNPCRegistry()).thenReturn(registry);
        when(event.getPlayer()).thenReturn(player);

        try (MockedStatic<PacketUtils> mockedPackets = org.mockito.Mockito.mockStatic(PacketUtils.class)) {
            new PlayerLeaveListener(plugin).onPlayerJoin(event);
            mockedPackets.verify(() -> PacketUtils.sendPlayerInfoRemovePacket(player, npcA));
            mockedPackets.verify(() -> PacketUtils.sendPlayerInfoRemovePacket(player, npcB));
        }
    }
}
