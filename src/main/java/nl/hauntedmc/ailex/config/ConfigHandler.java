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

    private static final int ASSISTANT_CONTEXT_REVISION = 2;
    private static final int CHAT_LIVENESS_REVISION = 3;
    private static final int CHAT_INTELLIGENCE_REVISION = 4;
    private static final String OLD_NPC_USER_PROMPT = "Bericht van speler {player_name}: \"{chat_message}\". "
            + "Behandel dit als een spelersvraag en antwoord als {npc_name} in één korte, gewone chatregel: direct nuttig, "
            + "passend bij de toon en zonder speaker label.";
    private static final String NEW_NPC_USER_PROMPT = "Bericht van speler {player_name}: \"{chat_message}\". "
            + "Behandel dit als een spelersvraag en antwoord als {npc_name}: direct nuttig, natuurlijk, beknopt maar volledig "
            + "genoeg om de vraag goed te beantwoorden, zonder speaker label.";

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

    /** Keeps the generated configuration aligned with current defaults while preserving deliberate operator overrides. */
    private void synchronizeConfigWithDefaults() {
        InputStream defaultsStream = plugin.getResource("config.yml");
        if (defaultsStream == null) {
            return;
        }
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)
        );
        FileConfiguration current = plugin.getConfig();
        int previousRevision = current.getInt("config_revision", 1);
        int bundledRevision = defaults.getInt("config_revision", previousRevision);
        syncSection(current, defaults, "");
        if (previousRevision < ASSISTANT_CONTEXT_REVISION && bundledRevision >= ASSISTANT_CONTEXT_REVISION) {
            migrateLegacyAssistantBudgets(current);
        }
        if (previousRevision < CHAT_LIVENESS_REVISION && bundledRevision >= CHAT_LIVENESS_REVISION) {
            migrateChatLivenessDefaults(current);
        }
        if (previousRevision < CHAT_INTELLIGENCE_REVISION && bundledRevision >= CHAT_INTELLIGENCE_REVISION) {
            migrateChatIntelligenceDefaults(current);
        }
        if (bundledRevision > previousRevision) {
            current.set("config_revision", bundledRevision);
        }
        plugin.saveConfig();
    }

    /** Migrates only values that exactly match the previous shipped defaults; custom operator values remain untouched. */
    private void migrateLegacyAssistantBudgets(FileConfiguration config) {
        migrateInt(config, "openai.max_output_tokens", 200, 240);
        migrateInt(config, "openai.assistant.context.max_input_tokens_fast", 3_000, 4_000);
        migrateInt(config, "openai.assistant.context.max_input_tokens_grounded", 9_000, 12_000);
        migrateInt(config, "openai.assistant.context.max_input_tokens_deliberate", 18_000, 24_000);
        migrateInt(config, "openai.assistant.retrieval.max_chunks", 10, 12);
        migrateInt(config, "openai.assistant.retrieval.max_evidence_characters", 24_000, 32_000);
        migrateInt(config, "openai.assistant.models.fast.max_output_tokens", 220, 260);
        migrateInt(config, "openai.assistant.models.grounded.max_output_tokens", 360, 480);
        migrateInt(config, "openai.assistant.models.deliberate.max_output_tokens", 640, 800);
        migrateInt(config, "openai.assistant.delivery.max_lines_grounded", 3, 4);
        migrateInt(config, "openai.assistant.delivery.max_lines_deliberate", 4, 5);
        migrateInt(config, "openai.assistant.delivery.max_line_characters", 240, 280);
        migrateInt(config, "openai.assistant.memory.max_context_characters", 8_000, 10_000);
        migrateInt(config, "openai.knowledge.max_characters", 24_000, 32_000);
        migrateInt(config, "openai.chat_context.max_message_characters", 720, 900);
        migrateInt(config, "openai.chat_context.max_context_characters", 14_000, 18_000);
        migrateInt(config, "openai.chat_context.general_chat.max_context_characters", 3_000, 4_000);
        migrateInt(config, "openai.chat_context.conversation.max_context_characters", 6_000, 8_000);
        migrateInt(config, "openai.chat_context.bot_memory.max_context_characters", 4_000, 5_000);
    }

    /**
     * Makes sustained conversations practical while preserving explicit operator tuning from older installations.
     * Neither setting controls whether an explicitly named assistant remains addressable.
     */
    private void migrateChatLivenessDefaults(FileConfiguration config) {
        migrateInt(config, "openai.chat.session_timeout_seconds", 300, 900);
        migrateInt(config, "openai.rate_limit.max_responses_per_player", 10, 30);
    }

    /** Moves only untouched 1.8.x defaults to the stronger 1.9 conversational execution profile. */
    private void migrateChatIntelligenceDefaults(FileConfiguration config) {
        migrateString(config, "openai.model", "gpt-5.6-luna", "gpt-5.6-terra");
        migrateInt(config, "openai.max_output_tokens", 240, 360);
        migrateString(config, "openai.assistant.models.fast.model", "gpt-5.6-luna", "gpt-5.6-terra");
        migrateInt(config, "openai.assistant.models.fast.max_output_tokens", 260, 400);
        migrateInt(config, "openai.assistant.models.grounded.max_output_tokens", 480, 640);
        migrateInt(config, "openai.assistant.models.deliberate.max_output_tokens", 800, 1_000);
        migrateInt(config, "openai.assistant.delivery.max_lines_fast", 1, 3);
        migrateInt(config, "openai.assistant.delivery.max_lines_grounded", 4, 5);
        migrateInt(config, "openai.assistant.delivery.max_lines_deliberate", 5, 8);
        migrateInt(config, "openai.assistant.delivery.max_line_characters", 280, 300);
        migrateBoolean(config, "openai.assistant.actions.enabled", true, false);
        migrateString(config, "npc.defaults.entity.prompts.userPromptTemplate", OLD_NPC_USER_PROMPT, NEW_NPC_USER_PROMPT);
    }

    private void migrateInt(FileConfiguration config, String path, int previousDefault, int newDefault) {
        if (config.contains(path) && config.getInt(path) == previousDefault) {
            config.set(path, newDefault);
        }
    }

    private void migrateString(FileConfiguration config, String path, String previousDefault, String newDefault) {
        if (config.contains(path) && previousDefault.equals(config.getString(path))) {
            config.set(path, newDefault);
        }
    }

    private void migrateBoolean(FileConfiguration config, String path, boolean previousDefault, boolean newDefault) {
        if (config.contains(path) && config.getBoolean(path) == previousDefault) {
            config.set(path, newDefault);
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
