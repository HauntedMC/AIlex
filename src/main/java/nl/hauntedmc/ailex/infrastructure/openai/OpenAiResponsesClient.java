package nl.hauntedmc.ailex.infrastructure.openai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.hauntedmc.ailex.util.LoggerUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Responses API client with privacy controls, role-aware dialogue replay, cache routing and usage accounting. */
public class OpenAiResponsesClient {

    static final String OPENAI_RESPONSES_API_URL = "https://api.openai.com/v1/responses";
    static final String FALLBACK_RESPONSE = "Ik kan nu even niet reageren.";
    static final String SAFETY_SYSTEM_PROMPT = "You are a Minecraft chat NPC for a general audience including minors. "
            + "Never generate sexual, erotic, pornographic, fetish, explicit, or 18+ content. "
            + "Never produce grooming, exploitative, or suggestive content. "
            + "If asked for inappropriate content, refuse briefly and redirect to a safe topic. "
            + "Keep all replies age-appropriate and safe-for-work.";

    private static final int MAX_CHAT_RESPONSE_LENGTH = 1_200;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 120;
    private static final String DEFAULT_REASONING_EFFORT = "low";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String SYSTEM_RESPONSE_INSTRUCTION = "Return only player-facing Minecraft chat text. "
            + "Be concise but complete; do not omit a useful explanation merely to force one sentence. "
            + "Do not use markdown, quotes around the whole reply, protocol fields, or speaker labels.";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final boolean safetyEnabled;
    private final String safetySystemPrompt;
    private final int maxOutputTokens;
    private final String reasoningEffort;
    private final boolean storeResponses;
    private final Duration requestTimeout;
    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicLong successfulRequestCount = new AtomicLong();
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong cachedInputTokens = new AtomicLong();
    private final AtomicLong cacheWriteTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong totalTokens = new AtomicLong();

    public OpenAiResponsesClient(JavaPlugin plugin) {
        this(plugin.getConfig());
    }

    OpenAiResponsesClient(FileConfiguration config) {
        this(
                config.getString("openai.api_key", ""),
                config.getString("openai.model", "gpt-5.6-terra"),
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                config.getBoolean("openai.safety.enabled", true),
                config.getString("openai.safety.system_prompt", SAFETY_SYSTEM_PROMPT),
                Math.clamp(config.getInt("openai.max_output_tokens", DEFAULT_MAX_OUTPUT_TOKENS), 16, 1_200),
                config.getString("openai.reasoning_effort", DEFAULT_REASONING_EFFORT),
                config.getBoolean("openai.store_responses", false),
                Duration.ofSeconds(Math.clamp(config.getInt("openai.request_timeout_seconds", 20), 3, 60))
        );
    }

    OpenAiResponsesClient(String apiKey, String model, HttpClient httpClient) {
        this(apiKey, model, httpClient, true, SAFETY_SYSTEM_PROMPT);
    }

    OpenAiResponsesClient(
            String apiKey, String model, HttpClient httpClient, boolean safetyEnabled, String safetySystemPrompt
    ) {
        this(apiKey, model, httpClient, safetyEnabled, safetySystemPrompt,
                DEFAULT_MAX_OUTPUT_TOKENS, DEFAULT_REASONING_EFFORT, false, DEFAULT_REQUEST_TIMEOUT);
    }

    OpenAiResponsesClient(
            String apiKey,
            String model,
            HttpClient httpClient,
            boolean safetyEnabled,
            String safetySystemPrompt,
            int maxOutputTokens,
            String reasoningEffort,
            boolean storeResponses
    ) {
        this(apiKey, model, httpClient, safetyEnabled, safetySystemPrompt,
                maxOutputTokens, reasoningEffort, storeResponses, DEFAULT_REQUEST_TIMEOUT);
    }

    OpenAiResponsesClient(
            String apiKey,
            String model,
            HttpClient httpClient,
            boolean safetyEnabled,
            String safetySystemPrompt,
            int maxOutputTokens,
            String reasoningEffort,
            boolean storeResponses,
            Duration requestTimeout
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.safetyEnabled = safetyEnabled;
        this.safetySystemPrompt = safetySystemPrompt == null ? "" : safetySystemPrompt.trim();
        this.maxOutputTokens = Math.clamp(maxOutputTokens, 16, 1_200);
        this.reasoningEffort = sanitizeReasoningEffort(reasoningEffort);
        this.storeResponses = storeResponses;
        this.requestTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;

        LoggerUtils.logInfo("Initialized OpenAI client with model: " + (this.model.isEmpty() ? "<empty>" : this.model));
        if (!isConfigured()) {
            LoggerUtils.logWarning("OpenAI integration is disabled: set both openai.api_key and openai.model in config.yml.");
        } else if (this.safetyEnabled) {
            LoggerUtils.logInfo("OpenAI safety prompt guard is enabled.");
        }
    }

    public String getChatResponse(String prompt) {
        return getChatResponse("", prompt);
    }

    public String getChatResponse(String systemPrompt, String userPrompt) {
        return getChatResponse(systemPrompt, userPrompt, RequestOptions.defaults());
    }

    /** Text convenience API. Prefer {@link #getChatResult} when per-request usage telemetry is needed. */
    public String getChatResponse(String systemPrompt, String userPrompt, RequestOptions options) {
        return getChatResult(systemPrompt, userPrompt, options).text();
    }

    /** Plain-text response plus server-reported token/cache usage. */
    public ResponseResult getChatResult(String systemPrompt, String userPrompt, RequestOptions options) {
        if (userPrompt == null || userPrompt.isBlank() || !isConfigured(options)) {
            return ResponseResult.failure(FALLBACK_RESPONSE, 0);
        }
        return execute(systemPrompt, userPrompt, null, SYSTEM_RESPONSE_INSTRUCTION, options, true);
    }

    public String getStructuredChatResponse(String systemPrompt, String userPrompt, JsonObject responseFormat) {
        return getStructuredChatResponse(systemPrompt, userPrompt, responseFormat, RequestOptions.defaults());
    }

    /** Structured text convenience API. Prefer {@link #getStructuredChatResult} for per-request usage. */
    public String getStructuredChatResponse(
            String systemPrompt, String userPrompt, JsonObject responseFormat, RequestOptions options
    ) {
        return getStructuredChatResult(systemPrompt, userPrompt, responseFormat, options).text();
    }

    /** Schema-constrained response plus server-reported token/cache usage. */
    public ResponseResult getStructuredChatResult(
            String systemPrompt, String userPrompt, JsonObject responseFormat, RequestOptions options
    ) {
        if (userPrompt == null || userPrompt.isBlank() || responseFormat == null || !isConfigured(options)) {
            return ResponseResult.failure("", 0);
        }
        return execute(
                systemPrompt,
                userPrompt,
                responseFormat,
                "Return only a JSON object that conforms exactly to the supplied response schema.",
                options,
                false
        );
    }

    /** Cumulative usage for this client instance, including calls made through String-returning APIs. */
    public UsageSnapshot usageSnapshot() {
        Usage usage = new Usage(
                inputTokens.get(), cachedInputTokens.get(), cacheWriteTokens.get(), outputTokens.get(), totalTokens.get()
        );
        return new UsageSnapshot(requestCount.get(), successfulRequestCount.get(), usage);
    }

    /** Compact operator-facing representation used by /ailex diagnostics. */
    public String usageStatus() {
        UsageSnapshot snapshot = usageSnapshot();
        return "calls=" + snapshot.requests()
                + ", success=" + snapshot.successfulRequests()
                + ", input=" + snapshot.usage().inputTokens()
                + ", cached=" + snapshot.usage().cachedInputTokens()
                + ", cache_write=" + snapshot.usage().cacheWriteTokens()
                + ", output=" + snapshot.usage().outputTokens()
                + ", cache_hit=" + String.format(Locale.ROOT, "%.1f%%", snapshot.usage().cacheHitRatio() * 100.0D);
    }

    private ResponseResult execute(
            String systemPrompt,
            String userPrompt,
            JsonObject responseFormat,
            String responseInstruction,
            RequestOptions options,
            boolean normalize
    ) {
        requestCount.incrementAndGet();
        HttpRequest request = createHttpRequest(systemPrompt, userPrompt, responseFormat, responseInstruction, options);
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            Usage usage = extractUsage(response.body());
            recordUsage(usage);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String error = extractErrorMessage(response.body());
                LoggerUtils.logWarning(error.isBlank()
                        ? "OpenAI request failed with status: " + response.statusCode()
                        : "OpenAI request failed with status " + response.statusCode() + ": " + error);
                return ResponseResult.failure(normalize ? FALLBACK_RESPONSE : "", response.statusCode(), usage);
            }
            String text = extractAssistantText(response.body());
            if (text.isBlank()) {
                LoggerUtils.logWarning("OpenAI response did not contain assistant text.");
                return ResponseResult.failure(normalize ? FALLBACK_RESPONSE : "", response.statusCode(), usage);
            }
            successfulRequestCount.incrementAndGet();
            return new ResponseResult(normalize ? normalizeResponse(text) : text.trim(), usage, response.statusCode(), true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LoggerUtils.logError("OpenAI request interrupted: " + exception.getMessage());
            return ResponseResult.failure(normalize ? FALLBACK_RESPONSE : "", 0);
        } catch (IOException exception) {
            LoggerUtils.logError("OpenAI request failed: " + exception.getMessage());
            return ResponseResult.failure(normalize ? FALLBACK_RESPONSE : "", 0);
        } catch (RuntimeException exception) {
            LoggerUtils.logError("OpenAI response parsing failed: " + exception.getMessage());
            return ResponseResult.failure(normalize ? FALLBACK_RESPONSE : "", 0);
        }
    }

    private void recordUsage(Usage usage) {
        if (usage == null) {
            return;
        }
        inputTokens.addAndGet(usage.inputTokens());
        cachedInputTokens.addAndGet(usage.cachedInputTokens());
        cacheWriteTokens.addAndGet(usage.cacheWriteTokens());
        outputTokens.addAndGet(usage.outputTokens());
        totalTokens.addAndGet(usage.totalTokens());
    }

    private boolean isConfigured() {
        return !apiKey.isBlank() && !model.isBlank();
    }

    private boolean isConfigured(RequestOptions options) {
        return !apiKey.isBlank() && !(options == null ? model : options.model(model)).isBlank();
    }

    HttpRequest createHttpRequest(String systemPrompt, String prompt) {
        return createHttpRequest(systemPrompt, prompt, null, SYSTEM_RESPONSE_INSTRUCTION, RequestOptions.defaults());
    }

    HttpRequest createHttpRequest(String systemPrompt, String prompt, RequestOptions options) {
        return createHttpRequest(systemPrompt, prompt, null, SYSTEM_RESPONSE_INSTRUCTION, options);
    }

    private HttpRequest createHttpRequest(
            String systemPrompt,
            String prompt,
            JsonObject responseFormat,
            String responseInstruction,
            RequestOptions options
    ) {
        RequestOptions effective = options == null ? RequestOptions.defaults() : options;
        return HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_RESPONSES_API_URL))
                .timeout(effective.timeout(requestTimeout))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        createRequestBody(systemPrompt, prompt, responseFormat, responseInstruction, effective),
                        StandardCharsets.UTF_8
                ))
                .build();
    }

    String createRequestBody(String prompt) {
        return createRequestBody("", prompt);
    }

    String createRequestBody(String systemPrompt, String prompt) {
        return createRequestBody(systemPrompt, prompt, null, SYSTEM_RESPONSE_INSTRUCTION, RequestOptions.defaults());
    }

    String createRequestBody(String systemPrompt, String prompt, JsonObject responseFormat, RequestOptions options) {
        return createRequestBody(systemPrompt, prompt, responseFormat, SYSTEM_RESPONSE_INSTRUCTION, options);
    }

    private String createRequestBody(
            String systemPrompt,
            String prompt,
            JsonObject responseFormat,
            String responseInstruction,
            RequestOptions options
    ) {
        RequestOptions effective = options == null ? RequestOptions.defaults() : options;
        JsonObject payload = new JsonObject();
        payload.addProperty("model", effective.model(model));
        payload.addProperty("max_output_tokens", effective.maxOutputTokens(maxOutputTokens));
        payload.addProperty("store", storeResponses);
        String effort = effective.reasoningEffort(reasoningEffort);
        if (!effort.isBlank()) {
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", effort);
            payload.add("reasoning", reasoning);
        }
        if (!effective.safetyIdentifier().isBlank()) {
            payload.addProperty("safety_identifier", effective.safetyIdentifier());
        }
        if (!effective.promptCacheKey().isBlank()) {
            payload.addProperty("prompt_cache_key", effective.promptCacheKey());
        }

        String instructions = combineInstructions(systemPrompt, responseInstruction);
        if (!instructions.isBlank()) {
            payload.addProperty("instructions", instructions);
        }

        ResponsesConversationInput.Parsed conversation = ResponsesConversationInput.parse(prompt);
        JsonArray input = new JsonArray();
        for (ResponsesConversationInput.RoleMessage previous : conversation.history()) {
            if (!previous.text().isBlank()) {
                input.add(createInputMessage(previous.role(), previous.text()));
            }
        }
        input.add(createInputMessage("user", conversation.currentPrompt()));
        payload.add("input", input);

        if (responseFormat != null) {
            JsonObject text = new JsonObject();
            text.add("format", responseFormat.deepCopy());
            if (!effective.verbosity().isBlank()) {
                text.addProperty("verbosity", effective.verbosity());
            }
            payload.add("text", text);
        }
        return payload.toString();
    }

    /** Per-request execution profile. Stable prompt-cache keys should identify the static instruction prefix. */
    public record RequestOptions(
            String model,
            int maxOutputTokens,
            String reasoningEffort,
            Duration requestTimeout,
            String safetyIdentifier,
            String promptCacheKey,
            String verbosity
    ) {
        public RequestOptions {
            safetyIdentifier = sanitizeIdentifier(safetyIdentifier, 64);
            promptCacheKey = sanitizeIdentifier(promptCacheKey, 64);
            verbosity = sanitizeVerbosity(verbosity);
        }

        public static RequestOptions defaults() {
            return new RequestOptions("", 0, "", null, "", "", "");
        }

        private String model(String fallback) {
            return model == null || model.isBlank() ? fallback : model.trim();
        }

        private int maxOutputTokens(int fallback) {
            return maxOutputTokens <= 0 ? fallback : Math.clamp(maxOutputTokens, 16, 4096);
        }

        private String reasoningEffort(String fallback) {
            String candidate = reasoningEffort == null || reasoningEffort.isBlank() ? fallback : reasoningEffort;
            String normalized = candidate == null ? "" : candidate.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "none", "low", "medium", "high", "xhigh", "max" -> normalized;
                default -> "";
            };
        }

        private Duration timeout(Duration fallback) {
            if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
                return fallback;
            }
            return requestTimeout.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : requestTimeout;
        }

        private static String sanitizeIdentifier(String value, int maxLength) {
            if (value == null) {
                return "";
            }
            String normalized = value.trim();
            return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
        }

        private static String sanitizeVerbosity(String value) {
            if (value == null) {
                return "";
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "low", "medium", "high" -> value.trim().toLowerCase(Locale.ROOT);
                default -> "";
            };
        }
    }

    /** Exact server-reported usage fields used for cost and prompt-cache observability. */
    public record Usage(long inputTokens, long cachedInputTokens, long cacheWriteTokens, long outputTokens, long totalTokens) {
        public static Usage empty() {
            return new Usage(0L, 0L, 0L, 0L, 0L);
        }

        public Usage plus(Usage other) {
            if (other == null) {
                return this;
            }
            return new Usage(
                    inputTokens + other.inputTokens,
                    cachedInputTokens + other.cachedInputTokens,
                    cacheWriteTokens + other.cacheWriteTokens,
                    outputTokens + other.outputTokens,
                    totalTokens + other.totalTokens
            );
        }

        public double cacheHitRatio() {
            return inputTokens <= 0L ? 0.0D : Math.clamp((double) cachedInputTokens / inputTokens, 0.0D, 1.0D);
        }
    }

    /** Cumulative counters for one client lifecycle. Reloading the OpenAI client intentionally starts a new window. */
    public record UsageSnapshot(long requests, long successfulRequests, Usage usage) {
        public UsageSnapshot {
            usage = usage == null ? Usage.empty() : usage;
        }
    }

    /** One Responses API result with player-safe failure text and an explicit success signal. */
    public record ResponseResult(String text, Usage usage, int httpStatus, boolean success) {
        public ResponseResult {
            text = text == null ? "" : text;
            usage = usage == null ? Usage.empty() : usage;
        }

        public static ResponseResult failure(String text, int status) {
            return failure(text, status, Usage.empty());
        }

        public static ResponseResult failure(String text, int status, Usage usage) {
            return new ResponseResult(text, usage, status, false);
        }
    }

    private Usage extractUsage(String responseBody) {
        JsonObject root = parseJsonObject(responseBody);
        if (root == null || !root.has("usage") || !root.get("usage").isJsonObject()) {
            return Usage.empty();
        }
        JsonObject usage = root.getAsJsonObject("usage");
        JsonObject inputDetails = object(usage, "input_tokens_details");
        long input = longValue(usage, "input_tokens");
        long output = longValue(usage, "output_tokens");
        long total = longValue(usage, "total_tokens");
        long cached = firstPositive(
                longValue(inputDetails, "cached_tokens"),
                longValue(usage, "cached_input_tokens")
        );
        long cacheWrite = firstPositive(
                longValue(inputDetails, "cache_write_tokens"),
                longValue(inputDetails, "cache_creation_tokens"),
                longValue(usage, "cache_write_tokens"),
                longValue(usage, "cache_creation_input_tokens")
        );
        return new Usage(input, cached, cacheWrite, output, total);
    }

    private long firstPositive(long... values) {
        for (long value : values) {
            if (value > 0L) {
                return value;
            }
        }
        return 0L;
    }

    private long longValue(JsonObject object, String property) {
        if (object == null || !object.has(property) || !object.get(property).isJsonPrimitive()) {
            return 0L;
        }
        try {
            return Math.max(0L, object.get(property).getAsLong());
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private JsonObject object(JsonObject parent, String property) {
        return parent != null && parent.has(property) && parent.get(property).isJsonObject()
                ? parent.getAsJsonObject(property) : null;
    }

    private String sanitizeReasoningEffort(String effort) {
        if (effort == null) {
            return "";
        }
        return switch (effort.trim().toLowerCase(Locale.ROOT)) {
            case "none", "low", "medium", "high", "xhigh", "max" -> effort.trim().toLowerCase(Locale.ROOT);
            default -> "";
        };
    }

    private String combineInstructions(String systemPrompt, String responseInstruction) {
        StringBuilder instructions = new StringBuilder();
        appendInstruction(instructions, safetyEnabled ? safetySystemPrompt : "");
        appendInstruction(instructions, systemPrompt);
        appendInstruction(instructions, responseInstruction);
        return instructions.toString();
    }

    private void appendInstruction(StringBuilder output, String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return;
        }
        if (!output.isEmpty()) {
            output.append("\n\n");
        }
        output.append(instruction.trim());
    }

    private JsonObject createInputMessage(String role, String text) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "input_text");
        textContent.addProperty("text", text);
        content.add(textContent);
        message.add("content", content);
        return message;
    }

    private String extractAssistantText(String responseBody) {
        JsonObject root = parseJsonObject(responseBody);
        if (root == null) {
            return "";
        }
        String outputArrayText = extractFromOutputArray(root.getAsJsonArray("output"));
        if (!outputArrayText.isBlank()) {
            return outputArrayText;
        }
        return getString(root, "output_text");
    }

    private String extractErrorMessage(String responseBody) {
        JsonObject root = parseJsonObject(responseBody);
        if (root == null || !root.has("error") || root.get("error").isJsonNull()) {
            return "";
        }
        JsonElement error = root.get("error");
        if (error.isJsonObject()) {
            return getString(error.getAsJsonObject(), "message");
        }
        return error.isJsonPrimitive() ? error.getAsString() : "";
    }

    private JsonObject parseJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(value);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String extractFromOutputArray(JsonArray outputArray) {
        if (outputArray == null || outputArray.isEmpty()) {
            return "";
        }
        StringBuilder assistantText = new StringBuilder();
        StringBuilder directOutputText = new StringBuilder();
        for (JsonElement outputItem : outputArray) {
            if (!outputItem.isJsonObject()) {
                continue;
            }
            JsonObject output = outputItem.getAsJsonObject();
            String outputType = getString(output, "type");
            if ("output_text".equals(outputType)) {
                appendText(directOutputText, getString(output, "text"));
                continue;
            }
            if (!"message".equals(outputType)) {
                continue;
            }
            String role = getString(output, "role");
            if (!role.isBlank() && !"assistant".equalsIgnoreCase(role)) {
                continue;
            }
            JsonArray content = output.getAsJsonArray("content");
            if (content == null) {
                continue;
            }
            for (JsonElement item : content) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject part = item.getAsJsonObject();
                String type = getString(part, "type");
                if ("output_text".equals(type) || type.isBlank()) {
                    appendText(assistantText, getString(part, "text"));
                } else if ("refusal".equals(type)) {
                    String refusal = getString(part, "refusal");
                    appendText(assistantText, refusal.isBlank() ? getString(part, "text") : refusal);
                }
            }
        }
        return assistantText.isEmpty() ? directOutputText.toString() : assistantText.toString();
    }

    private String getString(JsonObject object, String property) {
        if (object == null || !object.has(property) || object.get(property).isJsonNull()
                || !object.get(property).isJsonPrimitive()) {
            return "";
        }
        return object.get(property).getAsString().trim();
    }

    private void appendText(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(text.trim());
    }

    private String normalizeResponse(String response) {
        String normalized = response.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        normalized = normalized.lines()
                .map(line -> line.replaceAll("\\h+", " ").trim())
                .filter(line -> !line.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
        if (normalized.isEmpty()) {
            return FALLBACK_RESPONSE;
        }
        return normalized.length() > MAX_CHAT_RESPONSE_LENGTH
                ? normalized.substring(0, MAX_CHAT_RESPONSE_LENGTH - 3).trim() + "..." : normalized;
    }
}
