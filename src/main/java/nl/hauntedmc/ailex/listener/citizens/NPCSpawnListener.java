package nl.hauntedmc.ailex.listener.citizens;

import net.citizensnpcs.api.event.NPCSpawnEvent;

import net.citizensnpcs.api.event.SpawnReason;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPC;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Listener for NPC spawn events
 */
public class NPCSpawnListener implements Listener {

    private final AIlexPlugin plugin;

    /**
     * Constructor for the EntitySpawnListener
     * @param plugin the AIlex plugin
     */
    public NPCSpawnListener(AIlexPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when an NPC is spawned
     * This method initializes the entity of the NPC
     * @param event the NPCSpawnEvent
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntitySpawn(NPCSpawnEvent event) {
        if (event.getReason() != SpawnReason.PLUGIN
                || !plugin.getNpcManager().ownsCitizensNpc(event.getNPC().getUniqueId())) {
            return;
        }
        plugin.getNpcManager().getNPCRegistry().values().stream()
                .filter(npc -> event.getNPC().getUniqueId().equals(npc.getCitizensEntityID()))
                .forEach(NPC::postInitializeNPC);
    }
}
