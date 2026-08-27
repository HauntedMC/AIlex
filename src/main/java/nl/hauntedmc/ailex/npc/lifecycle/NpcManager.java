package nl.hauntedmc.ailex.npc.lifecycle;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPCRegistry;

import nl.hauntedmc.ailex.config.DataHandler;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCData;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.util.PacketUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Registry for NPCs spawned by AIlex
 */
public class NpcManager {

    private static final String AILEX_MANAGED_METADATA_KEY = "ailex.managed";
    private static final String AILEX_INTERNAL_ID_METADATA_KEY = "ailex.internal-id";

    private final HashMap<Integer, NPC> npcRegistry;
    private final BooleanSupplier spawningEnabled;

    /**
     * Creates a lifecycle manager for AIlex-owned NPCs.
     */
    public NpcManager() {
        this(() -> true);
    }

    /**
     * Creates an NPC manager with an explicit spawn policy.
     *
     * @param spawningEnabled supplies whether this plugin may create Citizens NPCs
     */
    public NpcManager(BooleanSupplier spawningEnabled) {
        npcRegistry = new HashMap<>();
        this.spawningEnabled = spawningEnabled == null ? () -> true : spawningEnabled;
    }

    /**
     * Create a new NPC of the given class at the given location with the given id and name.
     * Registration and chat availability are independent from whether Citizens can currently spawn the physical entity.
     * If an NPC with the given id already exists, it will not be created.
     *
     * @param npcClass the class of the NPC
     * @param npcData persisted NPC definition
     * @param <T> the type of the NPC
     */
    public <T extends NPC> void createNPC(Class<T> npcClass, NPCData npcData) {
        if (!spawningEnabled.getAsBoolean()) {
            throw new IllegalStateException("NPC spawning is disabled in config.yml.");
        }
        if (npcClass == null) {
            throw new IllegalArgumentException("NPC class is required.");
        }
        if (npcData == null || !npcData.isValid()) {
            throw new IllegalArgumentException("NPC data is invalid.");
        }
        if (npcRegistry.containsKey(npcData.getId())) {
            throw new IllegalArgumentException("NPC with ID " + npcData.getId() + " already exists.");
        }

        final T npc;
        try {
            npc = npcClass.getDeclaredConstructor(NPCData.class).newInstance(npcData);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Failed to create NPC of class: " + npcClass.getName(), exception);
        }

        // The logical assistant is registered and persisted before embodiment. Physical spawn failure must not make
        // an otherwise valid chat assistant disappear from the runtime registry.
        npcRegistry.put(npcData.getId(), npc);
        DataHandler.saveNPC(npcData);

        try {
            npc.spawn();
            if (npc.isSpawned()) {
                PacketUtils.broadcastPlayerInfoAddPacket(npc);
            } else {
                LoggerUtils.logWarning("NPC " + npcData.getName() + " (id " + npcData.getId()
                        + ") is registered for chat but Citizens did not create a physical entity.");
            }
        } catch (RuntimeException exception) {
            LoggerUtils.logWarning("NPC " + npcData.getName() + " (id " + npcData.getId()
                    + ") is registered for chat but physical spawn failed: " + exception.getMessage());
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

            NPC npc = npcRegistry.get(id);
            // Broadcast removal only when a physical Citizens entity actually exists.
            if (npc.isSpawned()) {
                PacketUtils.broadcastPlayerInfoRemovePacket(npc);
            }

            // Remove the NPC from the game/runtime.
            npc.remove();

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
     * Load all NPCs from the data config.
     * One invalid or physically unspawnable NPC must never prevent other configured assistants from loading.
     */
    public void loadNPCs() {
        if (!spawningEnabled.getAsBoolean()) {
            return;
        }
        Map<Integer, NPCData> npcDataMap = DataHandler.loadNPCs();
        removeManagedCitizensNpcEntries(npcDataMap);

        for (NPCData npcData : npcDataMap.values()) {
            try {
                Class<? extends NPC> npcClass = (Class<? extends NPC>) Class.forName(npcData.getNpcClass());
                createNPC(npcClass, npcData);
            } catch (ClassNotFoundException exception) {
                LoggerUtils.logError("Could not load NPC " + npcData.getId() + ": class "
                        + npcData.getNpcClass() + " was not found.");
            } catch (RuntimeException exception) {
                LoggerUtils.logError("Could not load NPC " + npcData.getId() + ": " + exception.getMessage());
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
     * Returns whether the Citizens NPC belongs to the active AIlex registry.
     * External Citizens NPCs must never be initialized, reset, or respawned by this plugin.
     *
     * @param citizensId the Citizens NPC UUID
     * @return true only for an NPC created by this manager
     */
    public boolean ownsCitizensNpc(UUID citizensId) {
        if (citizensId == null) {
            return false;
        }
        return npcRegistry.values().stream().anyMatch(npc -> citizensId.equals(npc.getCitizensEntityID()));
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
     * @param npc The NPC to unload
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
        List<net.citizensnpcs.api.npc.NPC> staleCitizensNpcs = new ArrayList<>();

        for (net.citizensnpcs.api.npc.NPC citizensNpc : citizensRegistry) {
            Boolean managedByAIlexValue = citizensNpc.data().get(AILEX_MANAGED_METADATA_KEY, false);
            boolean managedByAIlex = Boolean.TRUE.equals(managedByAIlexValue);

            Integer internalIdValue = citizensNpc.data().get(AILEX_INTERNAL_ID_METADATA_KEY, Integer.MIN_VALUE);
            boolean matchesTrackedInternalId = internalIdValue != null && trackedNpcIds.contains(internalIdValue);

            if (managedByAIlex || matchesTrackedInternalId) {
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

}
