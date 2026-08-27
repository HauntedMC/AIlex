package nl.hauntedmc.ailex.infrastructure.openai;

import org.bukkit.plugin.java.JavaPlugin;

import java.net.http.HttpClient;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Production reliability boundary around the raw Responses API transport.
 *
 * <p>The base client deliberately exposes {@link ResponseResult} for callers that need to inspect provider outcomes.
 * Player-facing code must never mistake its fallback text for a successful model answer, so this wrapper converts every
 * unsuccessful transport result into an exception before the assistant can validate, remember or replay that text.</p>
 */
public final class ResilientOpenAiResponsesClient extends OpenAiResponsesClient {

    private static final int PROVIDER_FAILURE_THRESHOLD = 5;
    private static final long PROVIDER_COOLDOWN_MILLIS = 15_000L;
    private static final long MIN_RETRY_DELAY_MILLIS = 75L;
    private static final long MAX_RETRY_DELAY_MILLIS = 175L;

    private final boolean retryTransientFailures;
    private final boolean providerCircuitEnabled;
    private final ProviderCircuitBreaker providerCircuitBreaker = new ProviderCircuitBreaker();
    private final Map<FailureKind, AtomicLong> failureCounts = new EnumMap<>(FailureKind.class);
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong circuitRejections = new AtomicLong();
    private volatile FailureSnapshot lastFailure = FailureSnapshot.none();

    public ResilientOpenAiResponsesClient(JavaPlugin plugin) {
        super(plugin);
        retryTransientFailures = true;
        providerCircuitEnabled = plugin.getConfig().getBoolean(
                "openai.assistant.reliability.circuit_breaker_enabled", true
        );
        initializeFailureCounters();
    }

    ResilientOpenAiResponsesClient(String apiKey, String model, HttpClient httpClient) {
        this(apiKey, model, httpClient, true, true);
    }

    ResilientOpenAiResponsesClient(
            String apiKey,
            String model,
            HttpClient httpClient,
            boolean retryTransientFailures
    ) {
        this(apiKey, model, httpClient, retryTransientFailures, true);
    }

    ResilientOpenAiResponsesClient(
            String apiKey,
            String model,
            HttpClient httpClient,
            boolean retryTransientFailures,
            boolean providerCircuitEnabled
    ) {
        super(apiKey, model, httpClient);
        this.retryTransientFailures = retryTransientFailures;
        this.providerCircuitEnabled = providerCircuitEnabled;
        initializeFailureCounters();
    }

    @Override
    public String getChatResponse(String systemPrompt, String userPrompt, RequestOptions options) {
        return executeReliably(
                "chat",
                () -> getChatResult(systemPrompt, userPrompt, options)
        ).text();
    }

    @Override
    public String getStructuredChatResponse(
            String systemPrompt,
            String userPrompt,
            com.google.gson.JsonObject responseFormat,
            RequestOptions options
    ) {
        return executeReliably(
                "structured-chat",
                () -> getStructuredChatResult(systemPrompt, userPrompt, responseFormat, options)
        ).text();
    }

    @Override
    public String usageStatus() {
        FailureSnapshot failure = lastFailure;
        return super.usageStatus()
                + ", provider_failures=" + totalFailures()
                + ", provider_retries=" + retries.get()
                + ", provider_circuit_rejections=" + circuitRejections.get()
                + ", provider_circuit=" + (providerCircuitEnabled ? providerCircuitBreaker.state() : "disabled")
                + ", last_failure=" + failure.kind().name().toLowerCase(Locale.ROOT)
                + (failure.httpStatus() > 0 ? "(" + failure.httpStatus() + ")" : "");
    }

    private ResponseResult executeReliably(String operation, Supplier<ResponseResult> request) {
        if (providerCircuitEnabled && !providerCircuitBreaker.allowsRequest()) {
            circuitRejections.incrementAndGet();
            throw new OpenAiUnavailableException(operation, FailureKind.CIRCUIT_OPEN, 0);
        }

        ResponseResult result = request.get();
        if (result.success()) {
            providerCircuitBreaker.recordReachableSuccess();
            return result;
        }

        FailureKind kind = classify(result);
        if (retryTransientFailures && kind.retryable()) {
            delayBeforeRetry();
            retries.incrementAndGet();
            result = request.get();
            if (result.success()) {
                providerCircuitBreaker.recordReachableSuccess();
                return result;
            }
            kind = classify(result);
        }

        recordFailure(kind, result.httpStatus());
        throw new OpenAiUnavailableException(operation, kind, result.httpStatus());
    }

    private void delayBeforeRetry() {
        long delay = ThreadLocalRandom.current().nextLong(MIN_RETRY_DELAY_MILLIS, MAX_RETRY_DELAY_MILLIS + 1L);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(FailureKind.INTERRUPTED, 0);
            throw new OpenAiUnavailableException("retry", FailureKind.INTERRUPTED, 0);
        }
    }

    private FailureKind classify(ResponseResult result) {
        int status = result == null ? 0 : result.httpStatus();
        if (status == 401 || status == 403) {
            return FailureKind.AUTHENTICATION;
        }
        if (status == 408) {
            return FailureKind.TIMEOUT;
        }
        if (status == 429) {
            return FailureKind.RATE_LIMITED;
        }
        if (status >= 500 && status <= 599) {
            return FailureKind.UPSTREAM;
        }
        if (status >= 400 && status <= 499) {
            return FailureKind.REQUEST_REJECTED;
        }
        if (status >= 200 && status <= 299) {
            return FailureKind.INVALID_RESPONSE;
        }
        if (status == 0) {
            return FailureKind.TRANSPORT;
        }
        return FailureKind.UNKNOWN;
    }

    private void recordFailure(FailureKind kind, int status) {
        FailureKind effective = kind == null ? FailureKind.UNKNOWN : kind;
        failureCounts.get(effective).incrementAndGet();
        lastFailure = new FailureSnapshot(effective, status, System.currentTimeMillis());
        if (effective.affectsCircuit()) {
            if (providerCircuitEnabled) {
                providerCircuitBreaker.recordProviderFailure();
            }
        } else {
            // A concrete non-provider response proves the endpoint is reachable; it must not preserve an old outage.
            providerCircuitBreaker.recordReachableOutcome();
        }
    }

    private long totalFailures() {
        return failureCounts.values().stream().mapToLong(AtomicLong::get).sum();
    }

    private void initializeFailureCounters() {
        for (FailureKind kind : FailureKind.values()) {
            failureCounts.put(kind, new AtomicLong());
        }
    }

    public enum FailureKind {
        NONE(false, false),
        RATE_LIMITED(false, true),
        UPSTREAM(true, true),
        INVALID_RESPONSE(true, true),
        TIMEOUT(false, true),
        TRANSPORT(false, true),
        AUTHENTICATION(false, false),
        REQUEST_REJECTED(false, false),
        INTERRUPTED(false, false),
        CIRCUIT_OPEN(false, false),
        UNKNOWN(false, true);

        private final boolean retryable;
        private final boolean affectsCircuit;

        FailureKind(boolean retryable, boolean affectsCircuit) {
            this.retryable = retryable;
            this.affectsCircuit = affectsCircuit;
        }

        boolean retryable() {
            return retryable;
        }

        boolean affectsCircuit() {
            return affectsCircuit;
        }
    }

    public static final class OpenAiUnavailableException extends RuntimeException {
        private final FailureKind failureKind;
        private final int httpStatus;

        OpenAiUnavailableException(String operation, FailureKind failureKind, int httpStatus) {
            super(buildMessage(operation, failureKind, httpStatus));
            this.failureKind = failureKind == null ? FailureKind.UNKNOWN : failureKind;
            this.httpStatus = httpStatus;
        }

        public FailureKind failureKind() {
            return failureKind;
        }

        public int httpStatus() {
            return httpStatus;
        }

        private static String buildMessage(String operation, FailureKind failureKind, int status) {
            FailureKind effective = failureKind == null ? FailureKind.UNKNOWN : failureKind;
            return "OpenAI " + operation + " failed: " + effective.name().toLowerCase(Locale.ROOT)
                    + (status > 0 ? " (HTTP " + status + ")" : "");
        }
    }

    private record FailureSnapshot(FailureKind kind, int httpStatus, long occurredAtMillis) {
        private static FailureSnapshot none() {
            return new FailureSnapshot(FailureKind.NONE, 0, 0L);
        }
    }

    private final class ProviderCircuitBreaker {
        private int consecutiveProviderFailures;
        private long openUntilMillis;
        private boolean halfOpenProbeInFlight;

        synchronized boolean allowsRequest() {
            if (openUntilMillis == 0L) {
                return true;
            }
            long now = System.currentTimeMillis();
            if (now < openUntilMillis) {
                return false;
            }
            if (halfOpenProbeInFlight) {
                return false;
            }
            halfOpenProbeInFlight = true;
            return true;
        }

        synchronized void recordReachableSuccess() {
            consecutiveProviderFailures = 0;
            openUntilMillis = 0L;
            halfOpenProbeInFlight = false;
        }

        synchronized void recordReachableOutcome() {
            recordReachableSuccess();
        }

        synchronized void recordProviderFailure() {
            halfOpenProbeInFlight = false;
            consecutiveProviderFailures++;
            if (consecutiveProviderFailures >= PROVIDER_FAILURE_THRESHOLD) {
                openUntilMillis = System.currentTimeMillis() + PROVIDER_COOLDOWN_MILLIS;
            }
        }

        synchronized String state() {
            if (openUntilMillis == 0L) {
                return "closed";
            }
            if (System.currentTimeMillis() < openUntilMillis) {
                return "open";
            }
            return halfOpenProbeInFlight ? "half-open" : "probe-ready";
        }
    }
}
