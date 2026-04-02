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
    private static final long LIST_ORDER_UPDATE_DELAY = PACKET_SEND_DELAY + 1L;

    /**
     * Send a player info add packet to a player
     * @param player the player to send the packet to
     * @param npc the NPC to send the packet for
     */
    public static void sendPlayerInfoAddPacket(Player player, NPC npc) {
        sendPacketDelayed(player, new WrapperPlayServerPlayerInfoRemove(npc.getUUID()), PACKET_SEND_DELAY - 1L);
        sendPacketDelayed(player, createPlayerInfoAddPacket(npc), PACKET_SEND_DELAY);
        sendPacketDelayed(player, createPlayerInfoListOrderUpdatePacket(npc), LIST_ORDER_UPDATE_DELAY);
    }

    static WrapperPlayServerPlayerInfoUpdate createPlayerInfoAddPacket(NPC npc) {
        return new WrapperPlayServerPlayerInfoUpdate(createPlayerInfoAddActions(), createPlayerInfoEntry(npc));
    }

    static EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> createPlayerInfoAddActions() {
        return EnumSet.of(
                WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER
        );
    }

    static WrapperPlayServerPlayerInfoUpdate.PlayerInfo createPlayerInfoEntry(NPC npc) {
        UserProfile userProfile = new UserProfile(
                npc.getUUID(),
                npc.getName(),
                List.of(new TextureProperty("textures", npc.getSkinTexture(), npc.getSkinSignature()))
        );
        return new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                userProfile,
                npc.isListedInTab(),
                0,
                GameMode.SURVIVAL,
                FormatterUtils.serializer.deserialize(npc.getDisplayTabName()),
                null,
                npc.getTabListOrder()
        );
    }

    static WrapperPlayServerPlayerInfoUpdate createPlayerInfoListOrderUpdatePacket(NPC npc) {
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                new UserProfile(npc.getUUID(), npc.getName())
        );
        entry.setListOrder(npc.getTabListOrder());
        return new WrapperPlayServerPlayerInfoUpdate(
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER,
                entry
        );
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
