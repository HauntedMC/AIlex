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
        HttpResponse<String> response = response(200, 1200, 900, 200, 18, 1218, "Hoi!");
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
    void shouldAggregateUsageForLegacyStringApisToo() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> first = response(200, 1000, 800, 150, 20, 1020, "eerste");
        HttpResponse<String> second = response(200, 600, 300, 0, 12, 612, "tweede");
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(first, second);

        try (MockedStatic<LoggerUtils> ignored = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            OpenAiResponsesClient client = new OpenAiResponsesClient("key", "gpt-5.6-luna", http);
            assertEquals("eerste", client.getChatResponse("system", "one"));
            assertEquals("tweede", client.getChatResponse("system", "two"));

            OpenAiResponsesClient.UsageSnapshot snapshot = client.usageSnapshot();
            assertEquals(2L, snapshot.requests());
            assertEquals(2L, snapshot.successfulRequests());
            assertEquals(1600L, snapshot.usage().inputTokens());
            assertEquals(1100L, snapshot.usage().cachedInputTokens());
            assertEquals(150L, snapshot.usage().cacheWriteTokens());
            assertEquals(32L, snapshot.usage().outputTokens());
            assertEquals(1632L, snapshot.usage().totalTokens());
            assertTrue(client.usageStatus().contains("cache_hit=68.8%"));
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
            OpenAiResponsesClient client = new OpenAiResponsesClient("key", "model", http);
            OpenAiResponsesClient.ResponseResult result = client
                    .getChatResult("", "hello", OpenAiResponsesClient.RequestOptions.defaults());
            assertEquals(OpenAiResponsesClient.Usage.empty(), result.usage());
            assertEquals(1L, client.usageSnapshot().requests());
            assertEquals(1L, client.usageSnapshot().successfulRequests());
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(
            int status,
            long input,
            long cached,
            long cacheWrite,
            long output,
            long total,
            String text
    ) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("""
                {
                  "output": [{"type":"message","role":"assistant","content":[
                    {"type":"output_text","text":"%s"}
                  ]}],
                  "usage": {
                    "input_tokens": %d,
                    "input_tokens_details": {"cached_tokens": %d, "cache_write_tokens": %d},
                    "output_tokens": %d,
                    "total_tokens": %d
                  }
                }
                """.formatted(text, input, cached, cacheWrite, output, total));
        return response;
    }
}
