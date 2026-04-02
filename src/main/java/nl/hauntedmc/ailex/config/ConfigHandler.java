package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.npc.NPCProperties;
import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

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
        instance.synchronizeConfigWithDefaults();
    }

    /**
     * Reloads the plugin configuration.
     * This method should be called after the configuration file has been modified.
     */
    public void reload() {
        plugin.reloadConfig();
        synchronizeConfigWithDefaults();
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
                        NPCProperties.DEFAULT_ALWAYS_USE_NAME_HOLOGRAM),
                config.getString("npc.defaults.entity.prompts.systemPrompt",
                        NPCProperties.DEFAULT_SYSTEM_PROMPT),
                config.getString("npc.defaults.entity.prompts.userPromptTemplate",
                        NPCProperties.DEFAULT_USER_PROMPT_TEMPLATE)
        );
    }

    /**
     * Synchronize plugin config with bundled defaults:
     * - missing keys are added
     * - obsolete keys are removed
     */
    private void synchronizeConfigWithDefaults() {
        InputStream defaultsStream = plugin.getResource("config.yml");
        if (defaultsStream == null) {
            return;
        }

        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)
        );
        FileConfiguration current = plugin.getConfig();

        syncSection(current, defaults, "");
        plugin.saveConfig();
    }

    private void syncSection(FileConfiguration currentConfig, ConfigurationSection defaultsSection, String currentPath) {
        ConfigurationSection currentSection = currentPath.isEmpty()
                ? currentConfig
                : currentConfig.getConfigurationSection(currentPath);

        if (currentSection == null) {
            currentConfig.createSection(currentPath);
            currentSection = currentConfig.getConfigurationSection(currentPath);
            if (currentSection == null) {
                return;
            }
        }

        Set<String> currentKeys = new HashSet<>(currentSection.getKeys(false));
        Set<String> defaultKeys = defaultsSection.getKeys(false);

        for (String currentKey : currentKeys) {
            if (!defaultKeys.contains(currentKey)) {
                String removePath = fullPath(currentPath, currentKey);
                currentConfig.set(removePath, null);
            }
        }

        for (String defaultKey : defaultKeys) {
            String keyPath = fullPath(currentPath, defaultKey);
            Object defaultValue = defaultsSection.get(defaultKey);
            boolean defaultIsSection = defaultsSection.isConfigurationSection(defaultKey);
            boolean currentIsSection = currentConfig.isConfigurationSection(keyPath);

            if (defaultIsSection) {
                if (!currentConfig.contains(keyPath) || !currentIsSection) {
                    currentConfig.set(keyPath, null);
                    currentConfig.createSection(keyPath);
                }

                ConfigurationSection nestedDefaults = defaultsSection.getConfigurationSection(defaultKey);
                if (nestedDefaults != null) {
                    syncSection(currentConfig, nestedDefaults, keyPath);
                }
                continue;
            }

            if (!currentConfig.contains(keyPath) || currentIsSection) {
                currentConfig.set(keyPath, defaultValue);
            }
        }
    }

    private String fullPath(String parentPath, String key) {
        if (parentPath.isEmpty()) {
            return key;
        }
        return parentPath + "." + key;
    }
}
