package nl.hauntedmc.ailex.infrastructure.openai;

import nl.hauntedmc.ailex.util.LoggerUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiUsageTest {

    @Test
    void shouldExposeResponsesApiUsageAndPromptCacheTokens() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "output": [{"type":"message","role":"assistant","content":[
                    {"type":"output_text","text":"Hoi!"}
                  ]}],
                  "usage": {
                    "input_tokens": 1200,
                    "input_tokens_details": {"cached_tokens": 900, "cache_write_tokens": 200},
                    "output_tokens": 18,
                    "total_tokens": 1218
                  }
                }
                """);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        try (MockedStatic<LoggerUtils> ignored = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            OpenAiResponsesClient client = new OpenAiResponsesClient("key", "gpt-5.6-luna", http);
            OpenAiResponsesClient.ResponseResult result = client.getChatResult(
                    "system", "hello", OpenAiResponsesClient.RequestOptions.defaults()
            );

            assertTrue(result.success());
            assertEquals("Hoi!", result.text());
            assertEquals(1200L, result.usage().inputTokens());
            assertEquals(900L, result.usage().cachedInputTokens());
            assertEquals(200L, result.usage().cacheWriteTokens());
            assertEquals(18L, result.usage().outputTokens());
            assertEquals(1218L, result.usage().totalTokens());
            assertEquals(0.75D, result.usage().cacheHitRatio(), 0.0001D);
        }
    }

    @Test
    void usageShouldBeEmptyWhenProviderOmitsIt() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"output":[{"type":"output_text","text":"ok"}]}
                """);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        try (MockedStatic<LoggerUtils> ignored = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            OpenAiResponsesClient.ResponseResult result = new OpenAiResponsesClient("key", "model", http)
                    .getChatResult("", "hello", OpenAiResponsesClient.RequestOptions.defaults());
            assertEquals(OpenAiResponsesClient.Usage.empty(), result.usage());
        }
    }
}
