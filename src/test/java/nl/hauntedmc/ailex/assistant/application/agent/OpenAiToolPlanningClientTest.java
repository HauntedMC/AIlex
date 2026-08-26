package nl.hauntedmc.ailex.assistant.application.agent;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiToolPlanningClientTest {

    @Test
    void parsesFunctionCallsAndProviderUsage() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.api_key", "test");
        config.set("openai.assistant.agent.planner_model", "planner");
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        OpenAiToolPlanningClient client = new OpenAiToolPlanningClient(plugin);

        OpenAiToolPlanningClient.PlanningResponse response = client.parse("""
                {
                  "output": [
                    {"type":"function_call","name":"search_knowledge","arguments":"{\\"query\\":\\"claims\\"}","call_id":"call-1"}
                  ],
                  "usage": {"input_tokens": 42, "output_tokens": 9}
                }
                """);

        assertTrue(response.success());
        assertEquals(1, response.calls().size());
        assertEquals("search_knowledge", response.calls().getFirst().name());
        assertEquals(42, response.inputTokens());
        assertEquals(9, response.outputTokens());
    }
}
