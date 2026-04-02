package nl.hauntedmc.ailex.util;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;

import nl.hauntedmc.ailex.npc.NPC;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PacketUtilsTest {

    @Test
    void sendPacketShouldRejectNullPlayerAndNullPacket() throws Exception {
        Method sendPacket = PacketUtils.class.getDeclaredMethod("sendPacket", Player.class, PacketWrapper.class);
        sendPacket.setAccessible(true);

        InvocationTargetException nullPlayer = assertThrows(InvocationTargetException.class, () -> sendPacket.invoke(null, null, mock(PacketWrapper.class)));
        InvocationTargetException nullPacket = assertThrows(InvocationTargetException.class, () -> sendPacket.invoke(null, mock(Player.class), null));

        assertTrue(nullPlayer.getCause() instanceof IllegalArgumentException);
        assertTrue(nullPacket.getCause() instanceof IllegalArgumentException);
        assertTrue(nullPlayer.getCause().getMessage().contains("cannot be null"));
        assertTrue(nullPacket.getCause().getMessage().contains("cannot be null"));
    }

    @Test
    void createPlayerInfoAddPacketShouldSetLowListOrderForAiPlayers() {
        NPC npc = mock(NPC.class);
        when(npc.getUUID()).thenReturn(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        when(npc.getName()).thenReturn("AI-Bot");
        when(npc.getSkinTexture()).thenReturn("texture");
        when(npc.getSkinSignature()).thenReturn("signature");
        when(npc.getDisplayTabName()).thenReturn("<green>● <grey>[Speler] AI-Bot");
        when(npc.getTabListOrder()).thenReturn(-4321);
        when(npc.isListedInTab()).thenReturn(true);

        var actions = PacketUtils.createPlayerInfoAddActions();
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry = PacketUtils.createPlayerInfoEntry(npc);

        assertTrue(actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER));
        assertEquals(-4321, entry.getListOrder());
        assertEquals("AI-Bot", entry.getGameProfile().getName());
    }
}
