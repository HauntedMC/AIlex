package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.npc.NPCProperties;
import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utility class for handling the plugin configuration.
 */
public class ConfigHandler {

    private static ConfigHandler instance;
    private final JavaPlugin plugin;

    /**
     * Private constructor for the ConfigHandler.
     * @param plugin the plugin to initialize the ConfigHandler with
     */
    private ConfigHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the instance of the ConfigHandler.
     * This method can be called after calling init().
     * @return the instance of the ConfigHandler
     */
    public static ConfigHandler getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AilexLogger is not initialized. Call init() first.");
        }
        return instance;
    }

    /**
     * Initializes the ConfigHandler with the given plugin.
     * This method should be called before calling getInstance().
     * @param plugin - the plugin to initialize the ConfigHandler with
     */
    public static void init(JavaPlugin plugin) {
        if (instance == null) {
            instance = new ConfigHandler(plugin);
        }
    }

    /**
     * Reloads the plugin configuration.
     * This method should be called after the configuration file has been modified.
     */
    public void reload() {
        plugin.reloadConfig();
        LoggerUtils.logInfo("Configuration reloaded.");
    }

    /**
     * Gets the plugin configuration.
     * @return the plugin configuration
     */
    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    /**
     * Build default NPC properties from configuration.
     * @return a new mutable {@link NPCProperties} instance populated from config defaults
     */
    public NPCProperties getDefaultNPCProperties() {
        FileConfiguration config = getConfig();
        return new NPCProperties(
                config.getString("npc.defaults.entity.prefix", NPCProperties.DEFAULT_PREFIX),
                config.getString("npc.defaults.entity.tabPrefix", NPCProperties.DEFAULT_TAB_PREFIX),
                config.getInt("npc.defaults.entity.tabListOrder", NPCProperties.DEFAULT_TAB_LIST_ORDER),
                config.getBoolean("npc.defaults.entity.damageable", NPCProperties.DEFAULT_DAMAGEABLE),
                config.getBoolean("npc.defaults.entity.respawnOnDeath", NPCProperties.DEFAULT_RESPAWN_ON_DEATH),
                config.getBoolean("npc.defaults.entity.chatEnabled", NPCProperties.DEFAULT_CHAT_ENABLED),
                config.getBoolean("npc.defaults.entity.listedInTab", NPCProperties.DEFAULT_LISTED_IN_TAB),
                config.getBoolean("npc.defaults.entity.alwaysUseNameHologram",
                        NPCProperties.DEFAULT_ALWAYS_USE_NAME_HOLOGRAM)
        );
    }
}
