package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigHandler190Test {

    @AfterEach
    void tearDown() {
        ConfigTestSupport.reset();
    }

    @Test
    void shouldMigrateUntouchedChatDefaultsButPreserveCustomProfileValues() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("config_revision", 3);
        config.set("openai.model", "gpt-5.6-luna");
        config.set("openai.max_output_tokens", 240);
        config.set("openai.assistant.models.fast.model", "gpt-5.6-luna");
        config.set("openai.assistant.models.fast.max_output_tokens", 260);
        config.set("openai.assistant.models.grounded.max_output_tokens", 777);
        config.set("openai.assistant.models.deliberate.max_output_tokens", 800);
        config.set("openai.assistant.delivery.max_lines_fast", 1);
        config.set("openai.assistant.delivery.max_lines_grounded", 4);
        config.set("openai.assistant.delivery.max_lines_deliberate", 5);
        config.set("openai.assistant.delivery.max_line_characters", 280);
        config.set("openai.assistant.actions.enabled", true);
        config.set("npc.defaults.entity.prompts.userPromptTemplate", oldPrompt());

        String defaults = "config_revision: 4\n"
                + "openai:\n"
                + "  model: gpt-5.6-terra\n"
                + "  max_output_tokens: 360\n"
                + "  assistant:\n"
                + "    actions:\n"
                + "      enabled: false\n"
                + "    models:\n"
                + "      fast:\n"
                + "        model: gpt-5.6-terra\n"
                + "        max_output_tokens: 400\n"
                + "      grounded:\n"
                + "        max_output_tokens: 640\n"
                + "      deliberate:\n"
                + "        max_output_tokens: 1000\n"
                + "    delivery:\n"
                + "      max_lines_fast: 3\n"
                + "      max_lines_grounded: 5\n"
                + "      max_lines_deliberate: 8\n"
                + "      max_line_characters: 300\n"
                + "npc:\n"
                + "  defaults:\n"
                + "    entity:\n"
                + "      prompts:\n"
                + "        userPromptTemplate: 'new default'\n";
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getResource("config.yml")).thenAnswer(invocation -> new ByteArrayInputStream(
                defaults.getBytes(StandardCharsets.UTF_8)
        ));

        ConfigHandler.init(plugin);

        assertEquals(4, config.getInt("config_revision"));
        assertEquals("gpt-5.6-terra", config.getString("openai.model"));
        assertEquals(360, config.getInt("openai.max_output_tokens"));
        assertEquals("gpt-5.6-terra", config.getString("openai.assistant.models.fast.model"));
        assertEquals(400, config.getInt("openai.assistant.models.fast.max_output_tokens"));
        assertEquals(777, config.getInt("openai.assistant.models.grounded.max_output_tokens"));
        assertEquals(1_000, config.getInt("openai.assistant.models.deliberate.max_output_tokens"));
        assertEquals(3, config.getInt("openai.assistant.delivery.max_lines_fast"));
        assertEquals(5, config.getInt("openai.assistant.delivery.max_lines_grounded"));
        assertEquals(8, config.getInt("openai.assistant.delivery.max_lines_deliberate"));
        assertFalse(config.getBoolean("openai.assistant.actions.enabled"));
        assertEquals(newPrompt(), config.getString("npc.defaults.entity.prompts.userPromptTemplate"));
    }

    private String oldPrompt() {
        return "Bericht van speler {player_name}: \"{chat_message}\". Behandel dit als een spelersvraag en antwoord als "
                + "{npc_name} in één korte, gewone chatregel: direct nuttig, passend bij de toon en zonder speaker label.";
    }

    private String newPrompt() {
        return "Bericht van speler {player_name}: \"{chat_message}\". Behandel dit als een spelersvraag en antwoord als "
                + "{npc_name}: direct nuttig, natuurlijk, beknopt maar volledig genoeg om de vraag goed te beantwoorden, "
                + "zonder speaker label.";
    }
}
