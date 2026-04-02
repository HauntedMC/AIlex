package nl.hauntedmc.ailex.ai.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

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
public class ChatGPTClient {

    static final String OPENAI_RESPONSES_API_URL = "https://api.openai.com/v1/responses";
    static final String FALLBACK_RESPONSE = "Ik kan nu even niet reageren.";
    static final String SAFETY_FALLBACK_RESPONSE = "Daar ga ik niet op in. Laten we het gezellig houden.";
    static final String SAFETY_SYSTEM_PROMPT = "You are a Minecraft chat NPC for a general audience including minors. "
            + "Never generate sexual, erotic, pornographic, fetish, explicit, or 18+ content. "
            + "Never produce grooming, exploitative, or suggestive content. "
            + "If asked for inappropriate content, refuse briefly and redirect to a safe topic. "
            + "Keep all replies age-appropriate and safe-for-work.";

    private static final int MAX_CHAT_RESPONSE_LENGTH = 300;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String SYSTEM_RESPONSE_INSTRUCTION = "Return exactly one short plain-text chat response. Do not use markdown, quotes, or speaker labels.";
    private static final Pattern INAPPROPRIATE_CONTENT_PATTERN = Pattern.compile(
            "(?i)(\\b(?:sex|seks|sexual|seksueel|porn|porno|nude|naakt|erotic|erotisch|fetish|nsfw|onlyfans|"
                    + "rape|verkracht(?:ing)?)\\b|18\\s*\\+)"
    );

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final boolean safetyEnabled;
    private final String safetySystemPrompt;
    private final String safetyFallbackResponse;

    /**
     * Constructor for the ChatGPTClient.
     * @param plugin - AIlex plugin instance
     */
    public ChatGPTClient(JavaPlugin plugin) {
        this(plugin.getConfig());
    }

    ChatGPTClient(FileConfiguration config) {
        this(
                config.getString("openai.api_key", ""),
                config.getString("openai.model", "gpt-4.1-mini"),
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build(),
                config.getBoolean("openai.safety.enabled", true),
                config.getString("openai.safety.system_prompt", SAFETY_SYSTEM_PROMPT),
                config.getString("openai.safety.fallback_response", SAFETY_FALLBACK_RESPONSE)
        );
    }

    ChatGPTClient(String apiKey, String model, HttpClient httpClient) {
        this(apiKey, model, httpClient, true, SAFETY_SYSTEM_PROMPT, SAFETY_FALLBACK_RESPONSE);
    }

    ChatGPTClient(String apiKey, String model, HttpClient httpClient, boolean safetyEnabled, String safetySystemPrompt,
                  String safetyFallbackResponse) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.safetyEnabled = safetyEnabled;
        this.safetySystemPrompt = safetySystemPrompt == null ? "" : safetySystemPrompt.trim();
        this.safetyFallbackResponse = safetyFallbackResponse == null || safetyFallbackResponse.isBlank()
                ? SAFETY_FALLBACK_RESPONSE
                : safetyFallbackResponse.trim();

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
        if (userPrompt == null || userPrompt.isBlank()) {
            return FALLBACK_RESPONSE;
        }

        if (!isConfigured()) {
            return FALLBACK_RESPONSE;
        }

        HttpRequest request = createHttpRequest(systemPrompt, userPrompt);

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

            String normalizedResponse = normalizeResponse(parsedResponse);
            if (violatesSafetyPolicy(normalizedResponse)) {
                LoggerUtils.logWarning("OpenAI response rejected by local safety guard.");
                return safetyFallbackResponse;
            }

            return normalizedResponse;
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

    private boolean isConfigured() {
        return !apiKey.isBlank() && !model.isBlank();
    }

    /**
     * Creates an HttpRequest with the given prompt.
     * @param prompt - the prompt to send to the API
     * @return the HttpRequest
     */
    private HttpRequest createHttpRequest(String systemPrompt, String prompt) {
        String inputJson = createRequestBody(systemPrompt, prompt);

        return HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_RESPONSES_API_URL))
                .timeout(REQUEST_TIMEOUT)
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
        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);

        JsonArray input = new JsonArray();
        if (safetyEnabled && !safetySystemPrompt.isBlank()) {
            input.add(createInputMessage("system", safetySystemPrompt));
        }
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            input.add(createInputMessage("system", systemPrompt));
        }
        input.add(createInputMessage("system", SYSTEM_RESPONSE_INSTRUCTION));
        input.add(createInputMessage("user", prompt));
        payload.add("input", input);

        return payload.toString();
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

        String topLevelOutputText = getString(root, "output_text");
        if (!topLevelOutputText.isBlank()) {
            return topLevelOutputText;
        }

        String outputArrayText = extractFromOutputArray(root.getAsJsonArray("output"));
        if (!outputArrayText.isBlank()) {
            return outputArrayText;
        }

        return extractFromLegacyChoices(root.getAsJsonArray("choices"));
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

        for (JsonElement outputItem : outputArray) {
            if (!outputItem.isJsonObject()) {
                continue;
            }

            JsonObject outputObject = outputItem.getAsJsonObject();

            String directText = getString(outputObject, "text");
            if (!directText.isBlank()) {
                return directText;
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
                String text = getString(contentObject, "text");
                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        return "";
    }

    private String extractFromLegacyChoices(JsonArray choicesArray) {
        if (choicesArray == null || choicesArray.isEmpty()) {
            return "";
        }

        for (JsonElement choiceItem : choicesArray) {
            if (!choiceItem.isJsonObject()) {
                continue;
            }

            JsonObject choice = choiceItem.getAsJsonObject();

            String completionText = getString(choice, "text");
            if (!completionText.isBlank()) {
                return completionText;
            }

            if (!choice.has("message") || !choice.get("message").isJsonObject()) {
                continue;
            }

            JsonObject message = choice.getAsJsonObject("message");
            JsonElement content = message.get("content");
            if (content == null || content.isJsonNull()) {
                continue;
            }

            if (content.isJsonPrimitive()) {
                String chatText = content.getAsString();
                if (!chatText.isBlank()) {
                    return chatText;
                }
            }

            if (!content.isJsonArray()) {
                continue;
            }

            for (JsonElement contentItem : content.getAsJsonArray()) {
                if (!contentItem.isJsonObject()) {
                    continue;
                }

                String chatText = getString(contentItem.getAsJsonObject(), "text");
                if (!chatText.isBlank()) {
                    return chatText;
                }
            }
        }

        return "";
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

    private boolean violatesSafetyPolicy(String response) {
        if (!safetyEnabled || response == null || response.isBlank()) {
            return false;
        }
        return INAPPROPRIATE_CONTENT_PATTERN.matcher(response).find();
    }
}
