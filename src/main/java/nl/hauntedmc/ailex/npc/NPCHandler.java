package nl.hauntedmc.ailex.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPCRegistry;

import nl.hauntedmc.ailex.config.DataHandler;
import nl.hauntedmc.ailex.util.PacketUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry for NPCs spawned by AIlex
 */
public class NPCHandler {

    private static final String AILEX_MANAGED_METADATA_KEY = "ailex.managed";
    private static final String AILEX_INTERNAL_ID_METADATA_KEY = "ailex.internal-id";
    private static final String CITIZENS_SHOULD_SAVE_KEY = "should-save";

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
        removeManagedCitizensNpcEntries(npcDataMap);

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

    private void removeManagedCitizensNpcEntries(Map<Integer, NPCData> npcDataMap) {
        NPCRegistry citizensRegistry = CitizensAPI.getNPCRegistry();
        Set<Integer> trackedNpcIds = new HashSet<>(npcDataMap.keySet());
        Set<String> expectedDisplayNames = buildExpectedDisplayNames(npcDataMap);
        List<net.citizensnpcs.api.npc.NPC> staleCitizensNpcs = new ArrayList<>();

        for (net.citizensnpcs.api.npc.NPC citizensNpc : citizensRegistry) {
            Boolean managedByAIlexValue = citizensNpc.data().get(AILEX_MANAGED_METADATA_KEY, false);
            boolean managedByAIlex = Boolean.TRUE.equals(managedByAIlexValue);

            Integer internalIdValue = citizensNpc.data().get(AILEX_INTERNAL_ID_METADATA_KEY, Integer.MIN_VALUE);
            boolean matchesTrackedInternalId = internalIdValue != null && trackedNpcIds.contains(internalIdValue);

            Boolean shouldSaveValue = citizensNpc.data().get(CITIZENS_SHOULD_SAVE_KEY, true);
            boolean shouldSave = !Boolean.FALSE.equals(shouldSaveValue);
            boolean matchesLegacyEphemeralName = !shouldSave && expectedDisplayNames.contains(citizensNpc.getName());

            if (managedByAIlex || matchesTrackedInternalId || matchesLegacyEphemeralName) {
                staleCitizensNpcs.add(citizensNpc);
            }
        }

        if (staleCitizensNpcs.isEmpty()) {
            return;
        }

        for (net.citizensnpcs.api.npc.NPC citizensNpc : staleCitizensNpcs) {
            citizensRegistry.deregister(citizensNpc);
        }

        citizensRegistry.saveToStore();
    }

    private Set<String> buildExpectedDisplayNames(Map<Integer, NPCData> npcDataMap) {
        Set<String> expectedDisplayNames = new HashSet<>();
        for (NPCData npcData : npcDataMap.values()) {
            NPCProperties properties = npcData.getProperties() == null ? NPCProperties.defaultValues() : npcData.getProperties();
            expectedDisplayNames.add(joinParts(properties.getPrefix(), npcData.getName()));
        }
        return expectedDisplayNames;
    }

    private static String joinParts(String... parts) {
        StringBuilder output = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (output.length() > 0) {
                    output.append(' ');
                }
                output.append(part);
            }
        }
        return output.toString();
    }
}
