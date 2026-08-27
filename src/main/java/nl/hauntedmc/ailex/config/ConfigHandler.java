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

/** Utility class for handling the current plugin configuration. */
public class ConfigHandler {

    private static final String ASSISTANT_DEADLINE_PATH = "openai.assistant.total_deadline_seconds";
    private static final int LEGACY_ASSISTANT_DEADLINE_SECONDS = 18;
    private static final int CURRENT_ASSISTANT_DEADLINE_SECONDS = 30;

    private static ConfigHandler instance;
    private final JavaPlugin plugin;

    private ConfigHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static ConfigHandler getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConfigHandler is not initialized. Call init() first.");
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
     * Aligns the active configuration with the bundled current schema.
     * Existing values for valid keys are preserved, missing keys receive the bundled default, and unknown keys are removed.
     */
    private void synchronizeConfigWithDefaults() {
        InputStream defaultsStream = plugin.getResource("config.yml");
        if (defaultsStream == null) {
            return;
        }
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)
        );
        syncSection(plugin.getConfig(), defaults, "");
        migrateKnownDefaults(plugin.getConfig());
        plugin.saveConfig();
    }

    /**
     * Migrates shipped defaults that turned out to be operationally unsafe. This intentionally targets exact historical
     * defaults only: custom operator values remain untouched. The 18-second assistant budget was the 1.9.0/1.9.1 default
     * and could terminate otherwise healthy grounded/structured requests before the provider returned.
     */
    private void migrateKnownDefaults(FileConfiguration currentConfig) {
        if (currentConfig.getInt(ASSISTANT_DEADLINE_PATH, CURRENT_ASSISTANT_DEADLINE_SECONDS)
                == LEGACY_ASSISTANT_DEADLINE_SECONDS) {
            currentConfig.set(ASSISTANT_DEADLINE_PATH, CURRENT_ASSISTANT_DEADLINE_SECONDS);
            LoggerUtils.logInfo("Migrated AIlex assistant deadline from 18s to 30s.");
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
                currentConfig.set(fullPath(currentPath, currentKey), null);
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
        return parentPath.isEmpty() ? key : parentPath + "." + key;
    }
}
