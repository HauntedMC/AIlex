package nl.hauntedmc.ailex.ai.llm;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatGPTClientTest {

    @Test
    void shouldUseResponsesApiWithConfiguredModelAndApiKey() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, "{\"output_text\":\"Hi there\"}");
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
            assertEquals(3, input.size());
            assertEquals(
                    ChatGPTClient.SAFETY_SYSTEM_PROMPT,
                    input.get(0).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().get("text")
                            .getAsString()
            );
            JsonObject contentObject = input.get(2)
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
    void shouldNormalizeAssistantOutputToOneChatLine() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, "{\"output_text\":\"  \\\"Hoi\\n daar\\\"  \"}");
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient("key", "gpt-4.1-mini", httpClient);
            assertEquals("Hoi daar", client.getChatResponse("hello"));
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
            assertEquals(4, input.size());
            assertEquals(
                    "system persona",
                    input.get(1).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().get("text")
                            .getAsString()
            );
            assertEquals(
                    "hello",
                    input.get(3).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().get("text")
                            .getAsString()
            );
        }
    }

    @Test
    void shouldReturnSafetyFallbackWhenResponseContainsInappropriateContent() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, "{\"output_text\":\"Dat klinkt wel seksueel.\"}");
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient(
                    "key",
                    "gpt-4.1-mini",
                    httpClient,
                    true,
                    ChatGPTClient.SAFETY_SYSTEM_PROMPT,
                    "veilig antwoord"
            );
            assertEquals("veilig antwoord", client.getChatResponse("hello"));
        }
    }

    @Test
    void shouldAllowResponseWhenSafetyGuardIsDisabled() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockStringResponse(200, "{\"output_text\":\"seksueel\"}");
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ChatGPTClient client = new ChatGPTClient(
                    "key",
                    "gpt-4.1-mini",
                    httpClient,
                    false,
                    "",
                    "veilig antwoord"
            );
            assertEquals("seksueel", client.getChatResponse("hello"));
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
