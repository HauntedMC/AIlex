package nl.hauntedmc.ailex.benchmark;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.application.context.RequiredContextPlanner;
import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantReply;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.LocalKnowledgeIndex;
import nl.hauntedmc.ailex.assistant.infrastructure.live.AssistantContextProviderRegistry;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryScope;
import nl.hauntedmc.ailex.assistant.runtime.AssistantConversationManager;
import nl.hauntedmc.ailex.infrastructure.openai.OpenAiResponsesClient;

import org.bukkit.configuration.file.YamlConfiguration;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
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
 * Headless local composition root for live-model evaluation. Production assistant, memory, retrieval, planner, OpenAI
 * transport and grounding classes execute unchanged; only the Bukkit/plugin boundary and live snapshot are supplied here.
 */
final class BenchmarkRuntime implements AutoCloseable {

    private static final int BENCHMARK_NPC_ID = -7001;
    private static final String BENCHMARK_NPC_MEMORY_ID = "benchmark";
    private final Path repositoryRoot;
    private final Path workDirectory;
    private final AIlexPlugin plugin;
    private final MockedStatic<AIlexPlugin> pluginStatic;
    private final YamlConfiguration configuration;
    private final AssistantMemoryService memoryService;
    private final OpenAiResponsesClient openAiClient;
    private final AssistantService assistantService;
    private final LocalKnowledgeIndex knowledgeIndex;
    private final Method memoryContextMethod;
    private final RequiredContextPlanner contextPlanner = new RequiredContextPlanner();
    private final AssistantConversationManager conversations = new AssistantConversationManager(System::currentTimeMillis);

    BenchmarkRuntime(Path repositoryRoot, Path workDirectory, JsonObject overrides) throws IOException {
        this.repositoryRoot = repositoryRoot.toAbsolutePath().normalize();
        this.workDirectory = workDirectory.toAbsolutePath().normalize();
        Files.createDirectories(this.workDirectory);
        copyKnowledge();

        this.configuration = YamlConfiguration.loadConfiguration(
                this.repositoryRoot.resolve("src/main/resources/config.yml").toFile()
        );
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required for live benchmark execution");
        }
        configuration.set("openai.api_key", apiKey.trim());
        configuration.set("openai.store_responses", false);
        configuration.set("openai.assistant.observability.enabled", false);
        configuration.set("openai.assistant.reliability.cache_static_answers", false);
        configuration.set("openai.assistant.memory.storage.backend", "sqlite");
        applyOverrides(overrides);

        this.plugin = mock(AIlexPlugin.class);
        when(plugin.getConfig()).thenReturn(configuration);
        when(plugin.getDataFolder()).thenReturn(this.workDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AIlexBenchmark"));
        when(plugin.getAssistantContextProviderRegistry()).thenReturn(new AssistantContextProviderRegistry());

        this.pluginStatic = mockStatic(AIlexPlugin.class);
        pluginStatic.when(AIlexPlugin::getPlugin).thenReturn(plugin);

        this.memoryService = new AssistantMemoryService(plugin);
        when(plugin.getAssistantMemoryService()).thenReturn(memoryService);
        this.openAiClient = new OpenAiResponsesClient(plugin);
        when(plugin.getOpenAiResponsesClient()).thenReturn(openAiClient);
        this.assistantService = new AssistantService(plugin);
        this.knowledgeIndex = knowledgeIndex(assistantService);
        this.memoryContextMethod = memoryContextMethod();
    }

    JsonObject runCase(JsonObject benchmarkCase, int repetition) {
        long caseStarted = System.nanoTime();
        String caseId = string(benchmarkCase, "id", "case");
        UUID playerId = UUID.nameUUIDFromBytes((caseId + ':' + repetition).getBytes(StandardCharsets.UTF_8));
        String playerName = string(benchmarkCase, "player", "BenchmarkPlayer");
        seedMemory(benchmarkCase, playerId);
        seedDialogue(benchmarkCase, playerId, playerName);

        JsonArray turns = array(benchmarkCase, "turns");
        if (turns == null || turns.isEmpty()) {
            throw new IllegalArgumentException("Benchmark case " + caseId + " has no turns");
        }

        JsonArray turnResults = new JsonArray();
        AssistantReply finalReply = AssistantReply.invalid();
        AssistantIntentClassifier.Analysis finalAnalysis = null;
        RequiredContextPlanner.Plan finalPlan = null;
        List<LocalKnowledgeIndex.KnowledgeChunk> finalRetrieved = List.of();
        OpenAiResponsesClient.Usage totalUsage = OpenAiResponsesClient.Usage.empty();
        long providerCalls = 0L;

        for (JsonElement turnElement : turns) {
            if (!turnElement.isJsonObject()) {
                continue;
            }
            JsonObject turn = turnElement.getAsJsonObject();
            String role = string(turn, "role", "user").toLowerCase(Locale.ROOT);
            String content = string(turn, "content", "");
            if (content.isBlank()) {
                continue;
            }
            if ("assistant".equals(role)) {
                conversations.recordAssistant(playerId, BENCHMARK_NPC_ID, "AIlex", content, AssistantIntent.CONVERSATION);
                continue;
            }

            AssistantConversationManager.Snapshot dialogueSnapshot = conversations.snapshot(
                    playerId, BENCHMARK_NPC_ID, sessionTimeoutMillis()
            );
            conversations.recordUser(playerId, BENCHMARK_NPC_ID, playerName, content);
            memoryService.observe(playerId, content);
            memoryService.rememberExplicitLanguagePreference(playerId, content);

            AssistantSettings settings = AssistantSettings.from(configuration);
            AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                    content, dialogueSnapshot.asDialogueContext()
            );
            String language = settings.languageDetection()
                    ? AssistantIntentClassifier.detectLanguage(
                            content, settings.defaultLanguage(), settings.allowedLanguages()
                    )
                    : settings.defaultLanguage();
            analysis = new AssistantIntentClassifier.Analysis(
                    overrideIntent(benchmarkCase, turn, analysis.intent()), settings.resolveMode(analysis.mode()), language
            );
            RequiredContextPlanner.Plan plan = contextPlanner.plan(
                    analysis.intent(), analysis.mode(), content, settings
            );
            AssistantService.LiveSnapshot liveSnapshot = liveSnapshot(turn);
            String systemPrompt = configuration.getString(
                    "openai.chat.standalone.system_prompt", "Je bent AIlex, een HauntedMC-communityassistent."
            );
            String userPrompt = userPrompt(playerName, content);
            String memory = plan.durableMemory()
                    ? memoryContext(playerId, content, plan.eventMemory()) : "";
            AssistantService.PreparedRequest prepared = new AssistantService.PreparedRequest(
                    playerId.toString(), playerName, "AIlex", BENCHMARK_NPC_MEMORY_ID,
                    content, systemPrompt, userPrompt, analysis, settings, plan, plan.knowledge(), liveSnapshot, memory,
                    dialogueSnapshot.asDialogueContext(), false, System.nanoTime()
            );

            List<LocalKnowledgeIndex.KnowledgeChunk> retrieved = retrieve(prepared);
            OpenAiResponsesClient.UsageSnapshot before = openAiClient.usageSnapshot();
            long started = System.nanoTime();
            AssistantReply reply = assistantService.respond(prepared);
            long latencyMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            OpenAiResponsesClient.UsageSnapshot after = openAiClient.usageSnapshot();
            OpenAiResponsesClient.Usage usage = usageDelta(before.usage(), after.usage());
            totalUsage = totalUsage.plus(usage);
            providerCalls += Math.max(0L, after.requests() - before.requests());
            String answer = String.join("\n", reply.lines());
            conversations.recordAssistant(playerId, BENCHMARK_NPC_ID, "AIlex", answer, analysis.intent());

            JsonObject turnResult = new JsonObject();
            turnResult.addProperty("question", content);
            turnResult.addProperty("answer", answer);
            turnResult.addProperty("intent", analysis.intent().name());
            turnResult.addProperty("mode", analysis.mode().name());
            turnResult.addProperty("model", settings.profileFor(analysis.mode()).model());
            turnResult.addProperty("latency_ms", latencyMillis);
            turnResult.addProperty("confidence", reply.confidence());
            turnResult.addProperty("handoff", reply.handoff());
            turnResult.addProperty("memory_context_characters", memory.length());
            turnResult.add("evidence_ids", strings(reply.evidenceIds()));
            turnResult.add("retrieved_context", retrievedContext(retrieved));
            turnResult.add("usage", usageJson(usage, Math.max(0L, after.requests() - before.requests())));
            turnResult.add("context_plan", planJson(plan));
            turnResults.add(turnResult);

            finalReply = reply;
            finalAnalysis = analysis;
            finalPlan = plan;
            finalRetrieved = retrieved;
        }

        String finalAnswer = String.join("\n", finalReply.lines());
        BenchmarkScorer.Score score = BenchmarkScorer.score(
                benchmarkCase, finalAnswer, finalReply.evidenceIds(), finalReply.handoff()
        );
        JsonObject result = new JsonObject();
        result.addProperty("id", caseId);
        result.addProperty("suite", string(benchmarkCase, "suite", "unknown"));
        result.addProperty("category", string(benchmarkCase, "category", "uncategorized"));
        result.addProperty("repetition", repetition);
        result.addProperty("question", lastQuestion(turnResults));
        result.addProperty("answer", finalAnswer);
        result.addProperty("expected_answer", expectedAnswer(benchmarkCase));
        result.addProperty("official_metric", officialMetric(benchmarkCase));
        result.addProperty("intent", finalAnalysis == null ? "" : finalAnalysis.intent().name());
        result.addProperty("mode", finalAnalysis == null ? "" : finalAnalysis.mode().name());
        result.addProperty("model", finalAnalysis == null ? ""
                : AssistantSettings.from(configuration).profileFor(finalAnalysis.mode()).model());
        result.addProperty("handoff", finalReply.handoff());
        result.addProperty("confidence", finalReply.confidence());
        result.add("evidence_ids", strings(finalReply.evidenceIds()));
        result.add("claim_evidence", claimEvidenceJson(finalReply));
        result.add("retrieved_context", retrievedContext(finalRetrieved));
        result.add("context_plan", finalPlan == null ? new JsonObject() : planJson(finalPlan));
        result.add("turns", turnResults);
        result.addProperty("hard_pass", score.passed());
        result.addProperty("hard_checks_passed", score.passedChecks());
        result.addProperty("hard_checks_total", score.totalChecks());
        result.add("hard_failures", strings(score.failures()));
        result.add("usage", usageJson(totalUsage, providerCalls));
        result.addProperty(
                "latency_ms", TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - caseStarted))
        );
        result.add("metadata", benchmarkCase.has("metadata")
                ? benchmarkCase.get("metadata").deepCopy() : new JsonObject());
        return result;
    }

    String configurationHash() {
        return sha256(configuration.saveToString());
    }

    String knowledgeHash() {
        Path knowledge = repositoryRoot.resolve("src/main/resources/knowledge");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var paths = Files.walk(knowledge)) {
                paths.filter(Files::isRegularFile).sorted().forEach(path -> {
                    try {
                        digest.update(knowledge.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
                        digest.update(Files.readAllBytes(path));
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash benchmark knowledge", exception);
        }
    }

    @Override
    public void close() {
        memoryService.close();
        pluginStatic.close();
    }

    private void seedMemory(JsonObject benchmarkCase, UUID playerId) {
        JsonArray events = array(benchmarkCase, "seed_events");
        if (events == null) {
            return;
        }
        int index = 0;
        for (JsonElement element : events) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject event = element.getAsJsonObject();
            String content = string(event, "content", "");
            if (content.isBlank()) {
                continue;
            }
            long occurredAt = eventTime(event, index);
            memoryService.rememberTrusted(
                    MemoryScope.PLAYER, playerId.toString(), BENCHMARK_NPC_MEMORY_ID, MemoryKind.EVENT,
                    "benchmark_event_" + String.format(Locale.ROOT, "%06d", index++), content,
                    1.0D, 0.90D, "benchmark-fixture", "benchmark", occurredAt, Duration.ZERO,
                    Set.of("benchmark", "event")
            );
        }
        memoryService.flush();
    }

    private void seedDialogue(JsonObject benchmarkCase, UUID playerId, String playerName) {
        JsonArray turns = array(benchmarkCase, "seed_dialogue");
        if (turns == null) {
            return;
        }
        for (JsonElement element : turns) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject turn = element.getAsJsonObject();
            String role = string(turn, "role", "user");
            String content = string(turn, "content", "");
            if (content.isBlank()) {
                continue;
            }
            if ("assistant".equalsIgnoreCase(role)) {
                conversations.recordAssistant(
                        playerId, BENCHMARK_NPC_ID, "AIlex", content, AssistantIntent.CONVERSATION
                );
            } else {
                conversations.recordUser(playerId, BENCHMARK_NPC_ID, playerName, content);
            }
        }
    }

    private List<LocalKnowledgeIndex.KnowledgeChunk> retrieve(AssistantService.PreparedRequest request) {
        if (!request.retrieveKnowledge()) {
            return List.of();
        }
        if (request.analysis().intent() == AssistantIntent.KNOWLEDGE_DISCOVERY) {
            return knowledgeIndex.discover(request.playerId() + '|' + request.message(), request.settings());
        }
        return knowledgeIndex.search(request.message(), request.settings());
    }

    private String memoryContext(UUID playerId, String query, boolean includeEvents) {
        try {
            return (String) memoryContextMethod.invoke(
                    assistantService, playerId, BENCHMARK_NPC_MEMORY_ID, query, includeEvents
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not build production AIlex memory context for benchmark turn", exception);
        }
    }

    private AssistantIntent overrideIntent(
            JsonObject benchmarkCase,
            JsonObject turn,
            AssistantIntent fallback
    ) {
        String value = string(turn, "intent_override", string(benchmarkCase, "intent_override", ""));
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return AssistantIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private AssistantService.LiveSnapshot liveSnapshot(JsonObject turn) {
        JsonArray values = array(turn, "live_values");
        JsonArray sourceIds = array(turn, "live_source_ids");
        List<String> liveValues = values == null ? List.of() : stringList(values);
        Set<String> ids = sourceIds == null ? Set.of() : Set.copyOf(stringList(sourceIds));
        return new AssistantService.LiveSnapshot(liveValues, ids);
    }

    private String userPrompt(String playerName, String message) {
        String template = configuration.getString(
                "npc.defaults.entity.prompts.userPromptTemplate",
                "Bericht van speler {player_name}: \"{chat_message}\". Antwoord direct en natuurlijk."
        );
        return template.replace("{player_name}", playerName).replace("{chat_message}", message);
    }

    private long sessionTimeoutMillis() {
        return Math.max(1L, configuration.getLong("openai.chat.session_timeout_seconds", 900L)) * 1000L;
    }

    private long eventTime(JsonObject event, int index) {
        if (event.has("occurred_at_epoch_ms") && event.get("occurred_at_epoch_ms").isJsonPrimitive()) {
            return event.get("occurred_at_epoch_ms").getAsLong();
        }
        String iso = string(event, "occurred_at", "");
        if (!iso.isBlank()) {
            try {
                return Instant.parse(iso).toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // Fall through to deterministic order-preserving fixture time.
            }
        }
        return 1_700_000_000_000L + index * 1000L;
    }

    private void applyOverrides(JsonObject overrides) {
        if (overrides == null) {
            return;
        }
        for (var entry : overrides.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                configuration.set(entry.getKey(), null);
            } else if (value.isJsonPrimitive()) {
                var primitive = value.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    configuration.set(entry.getKey(), primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    configuration.set(entry.getKey(), primitive.getAsNumber());
                } else {
                    configuration.set(entry.getKey(), primitive.getAsString());
                }
            }
        }
    }

    private void copyKnowledge() throws IOException {
        Path source = repositoryRoot.resolve("src/main/resources/knowledge");
        Path target = workDirectory.resolve("knowledge");
        if (!Files.isDirectory(source)) {
            throw new IllegalStateException("Knowledge directory is missing: " + source);
        }
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private LocalKnowledgeIndex knowledgeIndex(AssistantService service) {
        try {
            Field field = AssistantService.class.getDeclaredField("knowledgeIndex");
            field.setAccessible(true);
            return (LocalKnowledgeIndex) field.get(service);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access the production knowledge index for benchmark tracing", exception);
        }
    }

    private Method memoryContextMethod() {
        try {
            Method method = AssistantService.class.getDeclaredMethod(
                    "memoryContext", UUID.class, String.class, String.class, boolean.class
            );
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access the production memory context path for benchmarks", exception);
        }
    }

    private OpenAiResponsesClient.Usage usageDelta(
            OpenAiResponsesClient.Usage before,
            OpenAiResponsesClient.Usage after
    ) {
        return new OpenAiResponsesClient.Usage(
                Math.max(0L, after.inputTokens() - before.inputTokens()),
                Math.max(0L, after.cachedInputTokens() - before.cachedInputTokens()),
                Math.max(0L, after.cacheWriteTokens() - before.cacheWriteTokens()),
                Math.max(0L, after.outputTokens() - before.outputTokens()),
                Math.max(0L, after.totalTokens() - before.totalTokens())
        );
    }

    private JsonObject usageJson(OpenAiResponsesClient.Usage usage, long calls) {
        JsonObject object = new JsonObject();
        object.addProperty("provider_calls", calls);
        object.addProperty("input_tokens", usage.inputTokens());
        object.addProperty("cached_input_tokens", usage.cachedInputTokens());
        object.addProperty("cache_write_tokens", usage.cacheWriteTokens());
        object.addProperty("output_tokens", usage.outputTokens());
        object.addProperty("total_tokens", usage.totalTokens());
        return object;
    }

    private JsonObject planJson(RequiredContextPlanner.Plan plan) {
        JsonObject object = new JsonObject();
        object.addProperty("knowledge", plan.knowledge());
        object.addProperty("durable_memory", plan.durableMemory());
        object.addProperty("event_memory", plan.eventMemory());
        JsonArray sources = new JsonArray();
        plan.liveSources().stream().map(Enum::name).sorted().forEach(sources::add);
        object.add("live_sources", sources);
        return object;
    }

    private JsonArray retrievedContext(List<LocalKnowledgeIndex.KnowledgeChunk> chunks) {
        JsonArray array = new JsonArray();
        for (LocalKnowledgeIndex.KnowledgeChunk chunk : chunks) {
            JsonObject object = new JsonObject();
            object.addProperty("doc_id", chunk.id());
            object.addProperty("title", chunk.title());
            object.addProperty("text", chunk.text());
            object.addProperty("authority", chunk.authority());
            object.addProperty("source", chunk.source());
            array.add(object);
        }
        return array;
    }

    private JsonObject claimEvidenceJson(AssistantReply reply) {
        JsonObject object = new JsonObject();
        reply.claimEvidence().forEach((line, ids) -> object.add(String.valueOf(line), strings(ids)));
        return object;
    }

    private JsonArray strings(Iterable<String> values) {
        JsonArray array = new JsonArray();
        if (values != null) {
            for (String value : values) {
                array.add(value);
            }
        }
        return array;
    }

    private List<String> stringList(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                values.add(element.getAsString());
            }
        }
        return List.copyOf(values);
    }

    private String lastQuestion(JsonArray turnResults) {
        if (turnResults.isEmpty()) {
            return "";
        }
        return turnResults.get(turnResults.size() - 1).getAsJsonObject().get("question").getAsString();
    }

    private String expectedAnswer(JsonObject benchmarkCase) {
        JsonObject expect = benchmarkCase.has("expect") && benchmarkCase.get("expect").isJsonObject()
                ? benchmarkCase.getAsJsonObject("expect") : null;
        return expect == null ? "" : string(expect, "answer", string(expect, "exact", ""));
    }

    private String officialMetric(JsonObject benchmarkCase) {
        JsonObject metadata = benchmarkCase.has("metadata") && benchmarkCase.get("metadata").isJsonObject()
                ? benchmarkCase.getAsJsonObject("metadata") : null;
        return metadata == null ? "" : string(metadata, "official_metric", "");
    }

    private JsonArray array(JsonObject object, String name) {
        return object != null && object.has(name) && object.get(name).isJsonArray()
                ? object.getAsJsonArray(name) : null;
    }

    private String string(JsonObject object, String name, String fallback) {
        return object != null && object.has(name) && object.get(name).isJsonPrimitive()
                ? object.get(name).getAsString() : fallback;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
