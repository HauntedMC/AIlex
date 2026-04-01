package nl.hauntedmc.ailex.ai.llm;

import nl.hauntedmc.ailex.util.LoggerUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatGPTClientTest {

    @Test
    void shouldCreateRequestWithConfiguredModelAndApiKey() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.api_key", "test-key");
        config.set("openai.model", "gpt-3.5-turbo-instruct");
        when(plugin.getConfig()).thenReturn(config);

        ChatGPTClient client;
        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            client = new ChatGPTClient(plugin);
        }

        Method createHttpRequest = ChatGPTClient.class.getDeclaredMethod("createHttpRequest", String.class);
        createHttpRequest.setAccessible(true);
        HttpRequest request = (HttpRequest) createHttpRequest.invoke(client, "hello");

        assertEquals("https://api.openai.com/v1/engines/gpt-3.5-turbo-instruct/completions", request.uri().toString());
        assertEquals("Bearer test-key", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void shouldEscapeJsonSensitiveCharacters() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.api_key", "key");
        config.set("openai.model", "model");
        when(plugin.getConfig()).thenReturn(config);

        ChatGPTClient client;
        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            client = new ChatGPTClient(plugin);
        }

        Method escapeJson = ChatGPTClient.class.getDeclaredMethod("escapeJson", String.class);
        escapeJson.setAccessible(true);
        String escaped = (String) escapeJson.invoke(client, "\"line1\"\nline2\\");

        assertTrue(escaped.contains("\\\"line1\\\""));
        assertTrue(escaped.contains("\\n"));
        assertTrue(escaped.contains("\\\\"));
    }
}
