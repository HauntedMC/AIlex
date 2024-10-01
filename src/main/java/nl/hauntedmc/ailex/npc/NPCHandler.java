package nl.hauntedmc.ailex.npc;

import nl.hauntedmc.ailex.config.DataHandler;
import nl.hauntedmc.ailex.util.PacketUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for NPCs spawned by AIlex
 */
public class NPCHandler {

    private final HashMap<Integer, NPC> npcRegistry;

    /**
     * Constructor for the NPCHandler
     */
    public NPCHandler() {
        npcRegistry = new HashMap<>();
    }

    /**
     * Create a new NPC of the given class at the given location with the given id and name
     * If an NPC with the given id already exists, it will not be created
     * @param npcClass the class of the NPC
     * @param <T> the type of the NPC
     */
    public <T extends NPC> void createNPC(Class<T> npcClass, NPCData npcData) {
        if (!npcRegistry.containsKey(npcData.getId())) {
            try {
                // Create a new instance of the NPC
                T npc = npcClass.getDeclaredConstructor(NPCData.class).newInstance(npcData);

                // Register the NPC
                npcRegistry.put(npcData.getId(), npc);

                // Save the NPC data to the data file
                DataHandler.saveNPC(npcData);

                // Spawn the NPC
                npc.spawn();

                // Broadcast the player info add packet
                PacketUtils.broadcastPlayerInfoAddPacket(npc);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to create NPC of class: " + npcClass.getName(), e);
            }
        } else {
            throw new IllegalArgumentException("NPC with ID " + npcData.getId() + " already exists.");
        }
    }

    /**
     * Remove the NPC with the given id
     * @param id the id of the NPC to remove
     */
    public void removeNPC(int id) {
        if (this.npcRegistry.containsKey(id)) {
            // Remove the NPC from the data file
            DataHandler.removeNPC(id);

            // Broadcast the player info add packet
            PacketUtils.broadcastPlayerInfoRemovePacket(npcRegistry.get(id));

            // Remove the NPC from the game
            npcRegistry.get(id).remove();

            // Clean up the NPC registry by removing the NPC
            npcRegistry.remove(id);
        } else {
            throw new IllegalArgumentException("NPC with ID " + id + " does not exist.");
        }
    }

    /**
     * Save the NPC with the given id
     * @param id the id of the NPC to save
     */
    public void saveNPC(int id) {
        if (npcRegistry.containsKey(id)) {
            // Get the NPC data from the registry
            NPCData npcData = npcRegistry.get(id).getNPCData();

            // Save the NPC data to the data file
            DataHandler.saveNPC(npcData);
        } else {
            throw new IllegalArgumentException("NPC with ID " + id + " does not exist.");
        }
    }

    /**
     * Load all NPCs from the data config
     * This method will create and register all NPCs that are saved in the data config
     */
    public void loadNPCs() {
        Map<Integer, NPCData> npcDataMap = DataHandler.loadNPCs();
        for (NPCData npcData : npcDataMap.values()) {
            try {
                Class<? extends NPC> npcClass = (Class<? extends NPC>) Class.forName(npcData.getNpcClass());
                createNPC(npcClass, npcData);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the NPCRegistry map
     * @return the NPCRegistry map
     */
    public HashMap<Integer, NPC> getNPCRegistry() {
        return npcRegistry;
    }

    /**
     * Unload all NPCs
     * This method will save all NPCs to the data file and remove them from the game
     */
    public void unloadAllNPCs() {
        for (NPC npc : npcRegistry.values()) {
            unloadNPC(npc);
        }
    }

    /**
     * Unload the given NPC
     * @param npc the NPC to unload
     */
    private void unloadNPC(NPC npc) {
        // Save the NPC data to the data file
        DataHandler.saveNPC(npc.getNPCData());

        // Physically remove the NPC from the game
        npc.remove();
    }

    /**
     * Clear the NPCRegistry
     */
    public void clearNPCRegistry() {
        npcRegistry.clear();
    }
}
