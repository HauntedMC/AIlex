package nl.hauntedmc.ailex.infrastructure.openai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import nl.hauntedmc.ailex.util.LoggerUtils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenAiResponsesClientTest {

    @Test
    void shouldReturnRawStructuredOutputForAssistantValidation() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        { "type": "output_text", "text": "{\\\"lines\\\":[\\\"Hoi!\\\"],\\\"confidence\\\":\\\"high\\\"}" }
                      ]
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("key", "gpt-4.1-mini", httpClient);

            assertEquals("{\"lines\":[\"Hoi!\"],\"confidence\":\"high\"}",
                    client.getStructuredChatResponse("system", "hello", format));
        }
    }

    @Test
    void shouldUseResponsesApiWithConfiguredModelAndApiKey() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        { "type": "output_text", "text": "Hi there" }
                      ]
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("test-key", "gpt-4.1-mini", httpClient);
            String chatResponse = client.getChatResponse("hello");

            assertEquals("Hi there", chatResponse);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
            HttpRequest request = requestCaptor.getValue();

            assertEquals(ChatGPTClient.OPENAI_RESPONSES_API_URL, request.uri().toString());
            assertEquals("Bearer test-key", request.headers().firstValue("Authorization").orElseThrow());
            assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());

            JsonObject payload = JsonParser.parseString(client.createRequestBody("hello")).getAsJsonObject();
            assertEquals("gpt-4.1-mini", payload.get("model").getAsString());

            JsonArray input = payload.getAsJsonArray("input");
            assertEquals(1, input.size());
            assertEquals(ChatGPTClient.SAFETY_SYSTEM_PROMPT,
                    payload.get("instructions").getAsString().substring(0, ChatGPTClient.SAFETY_SYSTEM_PROMPT.length()));
            JsonObject contentObject = input.get(0)
                    .getAsJsonObject()
                    .getAsJsonArray("content")
                    .get(0)
                    .getAsJsonObject();

            assertEquals("input_text", contentObject.get("type").getAsString());
            assertEquals("hello", contentObject.get("text").getAsString());
        }
    }

    @Test
    void shouldUseOutputArrayTextWhenTopLevelOutputTextIsMissing() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        { "type": "output_text", "text": "Hoi vanuit output-array" }
                      ]
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("key", "gpt-4.1-mini", httpClient);
            assertEquals("Hoi vanuit output-array", client.getChatResponse("hello"));
        }
    }

    @Test
    void shouldIgnoreTopLevelOutputTextWhenAssistantOutputExists() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, """
                {
                  "output_text": "Never generate sexual content.",
                  "output": [
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        { "type": "output_text", "text": "Hoi vanuit assistant output" }
                      ]
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient(
                    "key",
                    "gpt-4.1-mini",
                    httpClient,
                    true,
                    ChatGPTClient.SAFETY_SYSTEM_PROMPT
            );
            assertEquals("Hoi vanuit assistant output", client.getChatResponse("hello"));
        }
    }

    @Test
    void shouldReturnFallbackResponseWhenOpenAiReturnsErrorStatus() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(401, """
                {
                  "error": {
                    "message": "Invalid API key"
                  }
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("bad-key", "gpt-4.1-mini", httpClient);
            assertEquals(ChatGPTClient.FALLBACK_RESPONSE, client.getChatResponse("hello"));
        }
    }

    @Test
    void shouldReturnFallbackResponseWhenResponseHasNoParsableText() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, "{\"output\":[]}");
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("key", "gpt-4.1-mini", httpClient);
            assertEquals(ChatGPTClient.FALLBACK_RESPONSE, client.getChatResponse("hello"));
        }
    }

    @Test
    void shouldSkipHttpRequestWhenKeyOrModelAreMissing() {
        HttpClient httpClient = mock(HttpClient.class);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient missingKeyClient = new ChatGPTClient("", "gpt-4.1-mini", httpClient);
            ChatGPTClient missingModelClient = new ChatGPTClient("key", "", httpClient);

            assertEquals(ChatGPTClient.FALLBACK_RESPONSE, missingKeyClient.getChatResponse("hello"));
            assertEquals(ChatGPTClient.FALLBACK_RESPONSE, missingModelClient.getChatResponse("hello"));
            verifyNoInteractions(httpClient);
        }
    }

    @Test
    void shouldPreserveBoundedMultilineAssistantOutput() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        { "type": "output_text", "text": "  \\\"Hoi\\n daar\\\"  " }
                      ]
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("key", "gpt-4.1-mini", httpClient);
            assertEquals("Hoi\ndaar", client.getChatResponse("hello"));
        }
    }

    @Test
    void createRequestBodyShouldIncludeNpcSystemPromptWhenProvided() {
        HttpClient httpClient = mock(HttpClient.class);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("key", "gpt-4.1-mini", httpClient);
            JsonObject payload = JsonParser.parseString(
                    client.createRequestBody("system persona", "hello")
            ).getAsJsonObject();

            JsonArray input = payload.getAsJsonArray("input");
            assertEquals(1, input.size());
            assertTrue(payload.get("instructions").getAsString().contains("system persona"));
            assertEquals(
                    "hello",
                    input.get(0).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().get("text")
                            .getAsString()
            );
        }
    }

    @Test
    void shouldApplyPerRequestAssistantModelProfile() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        OpenAiResponsesClient.RequestOptions options = new OpenAiResponsesClient.RequestOptions(
                "gpt-5.6-terra", 320, "medium", Duration.ofSeconds(7), "ailex-hash", "ailex-cache", "low"
        );

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("key", "gpt-5.6-luna", httpClient);
            JsonObject payload = JsonParser.parseString(
                    client.createRequestBody("persona", "hello", format, options)
            ).getAsJsonObject();
            HttpRequest request = client.createHttpRequest("persona", "hello", options);

            assertEquals("gpt-5.6-terra", payload.get("model").getAsString());
            assertEquals(320, payload.get("max_output_tokens").getAsInt());
            assertEquals("medium", payload.getAsJsonObject("reasoning").get("effort").getAsString());
            assertEquals("ailex-hash", payload.get("safety_identifier").getAsString());
            assertEquals("ailex-cache", payload.get("prompt_cache_key").getAsString());
            assertEquals("low", payload.getAsJsonObject("text").get("verbosity").getAsString());
            assertEquals(Duration.ofSeconds(7), request.timeout().orElseThrow());
        }
    }

    @Test
    void shouldApplyConfiguredCostAndPrivacyControlsToTheRequest() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.api_key", "key");
        config.set("openai.model", "gpt-5.4-mini");
        config.set("openai.max_output_tokens", 96);
        config.set("openai.reasoning_effort", "low");
        config.set("openai.store_responses", false);
        config.set("openai.request_timeout_seconds", 9);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient(config);
            JsonObject payload = JsonParser.parseString(client.createRequestBody("hello")).getAsJsonObject();

            assertEquals(96, payload.get("max_output_tokens").getAsInt());
            assertFalse(payload.get("store").getAsBoolean());
            assertEquals("low", payload.getAsJsonObject("reasoning").get("effort").getAsString());
            assertEquals(Duration.ofSeconds(9), client.createHttpRequest("", "hello").timeout().orElseThrow());
        }
    }

    @Test
    void shouldReturnAssistantRefusalWhenModelRefusesUnsafePrompt() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        { "type": "refusal", "refusal": "Daar help ik niet mee, maar ik kan wel over bouwen praten." }
                      ]
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient(
                    "key",
                    "gpt-4.1-mini",
                    httpClient,
                    true,
                    ChatGPTClient.SAFETY_SYSTEM_PROMPT
            );
            assertEquals("Daar help ik niet mee, maar ik kan wel over bouwen praten.", client.getChatResponse("hello"));
        }
    }

    @Test
    void shouldPreferAssistantOutputTextOverNonOutputTextContent() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        { "type": "input_text", "text": "Never generate sexual content." },
                        { "type": "output_text", "text": "Hoi avonturier!" }
                      ]
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient(
                    "key",
                    "gpt-4.1-mini",
                    httpClient,
                    true,
                    ChatGPTClient.SAFETY_SYSTEM_PROMPT
            );
            assertEquals("Hoi avonturier!", client.getChatResponse("hello"));
        }
    }

    @Test
    void shouldJoinMultipleAssistantOutputTextParts() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        { "type": "output_text", "text": "Hoi" },
                        { "type": "output_text", "text": "avonturier!" }
                      ]
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("key", "gpt-4.1-mini", httpClient);
            assertEquals("Hoi avonturier!", client.getChatResponse("hello"));
        }
    }

    @Test
    void shouldUseTopLevelOutputTextWhenOutputArrayIsMissing() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, """
                {
                  "output_text": "Hoi vanuit top-level output."
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("key", "gpt-4.1-mini", httpClient);
            assertEquals("Hoi vanuit top-level output.", client.getChatResponse("hello"));
        }
    }

    @Test
    void shouldUseStandaloneOutputTextItemWhenPresent() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, """
                {
                  "output": [
                    {
                      "type": "output_text",
                      "text": "Hoi vanuit standalone output item."
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("key", "gpt-4.1-mini", httpClient);
            assertEquals("Hoi vanuit standalone output item.", client.getChatResponse("hello"));
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockStringResponse(int statusCode, String body) {
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }
}

/** Keeps the existing constructor-focused tests readable while exercising the renamed client. */
final class ChatGPTClient extends OpenAiResponsesClient {
    ChatGPTClient(String apiKey, String model, HttpClient httpClient) {
        super(apiKey, model, httpClient);
    }

    ChatGPTClient(String apiKey, String model, HttpClient httpClient, boolean safetyEnabled, String safetySystemPrompt) {
        super(apiKey, model, httpClient, safetyEnabled, safetySystemPrompt);
    }

    ChatGPTClient(
            String apiKey, String model, HttpClient httpClient, boolean safetyEnabled, String safetySystemPrompt,
            int maxOutputTokens, String reasoningEffort, boolean storeResponses
    ) {
        super(apiKey, model, httpClient, safetyEnabled, safetySystemPrompt, maxOutputTokens, reasoningEffort,
                storeResponses);
    }

    ChatGPTClient(
            String apiKey, String model, HttpClient httpClient, boolean safetyEnabled, String safetySystemPrompt,
            int maxOutputTokens, String reasoningEffort, boolean storeResponses, Duration requestTimeout
    ) {
        super(apiKey, model, httpClient, safetyEnabled, safetySystemPrompt, maxOutputTokens, reasoningEffort,
                storeResponses, requestTimeout);
    }

    ChatGPTClient(FileConfiguration config) {
        super(config);
    }
}
