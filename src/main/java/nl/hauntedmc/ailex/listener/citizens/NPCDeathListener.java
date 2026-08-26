package nl.hauntedmc.ailex.listener.citizens;

import net.citizensnpcs.api.event.NPCDeathEvent;

import nl.hauntedmc.ailex.AIlexPlugin;

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
        if (!plugin.getNpcManager().ownsCitizensNpc(event.getNPC().getUniqueId())) {
            return;
        }
        plugin.getNpcManager().getNPCRegistry().values().stream()
                .filter(npc -> event.getNPC().getUniqueId().equals(npc.getCitizensEntityID()))
                .forEach(npc -> {
                    npc.clearActionQueue();
                    npc.cancelCurrentAction();
                    if (npc.isRespawnOnDeath()) {
                        npc.respawn();
                    }
                });
    }
}
