package nl.hauntedmc.ailex.listener.player;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.util.PacketUtils;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener for player leave events
 */
public class PlayerLeaveListener implements Listener {

    private final AIlexPlugin plugin;

    /**
     * Constructor for the PlayerLeaveListener
     * @param plugin the AIlex plugin
     */
    public PlayerLeaveListener(AIlexPlugin plugin) {
        this.plugin = plugin;
    }


    /**
     * Handle the player join event
     * Send the player info add packet for all NPCs
     * @param event the player join event
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getNPCHandler().getNPCRegistry().values().forEach(npc -> PacketUtils.sendPlayerInfoRemovePacket(event.getPlayer(), npc));
    }
}
