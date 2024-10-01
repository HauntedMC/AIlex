package nl.hauntedmc.ailex.ai.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Client for the OpenAI ChatGPT API.
 * This class is responsible for sending requests to the OpenAI API and receiving responses.
 */
public class ChatGPTClient {

    private final String apiKey;
    private final String apiUrl;
    private final HttpClient httpClient;

    /**
     * Constructor for the ChatGPTClient.
     * @param plugin - AIlex plugin instance
     */
    public ChatGPTClient(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        apiKey = config.getString("openai.api_key");
        String model = config.getString("openai.model");
        apiUrl = "https://api.openai.com/v1/engines/" + model + "/completions";
        httpClient = HttpClient.newHttpClient();

        LoggerUtils.logInfo("Initialized ChatGPTClient with API URL: " + apiUrl);
    }

    /**
     * Sends a request to the OpenAI API with the given prompt and returns the response.
     * @param prompt - the prompt to send to the API
     * @return the text response from the API
     */
    public String getChatResponse(String prompt) {
        HttpRequest request = createHttpRequest(prompt);

        // Send the request and get the response
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
                    JsonArray choices = jsonResponse.getAsJsonArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        JsonObject firstChoice = choices.get(0).getAsJsonObject();
                        String text = firstChoice.get("text").getAsString().trim();
                        if (text.startsWith("\"") && text.endsWith("\"")) {
                            text = text.substring(1, text.length() - 1);
                        }
                        return text;
                    } else {
                        LoggerUtils.logWarning("No choices found in the response.");
                        return "";
                    }
                })
                .exceptionally(e -> {
                    LoggerUtils.logError("Request to ChatGPT API failed: " + e.getMessage());
                    return "";
                })
                .join();
    }

    /**
     * Creates an HttpRequest with the given prompt.
     * @param prompt - the prompt to send to the API
     * @return the HttpRequest
     */
    private HttpRequest createHttpRequest(String prompt) {
        String inputJson = String.format(
                "{\"prompt\": \"%s\", \"max_tokens\": 100, \"temperature\": 0.7, \"top_p\": 1, \"frequency_penalty\": 0.0, \"presence_penalty\": 0.0}",
                escapeJson(prompt)
        );

        return HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(inputJson, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Escapes special characters in the JSON string.
     * @param text - the text to escape
     * @return the escaped text
     */
    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
