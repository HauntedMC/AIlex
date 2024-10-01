package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPCData;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles the data.yml file.
 * This file is used to store all the data of the NPCs.
 */
public class DataHandler {
    private static YamlConfiguration dataConfig;
    private static File dataFile;

    /**
     * Initialize the data.yml file.
     * @param plugin The AIlex plugin
     */
    public static void init(AIlexPlugin plugin) {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * Save the NPC data to the data.yml file.
     * @param npcData The NPC data to save
     */
    public static void saveNPC(NPCData npcData) {
        String path = "npcs." + npcData.getId();
        dataConfig.set(path + ".name", npcData.getName());
        dataConfig.set(path + ".spawn-location", npcData.getSpawnLocation());
        dataConfig.set(path + ".class", npcData.getNpcClass());
        save();
    }

    /**
     * Remove the NPC from the data.yml file.
     * @param id The ID of the NPC to remove
     */
    public static void removeNPC(int id) {
        String path = "npcs." + id;
        dataConfig.set(path, null);
        save();
    }

    /**
     * Load all the NPCs from the data.yml file.
     * @return A map with the ID of the NPC as key and the NPC data as value
     */
    public static Map<Integer, NPCData> loadNPCs() {
        Map<Integer, NPCData> npcDataMap = new HashMap<>();
        if (dataConfig.contains("npcs")) {
            for (String key : dataConfig.getConfigurationSection("npcs").getKeys(false)) {
                int id = Integer.parseInt(key);
                String name = dataConfig.getString("npcs." + id + ".name");
                Location location = dataConfig.getLocation("npcs." + id + ".spawn-location");
                String npcClass = dataConfig.getString("npcs." + id + ".class");

                NPCData npcData = new NPCData(id, name, location, npcClass);
                npcDataMap.put(id, npcData);
            }
        }
        return npcDataMap;
    }

    /**
     * Save the data to the data.yml file.
     */
    private static void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}