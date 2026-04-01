package nl.hauntedmc.ailex.util;

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
}
