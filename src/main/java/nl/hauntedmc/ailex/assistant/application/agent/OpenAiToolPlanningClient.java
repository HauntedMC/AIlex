package nl.hauntedmc.ailex.assistant.application.agent;

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
import java.util.ArrayList;
import java.util.List;

/** Minimal Responses API client dedicated to bounded read-tool planning. */
public final class OpenAiToolPlanningClient {

    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/responses");
    private static final int MAX_OUTPUT_TOKENS = 220;
    private static final long TRANSIENT_FAILURE_COOLDOWN_MILLIS = 10_000L;
    private static final long AUTH_FAILURE_COOLDOWN_MILLIS = 60_000L;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private volatile long unavailableUntilMillis;

    public OpenAiToolPlanningClient(JavaPlugin plugin) {
        this(plugin == null ? null : plugin.getConfig(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
    }

    OpenAiToolPlanningClient(FileConfiguration config, HttpClient httpClient) {
        this.apiKey = config == null ? "" : clean(config.getString("openai.api_key", ""));
        this.model = config == null ? "" : clean(config.getString(
                "openai.assistant.agent.planner_model",
                config.getString("openai.assistant.models.fast.model", "gpt-5.6-luna")
        ));
        this.httpClient = httpClient;
    }

    public PlanningResponse plan(List<JsonElement> history, List<JsonObject> tools, Duration timeout) {
        if (apiKey.isBlank() || model.isBlank() || history == null || history.isEmpty()
                || System.currentTimeMillis() < unavailableUntilMillis) {
            return PlanningResponse.failure();
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.addProperty("store", false);
        payload.addProperty("max_output_tokens", MAX_OUTPUT_TOKENS);
        payload.addProperty("parallel_tool_calls", false);
        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", "low");
        payload.add("reasoning", reasoning);
        payload.addProperty("instructions", "You are AIlex's bounded read-context planner. Do not answer the player. "
                + "Call only the provided read tools when additional evidence would materially improve correctness. "
                + "Prefer no tool call when supplied information is already sufficient. Never invent tool results or IDs.");
        JsonArray input = new JsonArray();
        history.forEach(element -> input.add(element.deepCopy()));
        payload.add("input", input);
        JsonArray toolArray = new JsonArray();
        tools.forEach(tool -> toolArray.add(tool.deepCopy()));
        payload.add("tools", toolArray);

        Duration effectiveTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(5) : timeout.compareTo(Duration.ofSeconds(8)) > 0 ? Duration.ofSeconds(8) : timeout;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(ENDPOINT)
                .timeout(effectiveTimeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                handleHttpFailure(response.statusCode());
                return PlanningResponse.failure();
            }
            PlanningResponse parsed = parse(response.body());
            if (!parsed.success()) {
                markUnavailable(
                        TRANSIENT_FAILURE_COOLDOWN_MILLIS,
                        "Planner response did not contain a usable output payload"
                );
            }
            return parsed;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PlanningResponse.failure();
        } catch (IOException exception) {
            markUnavailable(
                    TRANSIENT_FAILURE_COOLDOWN_MILLIS,
                    "Planner transport failed: " + exception.getMessage()
            );
            return PlanningResponse.failure();
        } catch (RuntimeException exception) {
            markUnavailable(
                    TRANSIENT_FAILURE_COOLDOWN_MILLIS,
                    "Planner response parsing failed: " + exception.getMessage()
            );
            return PlanningResponse.failure();
        }
    }

    public static JsonElement userMessage(String text) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        JsonArray content = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("type", "input_text");
        part.addProperty("text", text == null ? "" : text);
        content.add(part);
        message.add("content", content);
        return message;
    }

    public static JsonElement functionCallInput(FunctionCall call) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "function_call");
        item.addProperty("name", call.name());
        item.addProperty("arguments", call.arguments());
        item.addProperty("call_id", call.callId());
        return item;
    }

    public static JsonElement functionOutput(String callId, String output) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "function_call_output");
        item.addProperty("call_id", callId);
        item.addProperty("output", output == null ? "" : output);
        return item;
    }

    PlanningResponse parse(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray output = root.getAsJsonArray("output");
        if (output == null) {
            return PlanningResponse.failure();
        }
        List<FunctionCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (JsonElement element : output) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String type = string(item, "type");
            if ("function_call".equals(type)) {
                String name = string(item, "name");
                String arguments = string(item, "arguments");
                String callId = string(item, "call_id");
                if (!name.isBlank() && !callId.isBlank()) {
                    calls.add(new FunctionCall(name, arguments, callId));
                }
            } else if ("message".equals(type) && item.has("content") && item.get("content").isJsonArray()) {
                for (JsonElement content : item.getAsJsonArray("content")) {
                    if (content.isJsonObject() && "output_text".equals(string(content.getAsJsonObject(), "type"))) {
                        if (!text.isEmpty()) {
                            text.append(' ');
                        }
                        text.append(string(content.getAsJsonObject(), "text"));
                    }
                }
            }
        }
        JsonObject usage = root.has("usage") && root.get("usage").isJsonObject()
                ? root.getAsJsonObject("usage") : new JsonObject();
        int inputTokens = integer(usage, "input_tokens");
        int outputTokens = integer(usage, "output_tokens");
        return new PlanningResponse(List.copyOf(calls), text.toString().trim(), true, inputTokens, outputTokens);
    }

    private void handleHttpFailure(int status) {
        String message = "Planner request failed with status " + status;
        if (status == 401 || status == 403) {
            markUnavailable(AUTH_FAILURE_COOLDOWN_MILLIS, message);
        } else if (status == 408 || status == 429 || status >= 500) {
            markUnavailable(TRANSIENT_FAILURE_COOLDOWN_MILLIS, message);
        } else {
            LoggerUtils.logWarning("[AIlex agent] " + message);
        }
    }

    private void markUnavailable(long cooldownMillis, String message) {
        unavailableUntilMillis = Math.max(
                unavailableUntilMillis,
                System.currentTimeMillis() + Math.max(0L, cooldownMillis)
        );
        LoggerUtils.logWarning("[AIlex agent] " + message + "; planner temporarily disabled.");
    }

    private int integer(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? Math.max(0, object.get(key).getAsInt()) : 0;
    }

    private String string(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString().trim() : "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public record FunctionCall(String name, String arguments, String callId) {
        public FunctionCall {
            name = clean(name);
            arguments = arguments == null ? "{}" : arguments.trim();
            callId = clean(callId);
        }
    }

    public record PlanningResponse(
            List<FunctionCall> calls,
            String text,
            boolean success,
            int inputTokens,
            int outputTokens
    ) {
        public PlanningResponse {
            calls = calls == null ? List.of() : List.copyOf(calls);
            text = clean(text);
            inputTokens = Math.max(0, inputTokens);
            outputTokens = Math.max(0, outputTokens);
        }

        public static PlanningResponse failure() {
            return new PlanningResponse(List.of(), "", false, 0, 0);
        }
    }
}
