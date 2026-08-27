package nl.hauntedmc.ailex.assistant.application.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import nl.hauntedmc.ailex.util.LoggerUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiToolPlanningClientTest {

    @Test
    void parsesFunctionCallsAndProviderUsage() {
        YamlConfiguration config = configuredPlanner();
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

    @Test
    void transientPlannerFailureEntersCooldownInsteadOfHammeringProvider() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(503, "{}");
        when(httpClient.send(any(HttpRequest.class), stringBodyHandler())).thenReturn(response);
        OpenAiToolPlanningClient client = new OpenAiToolPlanningClient(configuredPlanner(), httpClient);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            assertFalse(client.plan(history(), List.of(), Duration.ofSeconds(2)).success());
            assertFalse(client.plan(history(), List.of(), Duration.ofSeconds(2)).success());
        }

        verify(httpClient, times(1)).send(any(HttpRequest.class), stringBodyHandler());
    }

    @Test
    void malformedPlannerResponseEntersCooldown() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, "{bad-json");
        when(httpClient.send(any(HttpRequest.class), stringBodyHandler())).thenReturn(response);
        OpenAiToolPlanningClient client = new OpenAiToolPlanningClient(configuredPlanner(), httpClient);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            assertFalse(client.plan(history(), List.of(), Duration.ofSeconds(2)).success());
            assertFalse(client.plan(history(), List.of(), Duration.ofSeconds(2)).success());
        }

        verify(httpClient, times(1)).send(any(HttpRequest.class), stringBodyHandler());
    }

    @Test
    void ordinaryClientRejectionDoesNotDisablePlanner() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(400, "{}");
        when(httpClient.send(any(HttpRequest.class), stringBodyHandler())).thenReturn(response);
        OpenAiToolPlanningClient client = new OpenAiToolPlanningClient(configuredPlanner(), httpClient);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            assertFalse(client.plan(history(), List.of(), Duration.ofSeconds(2)).success());
            assertFalse(client.plan(history(), List.of(), Duration.ofSeconds(2)).success());
        }

        verify(httpClient, times(2)).send(any(HttpRequest.class), stringBodyHandler());
    }

    private static YamlConfiguration configuredPlanner() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.api_key", "test");
        config.set("openai.assistant.agent.planner_model", "planner");
        return config;
    }

    private static List<JsonElement> history() {
        return List.of(OpenAiToolPlanningClient.userMessage("Find missing evidence"));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse.BodyHandler<String> stringBodyHandler() {
        return (HttpResponse.BodyHandler<String>) any(HttpResponse.BodyHandler.class);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockStringResponse(int statusCode, String body) {
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }
}
