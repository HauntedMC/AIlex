package nl.hauntedmc.ailex.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Local-only live benchmark entry point used by the ./bench wrapper. */
public final class BenchmarkMain {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private BenchmarkMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one benchmark request JSON path");
        }
        refuseCi();
        Path requestPath = Path.of(args[0]).toAbsolutePath().normalize();
        JsonObject request = JsonParser.parseString(Files.readString(requestPath)).getAsJsonObject();
        Path repositoryRoot = Path.of(string(request, "repository_root", ".")).toAbsolutePath().normalize();
        Path suitePath = Path.of(string(request, "suite_path", "")).toAbsolutePath().normalize();
        Path outputDirectory = Path.of(string(request, "output_dir", "benchmark/results/run"))
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(suitePath)) {
            throw new IllegalArgumentException("Suite JSONL does not exist: " + suitePath);
        }
        Files.createDirectories(outputDirectory);
        Path workDirectory = outputDirectory.resolve("work");
        Files.createDirectories(workDirectory);

        int limit = integer(request, "limit", 0);
        int repeat = Math.max(1, integer(request, "repeat", 1));
        String category = string(request, "category", "").toLowerCase(Locale.ROOT);
        String caseId = string(request, "case_id", "");
        JsonObject overrides = request.has("overrides") && request.get("overrides").isJsonObject()
                ? request.getAsJsonObject("overrides") : new JsonObject();
        List<JsonObject> cases = loadCases(suitePath, category, caseId, limit);
        if (cases.isEmpty()) {
            throw new IllegalStateException("No benchmark cases matched the requested filters");
        }

        Path resultsPath = outputDirectory.resolve("results.jsonl");
        int completed = 0;
        int hardEvaluated = 0;
        int hardPassed = 0;
        long startedAt = System.currentTimeMillis();
        try (BenchmarkRuntime runtime = new BenchmarkRuntime(repositoryRoot, workDirectory, overrides);
             BufferedWriter writer = Files.newBufferedWriter(resultsPath, StandardCharsets.UTF_8)) {
            for (JsonObject benchmarkCase : cases) {
                JsonObject executableCase = resolveFixtures(benchmarkCase, suitePath.getParent());
                for (int repetition = 1; repetition <= repeat; repetition++) {
                    JsonObject result = runtime.runCase(executableCase, repetition);
                    writer.write(GSON.toJson(result));
                    writer.newLine();
                    writer.flush();
                    completed++;
                    int hardChecks = result.get("hard_checks_total").getAsInt();
                    if (hardChecks > 0) {
                        hardEvaluated++;
                    }
                    if (hardChecks > 0 && result.get("hard_pass").getAsBoolean()) {
                        hardPassed++;
                    }
                    String hardStatus = hardChecks == 0 ? "N/A"
                            : result.get("hard_pass").getAsBoolean() ? "PASS" : "FAIL";
                    System.out.printf(
                            Locale.ROOT,
                            "[%d/%d] %s hard=%s latency=%dms%n",
                            completed,
                            cases.size() * repeat,
                            result.get("id").getAsString(),
                            hardStatus,
                            result.get("latency_ms").getAsLong()
                    );
                }
            }
            JsonObject run = new JsonObject();
            run.addProperty("run_id", string(request, "run_id", outputDirectory.getFileName().toString()));
            run.addProperty("suite_path", suitePath.toString());
            run.addProperty("started_at", Instant.ofEpochMilli(startedAt).toString());
            run.addProperty("completed_at", Instant.now().toString());
            run.addProperty("cases", completed);
            run.addProperty("hard_evaluated", hardEvaluated);
            run.addProperty("hard_passed", hardPassed);
            run.addProperty("hard_failed", hardEvaluated - hardPassed);
            run.addProperty("fixture_memory_storage", "ephemeral");
            run.addProperty("fixture_memory_reset_per_case", true);
            run.addProperty("routing_policy", "native unless a fixture explicitly declares intent_override");
            run.addProperty("git_sha", gitSha(repositoryRoot));
            run.addProperty("config_sha256", runtime.configurationHash());
            run.addProperty("knowledge_sha256", runtime.knowledgeHash());
            run.addProperty("results", resultsPath.getFileName().toString());
            Files.writeString(
                    outputDirectory.resolve("run.json"),
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(run),
                    StandardCharsets.UTF_8
            );
        }
        deleteRecursively(workDirectory);
        System.out.println("Benchmark results: " + outputDirectory);
    }

    private static JsonObject resolveFixtures(JsonObject benchmarkCase, Path suiteDirectory) throws IOException {
        JsonObject resolved = benchmarkCase.deepCopy();
        String fixture = string(resolved, "seed_events_file", "");
        if (fixture.isBlank()) {
            return resolved;
        }
        Path fixturePath = Path.of(fixture);
        if (!fixturePath.isAbsolute()) {
            fixturePath = suiteDirectory.resolve(fixturePath);
        }
        fixturePath = fixturePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(fixturePath)) {
            throw new IllegalArgumentException("Benchmark seed-events fixture does not exist: " + fixturePath);
        }
        resolved.add("seed_events", readFixture(fixturePath));
        resolved.remove("seed_events_file");
        return resolved;
    }

    private static JsonArray readFixture(Path path) throws IOException {
        JsonArray events = new JsonArray();
        if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jsonl")) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonElement parsed = JsonParser.parseString(line);
                    if (!parsed.isJsonObject()) {
                        throw new IllegalArgumentException("Seed-events JSONL entries must be objects: " + path);
                    }
                    events.add(parsed);
                }
            }
            return events;
        }
        JsonElement parsed = JsonParser.parseString(Files.readString(path));
        if (!parsed.isJsonArray()) {
            throw new IllegalArgumentException("Seed-events JSON fixture must be an array: " + path);
        }
        for (JsonElement event : parsed.getAsJsonArray()) {
            if (!event.isJsonObject()) {
                throw new IllegalArgumentException("Seed-events JSON entries must be objects: " + path);
            }
            events.add(event);
        }
        return events;
    }

    private static List<JsonObject> loadCases(
            Path path,
            String category,
            String caseId,
            int limit
    ) throws IOException {
        List<JsonObject> cases = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonElement parsed = JsonParser.parseString(line);
                if (!parsed.isJsonObject()) {
                    continue;
                }
                JsonObject benchmarkCase = parsed.getAsJsonObject();
                if (!category.isBlank()
                        && !category.equals(string(benchmarkCase, "category", "").toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (!caseId.isBlank() && !caseId.equals(string(benchmarkCase, "id", ""))) {
                    continue;
                }
                cases.add(benchmarkCase);
                if (limit > 0 && cases.size() >= limit) {
                    break;
                }
            }
        }
        cases.sort(Comparator.comparing(value -> string(value, "id", "")));
        return List.copyOf(cases);
    }

    private static void refuseCi() {
        String ci = System.getenv("CI");
        if (ci != null && "true".equalsIgnoreCase(ci.trim())) {
            throw new IllegalStateException("Live AIlex benchmarks are local-only and refuse to run when CI=true");
        }
    }

    private static String gitSha(Path root) {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 ? value : "unknown";
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "unknown";
        }
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Results remain valid if local work cleanup fails.
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
}
