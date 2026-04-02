package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPCData;
import nl.hauntedmc.ailex.npc.NPCProperties;

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
        dataConfig.set(path + ".spawn-location", npcData.getSpawnLocation());
        dataConfig.set(path + ".class", npcData.getNpcClass());
        dataConfig.set(path + ".name", npcData.getName());
        dataConfig.set(path + ".entity.name", npcData.getName());

        NPCProperties properties = npcData.getProperties() == null
                ? NPCProperties.defaultValues()
                : npcData.getProperties();
        String propertiesPath = path + ".entity.properties";
        dataConfig.set(propertiesPath + ".prefix", properties.getPrefix());
        dataConfig.set(propertiesPath + ".tabPrefix", properties.getTabPrefix());
        dataConfig.set(propertiesPath + ".tabListOrder", properties.getTabListOrder());
        dataConfig.set(propertiesPath + ".damageable", properties.isDamageable());
        dataConfig.set(propertiesPath + ".respawnOnDeath", properties.isRespawnOnDeath());
        dataConfig.set(propertiesPath + ".chatEnabled", properties.isChatEnabled());
        dataConfig.set(propertiesPath + ".listedInTab", properties.isListedInTab());
        dataConfig.set(propertiesPath + ".alwaysUseNameHologram", properties.isAlwaysUseNameHologram());
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
        NPCProperties defaultProperties = getDefaultProperties();
        if (dataConfig.contains("npcs")) {
            for (String key : dataConfig.getConfigurationSection("npcs").getKeys(false)) {
                int id = Integer.parseInt(key);
                String basePath = "npcs." + id;
                String name = dataConfig.getString(basePath + ".entity.name", dataConfig.getString(basePath + ".name"));
                Location location = dataConfig.getLocation(basePath + ".spawn-location");
                String npcClass = dataConfig.getString(basePath + ".class");
                NPCProperties properties = loadProperties(basePath + ".entity.properties", defaultProperties);

                NPCData npcData = new NPCData(id, name, location, npcClass, properties);
                npcDataMap.put(id, npcData);
            }
        }
        return npcDataMap;
    }

    private static NPCProperties getDefaultProperties() {
        try {
            return ConfigHandler.getInstance().getDefaultNPCProperties();
        } catch (IllegalStateException exception) {
            return NPCProperties.defaultValues();
        }
    }

    private static NPCProperties loadProperties(String path, NPCProperties defaults) {
        return new NPCProperties(
                dataConfig.getString(path + ".prefix", defaults.getPrefix()),
                dataConfig.getString(path + ".tabPrefix", defaults.getTabPrefix()),
                dataConfig.getInt(path + ".tabListOrder", defaults.getTabListOrder()),
                dataConfig.getBoolean(path + ".damageable", defaults.isDamageable()),
                dataConfig.getBoolean(path + ".respawnOnDeath", defaults.isRespawnOnDeath()),
                dataConfig.getBoolean(path + ".chatEnabled", defaults.isChatEnabled()),
                dataConfig.getBoolean(path + ".listedInTab", defaults.isListedInTab()),
                dataConfig.getBoolean(path + ".alwaysUseNameHologram", defaults.isAlwaysUseNameHologram())
        );
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
