package nl.hauntedmc.ailex.listener.citizens;

import net.citizensnpcs.api.event.NPCDeathEvent;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Listener for NPC death events
 */
public class NPCDeathListener implements Listener {

    private final AIlexPlugin plugin;

    /**
     * Constructor for the DeathListener
     * @param plugin the AIlex plugin
     */
    public NPCDeathListener(AIlexPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle the entity death event
     * Respawn the NPC when it dies
     * @param event the entity death event
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(NPCDeathEvent event) {
        LoggerUtils.logInfo("NPC died: " + event.getNPC().getName() + " [ID=" + event.getNPC().getId() + "] at " + event.getNPC().getStoredLocation());
        plugin.getNPCHandler().getNPCRegistry().values().stream()
                .filter(npc -> npc.getCitizensEntityID().equals(event.getNPC().getUniqueId()))
                .forEach(npc -> {
                    npc.clearActionQueue();
                    npc.cancelCurrentAction();
                    npc.respawn();
                });
    }
}
