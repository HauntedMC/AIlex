package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.npc.NPCProperties;
import nl.hauntedmc.ailex.testutil.ConfigTestSupport;
import nl.hauntedmc.ailex.util.LoggerUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigHandlerTest {

    @AfterEach
    void tearDown() {
        ConfigTestSupport.reset();
    }

    @Test
    void getInstanceShouldFailWhenNotInitialized() {
        assertThrows(IllegalStateException.class, ConfigHandler::getInstance);
    }

    @Test
    void shouldInitializeAndExposeCurrentConfiguration() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration configuration = new YamlConfiguration();
        String defaultsYaml = "openai:\n  api_key: \"\"\n";
        when(plugin.getConfig()).thenReturn(configuration);
        when(plugin.getResource("config.yml")).thenAnswer(
                invocation -> new ByteArrayInputStream(defaultsYaml.getBytes(StandardCharsets.UTF_8))
        );

        ConfigHandler.init(plugin);

        assertEquals(configuration, ConfigHandler.getInstance().getConfig());
        assertTrue(configuration.contains("openai.api_key"));
    }

    @Test
    void reloadShouldRefreshPluginConfigAndLog() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration configuration = new YamlConfiguration();
        String defaultsYaml = "openai:\n  api_key: \"\"\n";
        when(plugin.getConfig()).thenReturn(configuration);
        when(plugin.getResource("config.yml")).thenAnswer(
                invocation -> new ByteArrayInputStream(defaultsYaml.getBytes(StandardCharsets.UTF_8))
        );
        ConfigHandler.init(plugin);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ConfigHandler.getInstance().reload();
            verify(plugin, times(1)).reloadConfig();
            mockedLogger.verify(() -> LoggerUtils.logInfo("Configuration reloaded."), times(1));
        }
    }

    @Test
    void getDefaultNpcPropertiesShouldReadValuesFromConfig() {
        ConfigTestSupport.initWith(Map.of(
                "npc.defaults.entity.prefix", "<gray>[Bot]",
                "npc.defaults.entity.tabPrefix", "<green>◆",
                "npc.defaults.entity.tabListOrder", -2222,
                "npc.defaults.entity.damageable", false,
                "npc.defaults.entity.respawnOnDeath", false,
                "npc.defaults.entity.chatEnabled", false,
                "npc.defaults.entity.listedInTab", false,
                "npc.defaults.entity.alwaysUseNameHologram", true,
                "npc.defaults.entity.prompts.systemPrompt", "system prompt",
                "npc.defaults.entity.prompts.userPromptTemplate", "template {player_name} {chat_message}"
        ));

        NPCProperties properties = ConfigHandler.getInstance().getDefaultNPCProperties();
        assertEquals("<gray>[Bot]", properties.getPrefix());
        assertEquals("<green>◆", properties.getTabPrefix());
        assertEquals(-2222, properties.getTabListOrder());
        assertEquals(false, properties.isDamageable());
        assertEquals(false, properties.isRespawnOnDeath());
        assertEquals(false, properties.isChatEnabled());
        assertEquals(false, properties.isListedInTab());
        assertEquals(true, properties.isAlwaysUseNameHologram());
        assertEquals("system prompt", properties.getSystemPrompt());
        assertEquals("template {player_name} {chat_message}", properties.getUserPromptTemplate());
    }

    @Test
    void initShouldAddMissingKeysRemoveUnknownKeysAndKeepCurrentValues() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("openai.model", "custom-model");
        configuration.set("openai.chat_context.persist_to_disk", true);
        configuration.set("obsolete.value", true);

        String defaultsYaml = "openai:\n"
                + "  api_key: \"\"\n"
                + "  model: \"default-model\"\n"
                + "  chat_context:\n"
                + "    persist_to_disk: false\n"
                + "npc:\n"
                + "  defaults:\n"
                + "    entity:\n"
                + "      prefix: \"<grey>[Speler]\"\n";

        when(plugin.getConfig()).thenReturn(configuration);
        when(plugin.getResource("config.yml")).thenAnswer(
                invocation -> new ByteArrayInputStream(defaultsYaml.getBytes(StandardCharsets.UTF_8))
        );

        ConfigHandler.init(plugin);

        assertEquals("", configuration.getString("openai.api_key"));
        assertEquals("custom-model", configuration.getString("openai.model"));
        assertTrue(configuration.getBoolean("openai.chat_context.persist_to_disk"));
        assertEquals("<grey>[Speler]", configuration.getString("npc.defaults.entity.prefix"));
        assertFalse(configuration.contains("obsolete.value"));
    }
}
