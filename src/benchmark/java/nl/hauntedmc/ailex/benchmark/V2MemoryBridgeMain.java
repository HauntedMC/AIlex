package nl.hauntedmc.ailex.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryRecord;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryScope;

import org.bukkit.configuration.file.YamlConfiguration;
import org.mockito.MockedStatic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * JSON-lines bridge exposing AIlex's real text-memory repository/search implementation to the official
 * LongMemEval-V2 Python Memory interface. It performs no model calls; the upstream benchmark owns the reader/evaluator.
 */
public final class V2MemoryBridgeMain {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final UUID PLAYER_ID = UUID.nameUUIDFromBytes("ailex-longmemeval-v2".getBytes(StandardCharsets.UTF_8));
    private static final String NPC_ID = "longmemeval-v2";
    private static final int DEFAULT_MAX_RESULTS = 24;

    private V2MemoryBridgeMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected repository root and bridge workspace arguments");
        }
        Path repositoryRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path workspace = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(workspace);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                repositoryRoot.resolve("src/main/resources/config.yml").toFile()
        );
        config.set("openai.assistant.memory.enabled", true);
        config.set("openai.assistant.memory.storage.backend", "sqlite");
        config.set("openai.assistant.memory.consolidation.enabled", false);
        config.set("openai.assistant.memory.retention.enabled", false);

        AIlexPlugin plugin = mock(AIlexPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(workspace.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AIlexV2Memory"));

        try (MockedStatic<AIlexPlugin> pluginStatic = mockStatic(AIlexPlugin.class)) {
            pluginStatic.when(AIlexPlugin::getPlugin).thenReturn(plugin);
            try (AssistantMemoryService memory = new AssistantMemoryService(plugin);
                 BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                ready();
                String line;
                long sequence = 0L;
                while ((line = input.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonObject request;
                    try {
                        JsonElement parsed = JsonParser.parseString(line);
                        if (!parsed.isJsonObject()) {
                            response(false, "request must be a JSON object", new JsonArray(), 0L);
                            continue;
                        }
                        request = parsed.getAsJsonObject();
                    } catch (RuntimeException exception) {
                        response(false, "invalid JSON request", new JsonArray(), 0L);
                        continue;
                    }
                    String operation = string(request, "op", "").toLowerCase(Locale.ROOT);
                    long started = System.nanoTime();
                    try {
                        switch (operation) {
                            case "insert_batch" -> sequence = insertBatch(memory, request, sequence);
                            case "query" -> query(memory, request, started);
                            case "flush" -> {
                                memory.flush();
                                response(true, "", new JsonArray(), elapsedMillis(started));
                            }
                            case "close" -> {
                                memory.flush();
                                response(true, "", new JsonArray(), elapsedMillis(started));
                                return;
                            }
                            default -> response(false, "unknown operation: " + operation, new JsonArray(), elapsedMillis(started));
                        }
                        if ("insert_batch".equals(operation)) {
                            response(true, "", new JsonArray(), elapsedMillis(started));
                        }
                    } catch (RuntimeException exception) {
                        response(false, safeMessage(exception), new JsonArray(), elapsedMillis(started));
                    }
                }
            }
        }
    }

    private static long insertBatch(AssistantMemoryService memory, JsonObject request, long sequence) {
        JsonArray items = request.has("items") && request.get("items").isJsonArray()
                ? request.getAsJsonArray("items") : new JsonArray();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String content = string(item, "content", "").replaceAll("\\s+", " ").trim();
            if (content.isBlank()) {
                continue;
            }
            long occurredAt = longValue(item, "occurred_at_epoch_ms", 1_700_000_000_000L + sequence);
            String trajectoryId = string(item, "trajectory_id", "trajectory");
            String key = "trajectory_" + String.format(Locale.ROOT, "%012d", sequence++);
            memory.rememberTrusted(
                    MemoryScope.PLAYER,
                    PLAYER_ID.toString(),
                    NPC_ID,
                    MemoryKind.EVENT,
                    key,
                    content,
                    1.0D,
                    0.90D,
                    "longmemeval-v2-trajectory",
                    trajectoryId,
                    occurredAt,
                    Duration.ZERO,
                    Set.of("benchmark", "longmemeval-v2", "trajectory")
            );
        }
        return sequence;
    }

    private static void query(AssistantMemoryService memory, JsonObject request, long started) {
        String question = string(request, "query", "");
        int maximumResults = Math.clamp(integer(request, "max_results", DEFAULT_MAX_RESULTS), 1, 96);
        List<MemoryRecord> matches = memory.search(
                PLAYER_ID, NPC_ID, question, Set.of(MemoryKind.EVENT, MemoryKind.EPISODE), maximumResults
        );
        JsonArray items = new JsonArray();
        for (MemoryRecord record : matches) {
            JsonObject item = new JsonObject();
            item.addProperty("type", "text");
            StringBuilder value = new StringBuilder("evidence_id=memory.")
                    .append(record.id()).append(' ').append(record.value());
            if (record.occurredAt() > 0L) {
                value.append(" occurred_at_epoch_ms=").append(record.occurredAt());
            }
            item.addProperty("value", value.toString());
            items.add(item);
        }
        response(true, "", items, elapsedMillis(started));
    }

    private static void ready() {
        JsonObject response = new JsonObject();
        response.addProperty("bridge", true);
        response.addProperty("ready", true);
        System.out.println(GSON.toJson(response));
        System.out.flush();
    }

    private static void response(boolean ok, String error, JsonArray items, long latencyMillis) {
        JsonObject response = new JsonObject();
        response.addProperty("bridge", true);
        response.addProperty("ok", ok);
        response.addProperty("error", error == null ? "" : error);
        response.addProperty("latency_ms", Math.max(0L, latencyMillis));
        response.add("items", items == null ? new JsonArray() : items);
        System.out.println(GSON.toJson(response));
        System.out.flush();
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - started));
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : fallback;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? "" : throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
