package nl.hauntedmc.ailex.assistant.infrastructure.knowledge;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Learned semantic embedding provider backed by OpenAI's embeddings endpoint. */
public final class OpenAiEmbeddingProvider implements SemanticEmbeddingProvider {

    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/embeddings");
    private static final int MAX_BATCH = 64;
    private static final int MAX_CACHE = 2_048;
    private static final long FAILURE_COOLDOWN_MILLIS = 60_000L;

    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final Duration timeout;
    private final boolean enabled;
    private final HttpClient httpClient;
    private final Map<String, double[]> cache = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, double[]> eldest) {
                    return size() > MAX_CACHE;
                }
            }
    );
    private volatile long unavailableUntil;

    public OpenAiEmbeddingProvider(JavaPlugin plugin) {
        this(plugin == null ? null : plugin.getConfig(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
    }

    OpenAiEmbeddingProvider(FileConfiguration config, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.apiKey = config == null ? "" : clean(config.getString("openai.api_key", ""));
        this.enabled = config == null || config.getBoolean(
                "openai.assistant.retrieval.semantic_embeddings.enabled", true
        );
        this.model = config == null ? "text-embedding-3-small" : clean(config.getString(
                "openai.assistant.retrieval.semantic_embeddings.model", "text-embedding-3-small"
        ));
        this.dimensions = config == null ? 512 : Math.clamp(config.getInt(
                "openai.assistant.retrieval.semantic_embeddings.dimensions", 512
        ), 128, 3_072);
        int timeoutSeconds = config == null ? 8 : Math.clamp(config.getInt(
                "openai.assistant.retrieval.semantic_embeddings.timeout_seconds", 8
        ), 2, 20);
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public boolean available() {
        return enabled && !apiKey.isBlank() && !model.isBlank() && System.currentTimeMillis() >= unavailableUntil;
    }

    @Override
    public List<double[]> embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty() || !available()) {
            return List.of();
        }
        List<String> normalized = inputs.stream().map(this::clean).toList();
        List<double[]> result = new ArrayList<>(normalized.size());
        List<String> missing = new ArrayList<>();
        for (String input : normalized) {
            double[] cached = cache.get(input);
            if (cached == null) {
                missing.add(input);
            }
        }
        if (!missing.isEmpty() && !fillCache(missing)) {
            return List.of();
        }
        for (String input : normalized) {
            double[] vector = cache.get(input);
            if (vector == null) {
                return List.of();
            }
            result.add(vector.clone());
        }
        return List.copyOf(result);
    }

    public void clearCache() {
        cache.clear();
    }

    private boolean fillCache(List<String> missing) {
        for (int offset = 0; offset < missing.size(); offset += MAX_BATCH) {
            List<String> batch = missing.subList(offset, Math.min(missing.size(), offset + MAX_BATCH));
            if (!requestBatch(batch)) {
                return false;
            }
        }
        return true;
    }

    private boolean requestBatch(List<String> batch) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.addProperty("encoding_format", "float");
        payload.addProperty("dimensions", dimensions);
        JsonArray input = new JsonArray();
        batch.forEach(input::add);
        payload.add("input", input);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(ENDPOINT)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                markUnavailable("Embedding request failed with status " + response.statusCode());
                return false;
            }
            JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray data = object.getAsJsonArray("data");
            if (data == null || data.size() != batch.size()) {
                markUnavailable("Embedding response had an unexpected vector count");
                return false;
            }
            double[][] vectors = new double[batch.size()][];
            for (JsonElement element : data) {
                JsonObject item = element.getAsJsonObject();
                int index = item.get("index").getAsInt();
                if (index < 0 || index >= vectors.length) {
                    markUnavailable("Embedding response contained an invalid index");
                    return false;
                }
                JsonArray embedding = item.getAsJsonArray("embedding");
                double[] vector = new double[embedding.size()];
                for (int dimension = 0; dimension < embedding.size(); dimension++) {
                    vector[dimension] = embedding.get(dimension).getAsDouble();
                }
                vectors[index] = vector;
            }
            for (int index = 0; index < batch.size(); index++) {
                if (vectors[index] == null || vectors[index].length == 0) {
                    markUnavailable("Embedding response omitted a vector");
                    return false;
                }
                cache.put(batch.get(index), vectors[index]);
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markUnavailable("Embedding request was interrupted");
            return false;
        } catch (IOException | RuntimeException exception) {
            markUnavailable("Embedding request failed: " + exception.getMessage());
            return false;
        }
    }

    private void markUnavailable(String message) {
        unavailableUntil = System.currentTimeMillis() + FAILURE_COOLDOWN_MILLIS;
        LoggerUtils.logWarning("[AIlex retrieval] " + message + "; using lexical retrieval during cooldown.");
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
