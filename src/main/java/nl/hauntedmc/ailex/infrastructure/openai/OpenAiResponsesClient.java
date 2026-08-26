package nl.hauntedmc.ailex.infrastructure.openai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Locale;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Client for OpenAI chat responses.
 * This class sends requests to the OpenAI Responses API and extracts one assistant reply.
 */
public class OpenAiResponsesClient {

    static final String OPENAI_RESPONSES_API_URL = "https://api.openai.com/v1/responses";
    static final String FALLBACK_RESPONSE = "Ik kan nu even niet reageren.";
    static final String SAFETY_SYSTEM_PROMPT = "You are a Minecraft chat NPC for a general audience including minors. "
            + "Never generate sexual, erotic, pornographic, fetish, explicit, or 18+ content. "
            + "Never produce grooming, exploitative, or suggestive content. "
            + "If asked for inappropriate content, refuse briefly and redirect to a safe topic. "
            + "Keep all replies age-appropriate and safe-for-work.";

    private static final int MAX_CHAT_RESPONSE_LENGTH = 600;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 120;
    private static final String DEFAULT_REASONING_EFFORT = "low";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String SYSTEM_RESPONSE_INSTRUCTION = "Return exactly one short plain-text Minecraft chat response. "
            + "If you refuse, keep it brief and safe. Do not use markdown, quotes, or speaker labels.";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final boolean safetyEnabled;
    private final String safetySystemPrompt;
    private final int maxOutputTokens;
    private final String reasoningEffort;
    private final boolean storeResponses;
    private final Duration requestTimeout;

    /**
     * Constructor for the OpenAI Responses API client.
     * @param plugin - AIlex plugin instance
     */
    public OpenAiResponsesClient(JavaPlugin plugin) {
        this(plugin.getConfig());
    }

    OpenAiResponsesClient(FileConfiguration config) {
        this(
                config.getString("openai.api_key", ""),
                config.getString("openai.model", "gpt-5.4-mini"),
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build(),
                config.getBoolean("openai.safety.enabled", true),
                config.getString("openai.safety.system_prompt", SAFETY_SYSTEM_PROMPT),
                Math.clamp(config.getInt("openai.max_output_tokens", DEFAULT_MAX_OUTPUT_TOKENS), 16, 600),
                config.getString("openai.reasoning_effort", DEFAULT_REASONING_EFFORT),
                config.getBoolean("openai.store_responses", false),
                Duration.ofSeconds(Math.clamp(config.getInt("openai.request_timeout_seconds", 20), 3, 60))
        );
    }

    OpenAiResponsesClient(String apiKey, String model, HttpClient httpClient) {
        this(apiKey, model, httpClient, true, SAFETY_SYSTEM_PROMPT);
    }

    OpenAiResponsesClient(String apiKey, String model, HttpClient httpClient, boolean safetyEnabled, String safetySystemPrompt) {
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
        this.maxOutputTokens = Math.clamp(maxOutputTokens, 16, 600);
        this.reasoningEffort = sanitizeReasoningEffort(reasoningEffort);
        this.storeResponses = storeResponses;
        this.requestTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;

        LoggerUtils.logInfo("Initialized OpenAI client with model: " + (this.model.isEmpty() ? "<empty>" : this.model));
        if (!isConfigured()) {
            LoggerUtils.logWarning("OpenAI integration is disabled: set both openai.api_key and openai.model in config.yml.");
            return;
        }
        if (this.safetyEnabled) {
            LoggerUtils.logInfo("OpenAI safety prompt guard is enabled.");
        }
    }

    /**
     * Sends a request to the OpenAI API with the given prompt and returns the response.
     * @param prompt - the prompt to send to the API
     * @return the text response from the API
     */
    public String getChatResponse(String prompt) {
        return getChatResponse("", prompt);
    }

    /**
     * Sends a request to the OpenAI API using an NPC-specific system prompt and user prompt.
     * @param systemPrompt - optional system prompt for NPC persona/behavior
     * @param userPrompt - user prompt content
     * @return the text response from the API
     */
    public String getChatResponse(String systemPrompt, String userPrompt) {
        return getChatResponse(systemPrompt, userPrompt, RequestOptions.defaults());
    }

    /**
     * Sends a request with a bounded, per-call execution profile. This lets a caller choose a
     * model tier without rebuilding the client or weakening its configured privacy safeguards.
     */
    public String getChatResponse(String systemPrompt, String userPrompt, RequestOptions options) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return FALLBACK_RESPONSE;
        }

        if (!isConfigured(options)) {
            return FALLBACK_RESPONSE;
        }

        HttpRequest request = createHttpRequest(systemPrompt, userPrompt, null, SYSTEM_RESPONSE_INSTRUCTION, options);

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorMessage = extractErrorMessage(response.body());
                if (errorMessage.isEmpty()) {
                    LoggerUtils.logWarning("OpenAI request failed with status: " + response.statusCode());
                } else {
                    LoggerUtils.logWarning("OpenAI request failed with status " + response.statusCode() + ": " + errorMessage);
                }
                return FALLBACK_RESPONSE;
            }

            String parsedResponse = extractAssistantText(response.body());
            if (parsedResponse.isBlank()) {
                LoggerUtils.logWarning("OpenAI response did not contain assistant text.");
                return FALLBACK_RESPONSE;
            }

            return normalizeResponse(parsedResponse);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LoggerUtils.logError("OpenAI request interrupted: " + e.getMessage());
            return FALLBACK_RESPONSE;
        } catch (IOException e) {
            LoggerUtils.logError("OpenAI request failed: " + e.getMessage());
            return FALLBACK_RESPONSE;
        } catch (RuntimeException e) {
            LoggerUtils.logError("OpenAI response parsing failed: " + e.getMessage());
            return FALLBACK_RESPONSE;
        }
    }

    /**
     * Requests a schema-constrained response without applying chat-line normalization. Callers must
     * validate the returned JSON before exposing any part of it to a player.
     *
     * @param systemPrompt assistant policy and NPC persona
     * @param userPrompt request and trusted evidence
     * @param responseFormat a Responses API {@code text.format} object
     * @return raw model text, or an empty string when the request cannot be completed
     */
    public String getStructuredChatResponse(String systemPrompt, String userPrompt, JsonObject responseFormat) {
        return getStructuredChatResponse(systemPrompt, userPrompt, responseFormat, RequestOptions.defaults());
    }

    /** Requests a schema-constrained response with a bounded execution profile. */
    public String getStructuredChatResponse(
            String systemPrompt, String userPrompt, JsonObject responseFormat, RequestOptions options
    ) {
        if (userPrompt == null || userPrompt.isBlank() || responseFormat == null || !isConfigured(options)) {
            return "";
        }

        HttpRequest request = createHttpRequest(
                systemPrompt,
                userPrompt,
                responseFormat,
                "Return only a JSON object that conforms exactly to the supplied response schema.",
                options
        );
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LoggerUtils.logWarning("OpenAI structured request failed with status: " + response.statusCode());
                return "";
            }
            return extractAssistantText(response.body()).trim();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LoggerUtils.logError("OpenAI structured request interrupted: " + exception.getMessage());
            return "";
        } catch (IOException | RuntimeException exception) {
            LoggerUtils.logError("OpenAI structured request failed: " + exception.getMessage());
            return "";
        }
    }

    private boolean isConfigured() {
        return !apiKey.isBlank() && !model.isBlank();
    }

    private boolean isConfigured(RequestOptions options) {
        return !apiKey.isBlank() && !((options == null ? "" : options.model(model)).isBlank());
    }

    /**
     * Creates an HttpRequest with the given prompt.
     * @param prompt - the prompt to send to the API
     * @return the HttpRequest
     */
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
        RequestOptions effectiveOptions = options == null ? RequestOptions.defaults() : options;
        String inputJson = createRequestBody(systemPrompt, prompt, responseFormat, responseInstruction, effectiveOptions);
        Duration timeout = effectiveOptions.timeout(requestTimeout);

        return HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_RESPONSES_API_URL))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(inputJson, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Creates a JSON request body for the OpenAI Responses API.
     * @param prompt - the prompt to send to the API
     * @return JSON payload as string
     */
    String createRequestBody(String prompt) {
        return createRequestBody("", prompt);
    }

    /**
     * Creates a JSON request body for the OpenAI Responses API.
     * @param systemPrompt Optional system prompt for NPC persona/behavior
     * @param prompt User prompt text
     * @return JSON payload as string
     */
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
        RequestOptions effectiveOptions = options == null ? RequestOptions.defaults() : options;
        JsonObject payload = new JsonObject();
        payload.addProperty("model", effectiveOptions.model(model));
        payload.addProperty("max_output_tokens", effectiveOptions.maxOutputTokens(maxOutputTokens));
        payload.addProperty("store", storeResponses);
        String effectiveReasoningEffort = effectiveOptions.reasoningEffort(reasoningEffort);
        if (!effectiveReasoningEffort.isEmpty()) {
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", effectiveReasoningEffort);
            payload.add("reasoning", reasoning);
        }
        if (!effectiveOptions.safetyIdentifier().isBlank()) {
            payload.addProperty("safety_identifier", effectiveOptions.safetyIdentifier());
        }
        if (!effectiveOptions.promptCacheKey().isBlank()) {
            payload.addProperty("prompt_cache_key", effectiveOptions.promptCacheKey());
        }

        String instructions = combineInstructions(systemPrompt, responseInstruction);
        if (!instructions.isBlank()) {
            payload.addProperty("instructions", instructions);
        }
        JsonArray input = new JsonArray();
        input.add(createInputMessage("user", prompt));
        payload.add("input", input);
        if (responseFormat != null) {
            JsonObject text = new JsonObject();
            text.add("format", responseFormat.deepCopy());
            if (!effectiveOptions.verbosity().isBlank()) {
                text.addProperty("verbosity", effectiveOptions.verbosity());
            }
            payload.add("text", text);
        }

        return payload.toString();
    }

    private String sanitizeReasoningEffort(String effort) {
        if (effort == null) {
            return "";
        }
        String normalizedEffort = effort.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedEffort) {
            case "none", "low", "medium", "high", "xhigh", "max" -> normalizedEffort;
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

    /** Per-request controls that may safely override the default model execution profile. */
    public record RequestOptions(
            String model,
            int maxOutputTokens,
            String reasoningEffort,
            Duration requestTimeout,
            String safetyIdentifier,
            String promptCacheKey,
            String verbosity
    ) {
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

        public RequestOptions {
            safetyIdentifier = sanitizeIdentifier(safetyIdentifier, 64);
            promptCacheKey = sanitizeIdentifier(promptCacheKey, 64);
            verbosity = sanitizeVerbosity(verbosity);
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
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        JsonObject root = parseJsonObject(responseBody);
        if (root == null) {
            return "";
        }

        String outputArrayText = extractFromOutputArray(root.getAsJsonArray("output"));
        if (!outputArrayText.isBlank()) {
            return outputArrayText;
        }

        String topLevelOutputText = getString(root, "output_text");
        if (!topLevelOutputText.isBlank()) {
            return topLevelOutputText;
        }

        return "";
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        JsonObject root = parseJsonObject(responseBody);
        if (root == null || !root.has("error") || root.get("error").isJsonNull()) {
            return "";
        }

        JsonElement error = root.get("error");
        if (error.isJsonObject()) {
            return getString(error.getAsJsonObject(), "message");
        }
        if (error.isJsonPrimitive()) {
            return error.getAsString();
        }

        return "";
    }

    private JsonObject parseJsonObject(String value) {
        try {
            JsonElement parsed = JsonParser.parseString(value);
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (RuntimeException ignored) {
            // Keep this method pure; caller decides whether to log.
        }
        return null;
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

            JsonObject outputObject = outputItem.getAsJsonObject();
            String outputType = getString(outputObject, "type");
            if ("output_text".equals(outputType)) {
                appendText(directOutputText, getString(outputObject, "text"));
                continue;
            }
            if (!"message".equals(outputType)) {
                continue;
            }

            String role = getString(outputObject, "role");
            if (!role.isBlank() && !"assistant".equalsIgnoreCase(role)) {
                continue;
            }

            JsonArray content = outputObject.getAsJsonArray("content");
            if (content == null || content.isEmpty()) {
                continue;
            }

            for (JsonElement contentItem : content) {
                if (!contentItem.isJsonObject()) {
                    continue;
                }

                JsonObject contentObject = contentItem.getAsJsonObject();
                String contentType = getString(contentObject, "type");
                if ("output_text".equals(contentType) || contentType.isBlank()) {
                    appendText(assistantText, getString(contentObject, "text"));
                    continue;
                }
                if ("refusal".equals(contentType)) {
                    String refusalText = getString(contentObject, "refusal");
                    if (refusalText.isBlank()) {
                        refusalText = getString(contentObject, "text");
                    }
                    appendText(assistantText, refusalText);
                }
            }
        }

        if (assistantText.length() > 0) {
            return assistantText.toString();
        }
        return directOutputText.toString();
    }

    private String getString(JsonObject object, String property) {
        if (object == null || !object.has(property) || object.get(property).isJsonNull()) {
            return "";
        }

        JsonElement value = object.get(property);
        if (!value.isJsonPrimitive()) {
            return "";
        }

        String text = value.getAsString();
        return text == null ? "" : text.trim();
    }

    private void appendText(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(text.trim());
    }

    private String normalizeResponse(String response) {
        String normalized = response.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        normalized = normalized.replaceAll("\\s*\\n+\\s*", " ");
        normalized = normalized.replaceAll("\\s{2,}", " ").trim();

        if (normalized.isEmpty()) {
            return FALLBACK_RESPONSE;
        }

        if (normalized.length() > MAX_CHAT_RESPONSE_LENGTH) {
            return normalized.substring(0, MAX_CHAT_RESPONSE_LENGTH - 3).trim() + "...";
        }

        return normalized;
    }

}
