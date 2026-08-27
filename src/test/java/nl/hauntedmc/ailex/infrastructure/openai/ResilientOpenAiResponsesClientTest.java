package nl.hauntedmc.ailex.infrastructure.openai;

import nl.hauntedmc.ailex.infrastructure.openai.ResilientOpenAiResponsesClient.FailureKind;
import nl.hauntedmc.ailex.infrastructure.openai.ResilientOpenAiResponsesClient.OpenAiUnavailableException;
import nl.hauntedmc.ailex.util.LoggerUtils;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilientOpenAiResponsesClientTest {

    @Test
    void failedFastCallThrowsInsteadOfReturningFallbackAsAssistantText() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> rateLimited = mockStringResponse(
                429, "{\"error\":{\"message\":\"rate limited\"}}"
        );
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(rateLimited);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            ResilientOpenAiResponsesClient client = new ResilientOpenAiResponsesClient(
                    "key", "gpt-5.6-terra", httpClient
            );

            OpenAiUnavailableException exception = assertThrows(
                    OpenAiUnavailableException.class,
                    () -> client.getChatResponse("system", "hello")
            );

            assertEquals(FailureKind.RATE_LIMITED, exception.failureKind());
            assertEquals(429, exception.httpStatus());
            verify(httpClient, times(1)).send(any(HttpRequest.class), anyStringBodyHandler());
        }
    }

    @Test
    void resultApiCannotBypassReliabilityBoundary() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> unavailable = mockStringResponse(503, "{\"error\":{\"message\":\"busy\"}}");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(unavailable);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            ResilientOpenAiResponsesClient client = new ResilientOpenAiResponsesClient(
                    "key", "gpt-5.6-terra", httpClient, false
            );

            OpenAiUnavailableException exception = assertThrows(
                    OpenAiUnavailableException.class,
                    () -> client.getChatResult(
                            "system", "hello", OpenAiResponsesClient.RequestOptions.defaults()
                    )
            );

            assertEquals(FailureKind.UPSTREAM, exception.failureKind());
            verify(httpClient, times(1)).send(any(HttpRequest.class), anyStringBodyHandler());
        }
    }

    @Test
    void normalizedEmptySuccessCannotTurnIntoFallbackAssistantText() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> emptyQuotedText = mockStringResponse(200, successfulResponse("\\\"\\\""));
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(emptyQuotedText);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            ResilientOpenAiResponsesClient client = new ResilientOpenAiResponsesClient(
                    "key", "gpt-5.6-terra", httpClient, false
            );

            OpenAiUnavailableException exception = assertThrows(
                    OpenAiUnavailableException.class,
                    () -> client.getChatResponse("system", "hello")
            );

            assertEquals(FailureKind.INVALID_RESPONSE, exception.failureKind());
            assertEquals(200, exception.httpStatus());
            verify(httpClient, times(1)).send(any(HttpRequest.class), anyStringBodyHandler());
        }
    }

    @Test
    void transientUpstreamFailureRetriesOnceThenReturnsRealAnswer() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> unavailable = mockStringResponse(503, "{\"error\":{\"message\":\"busy\"}}");
        HttpResponse<String> success = mockStringResponse(200, successfulResponse("Hoi!"));
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(unavailable, success);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            ResilientOpenAiResponsesClient client = new ResilientOpenAiResponsesClient(
                    "key", "gpt-5.6-terra", httpClient
            );

            assertEquals("Hoi!", client.getChatResponse("system", "hello"));
            assertTrue(client.usageStatus().contains("provider_retries=1"));
            assertTrue(client.usageStatus().contains("provider_circuit=closed"));
            verify(httpClient, times(2)).send(any(HttpRequest.class), anyStringBodyHandler());
        }
    }

    @Test
    void transportFailureThrowsAndCannotBecomeConversationText() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenThrow(new IOException("offline"));

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            ResilientOpenAiResponsesClient client = new ResilientOpenAiResponsesClient(
                    "key", "gpt-5.6-terra", httpClient
            );

            OpenAiUnavailableException exception = assertThrows(
                    OpenAiUnavailableException.class,
                    () -> client.getChatResponse("system", "hello")
            );

            assertEquals(FailureKind.TRANSPORT, exception.failureKind());
            verify(httpClient, times(1)).send(any(HttpRequest.class), anyStringBodyHandler());
        }
    }

    @Test
    void providerCircuitOpensOnlyAfterRepeatedRealProviderFailures() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> unavailable = mockStringResponse(503, "{\"error\":{\"message\":\"busy\"}}");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(unavailable);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            ResilientOpenAiResponsesClient client = new ResilientOpenAiResponsesClient(
                    "key", "gpt-5.6-terra", httpClient, false
            );

            for (int attempt = 0; attempt < 5; attempt++) {
                OpenAiUnavailableException exception = assertThrows(
                        OpenAiUnavailableException.class,
                        () -> client.getChatResponse("system", "hello")
                );
                assertEquals(FailureKind.UPSTREAM, exception.failureKind());
            }

            OpenAiUnavailableException blocked = assertThrows(
                    OpenAiUnavailableException.class,
                    () -> client.getChatResponse("system", "hello")
            );
            assertEquals(FailureKind.CIRCUIT_OPEN, blocked.failureKind());
            assertTrue(client.usageStatus().contains("provider_circuit=open"));
            verify(httpClient, times(5)).send(any(HttpRequest.class), anyStringBodyHandler());
        }
    }

    @Test
    void invalidSuccessfulResponsesDoNotPoisonProviderCircuit() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> invalid = mockStringResponse(200, "{\"output\":[]}");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(invalid);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            ResilientOpenAiResponsesClient client = new ResilientOpenAiResponsesClient(
                    "key", "gpt-5.6-terra", httpClient, false
            );

            for (int attempt = 0; attempt < 7; attempt++) {
                OpenAiUnavailableException exception = assertThrows(
                        OpenAiUnavailableException.class,
                        () -> client.getChatResponse("system", "hello")
                );
                assertEquals(FailureKind.INVALID_RESPONSE, exception.failureKind());
            }

            assertTrue(client.usageStatus().contains("provider_circuit=closed"));
            verify(httpClient, times(7)).send(any(HttpRequest.class), anyStringBodyHandler());
        }
    }

    @Test
    void authenticationFailuresDoNotPoisonProviderCircuit() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> unauthorized = mockStringResponse(
                401, "{\"error\":{\"message\":\"bad key\"}}"
        );
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(unauthorized);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            ResilientOpenAiResponsesClient client = new ResilientOpenAiResponsesClient(
                    "key", "gpt-5.6-terra", httpClient, false
            );

            for (int attempt = 0; attempt < 7; attempt++) {
                OpenAiUnavailableException exception = assertThrows(
                        OpenAiUnavailableException.class,
                        () -> client.getChatResponse("system", "hello")
                );
                assertEquals(FailureKind.AUTHENTICATION, exception.failureKind());
            }

            assertTrue(client.usageStatus().contains("provider_circuit=closed"));
            verify(httpClient, times(7)).send(any(HttpRequest.class), anyStringBodyHandler());
        }
    }

    @Test
    void disabledProviderCircuitNeverRejectsRequests() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> unavailable = mockStringResponse(503, "{\"error\":{\"message\":\"busy\"}}");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(unavailable);

        try (MockedStatic<LoggerUtils> ignored = mockStatic(LoggerUtils.class)) {
            ResilientOpenAiResponsesClient client = new ResilientOpenAiResponsesClient(
                    "key", "gpt-5.6-terra", httpClient, false, false
            );

            for (int attempt = 0; attempt < 7; attempt++) {
                OpenAiUnavailableException exception = assertThrows(
                        OpenAiUnavailableException.class,
                        () -> client.getChatResponse("system", "hello")
                );
                assertEquals(FailureKind.UPSTREAM, exception.failureKind());
            }

            assertTrue(client.usageStatus().contains("provider_circuit=disabled"));
            verify(httpClient, times(7)).send(any(HttpRequest.class), anyStringBodyHandler());
        }
    }

    private static String successfulResponse(String text) {
        return "{\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"output_text\",\"text\":\"" + text + "\"}]}]}";
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse.BodyHandler<String> anyStringBodyHandler() {
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
