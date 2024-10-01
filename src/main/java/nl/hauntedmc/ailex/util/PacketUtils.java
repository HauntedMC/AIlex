package nl.hauntedmc.ailex.util;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPC;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;

public class PacketUtils {

    private static final long PACKET_SEND_DELAY = 40L;

    /**
     * Send a player info add packet to a player
     * @param player the player to send the packet to
     * @param npc the NPC to send the packet for
     */
    public static void sendPlayerInfoAddPacket(Player player, NPC npc) {
        UserProfile userProfile = new UserProfile(npc.getUUID(), npc.getName(), List.of(new TextureProperty("textures", npc.getSkinTexture(), npc.getSkinSignature())));
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(userProfile, true, 0,  GameMode.SURVIVAL, FormatterUtils.serializer.deserialize(npc.getDisplayTabName()), null);
        WrapperPlayServerPlayerInfoUpdate packet =  new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                info);

        sendPacketDelayed(player, packet, PACKET_SEND_DELAY);
    }

    /**
     * Broadcast a player info add packet
     * @param npc the NPC to send the packet for
     */
    public static void broadcastPlayerInfoAddPacket(NPC npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendPlayerInfoAddPacket(player, npc);
        }
    }

    /**
     * Send a player info remove packet to a player
     * @param player the player to send the packet to
     * @param npc the NPC to send the packet for
     */
    public static void sendPlayerInfoRemovePacket(Player player, NPC npc) {
        WrapperPlayServerPlayerInfoRemove packet = new WrapperPlayServerPlayerInfoRemove(npc.getUUID());
        sendPacket(player, packet);
    }

    /**
     * Broadcast a player info remove packet
     * @param npc the NPC to send the packet for
     */
    public static void broadcastPlayerInfoRemovePacket(NPC npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendPlayerInfoRemovePacket(player, npc);
        }
    }


    /**
     * Send a packet to a player
     * @param player the player to send the packet to
     * @param packetWrapper the packet to send
     * @param <T> the type of the packet
     */
    private static <T extends PacketWrapper<?>> void sendPacket(Player player, T packetWrapper) {
        if (player != null && packetWrapper != null) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packetWrapper);
        } else {
            throw new IllegalArgumentException("Player or packet cannot be null");
        }
    }
    
    /**
     * Send a packet to a player with a delay
     * @param player the player to send the packet to
     * @param packetWrapper the packet to send
     * @param delay the delay in ticks
     * @param <T> the type of the packet
     */
    private static <T extends PacketWrapper<?>> void sendPacketDelayed(Player player, T packetWrapper, long delay) {
        Bukkit.getScheduler().runTaskLater(AIlexPlugin.getPlugin(), () -> sendPacket(player, packetWrapper), delay);
    }

}
