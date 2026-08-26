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

/** Utility class for handling the plugin configuration. */
public class ConfigHandler {

    private static final int CURRENT_CONFIG_VERSION = 2;
    private static ConfigHandler instance;
    private final JavaPlugin plugin;

    private ConfigHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static ConfigHandler getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AilexLogger is not initialized. Call init() first.");
        }
        return instance;
    }

    public static void init(JavaPlugin plugin) {
        if (instance == null) {
            instance = new ConfigHandler(plugin);
        }
        instance.synchronizeConfigWithDefaults();
    }

    public void reload() {
        plugin.reloadConfig();
        synchronizeConfigWithDefaults();
        LoggerUtils.logInfo("Configuration reloaded.");
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

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
                config.getString("npc.defaults.entity.prompts.systemPrompt", NPCProperties.DEFAULT_SYSTEM_PROMPT),
                config.getString("npc.defaults.entity.prompts.userPromptTemplate",
                        NPCProperties.DEFAULT_USER_PROMPT_TEMPLATE)
        );
    }

    /**
     * Synchronizes plugin config with bundled defaults and applies one-way compatibility migrations first.
     * Existing operator values are retained except where a versioned migration deliberately changes an unsafe old default.
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

        migrate(current);
        syncSection(current, defaults, "");
        current.set("config_version", CURRENT_CONFIG_VERSION);
        plugin.saveConfig();
    }

    private void migrate(FileConfiguration current) {
        int version = current.getInt("config_version", 1);
        if (version < 2) {
            // 1.4 persisted the raw short-term transcript by default. In 1.5 durable state lives in typed SQLite
            // Memory V2, so an upgrade explicitly turns raw transcript persistence off. Operators may opt in again.
            current.set("openai.chat_context.persist_to_disk", false);
            current.set("config_version", 2);
            LoggerUtils.logInfo("Migrated AIlex config to v2: disabled legacy raw chat transcript persistence.");
        }
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
