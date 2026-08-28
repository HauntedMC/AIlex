package nl.hauntedmc.ailex.assistant.application.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.LocalKnowledgeIndex;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryEvidenceId;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Capability registry for the model-facing read surface. It is deliberately closed over explicit implementations:
 * registering a tool requires Java code, and every invocation is re-checked against the request's capability ceiling.
 */
public final class AssistantToolRegistry {

    private static final int MAX_OBSERVATION_CHARACTERS = 4_000;

    private final LocalKnowledgeIndex knowledgeIndex;
    private final AssistantMemoryService memoryService;
    private final AssistantExperienceMemoryService experienceMemory;
    private final Map<String, AssistantTool> tools;

    public AssistantToolRegistry(
            LocalKnowledgeIndex knowledgeIndex,
            AssistantMemoryService memoryService,
            AssistantExperienceMemoryService experienceMemory
    ) {
        this.knowledgeIndex = knowledgeIndex;
        this.memoryService = memoryService;
        this.experienceMemory = experienceMemory;

        Map<String, AssistantTool> registered = new LinkedHashMap<>();
        register(registered, stringTool(
                "search_memory",
                "Use for explicit player-owned facts, preferences, goals or remembered context not already supplied. Search by the smallest discriminative concept; do not use for current live state or official server rules.",
                "session",
                "query",
                "What memory should be recalled?",
                this::searchMemory
        ));
        register(registered, stringTool(
                "search_memory_timeline",
                "Use for corrections, what changed, what was true earlier, or conflicting remembered values. Prefer a stable semantic key; this is historical evidence, not current-state inspection.",
                "session",
                "key",
                "Stable semantic key, or a short description of it.",
                this::searchTimeline
        ));
        register(registered, stringTool(
                "search_experience",
                "Strategy-only recall of externally verified prior AIlex outcomes. Use to choose a better retrieval/response approach; never cite this tool as factual evidence about a player or server.",
                "session",
                "query",
                "Situation or failure mode to recall.",
                this::searchExperience
        ));
        register(registered, stringTool(
                "search_knowledge",
                "Use for HauntedMC-specific commands, rules, ranks, systems and reviewed server facts. Query narrowly. Reviewed knowledge outranks player-learned shared claims but not live runtime state for current-state questions.",
                "knowledge",
                "query",
                "Focused knowledge query.",
                this::searchKnowledge
        ));
        register(registered, liveTool());
        tools = Map.copyOf(registered);
    }

    public List<JsonObject> definitions(AssistantSettings settings) {
        if (settings == null) {
            return List.of();
        }
        return tools.values().stream()
                .filter(tool -> tool.available(settings))
                .map(tool -> tool.definition(settings))
                .toList();
    }

    public AssistantTool.ToolResult execute(
            AssistantService.PreparedRequest request,
            OpenAiToolPlanningClient.FunctionCall call
    ) {
        if (request == null || call == null) {
            return AssistantTool.ToolResult.unavailable("Tool request unavailable.");
        }
        AssistantTool tool = tools.get(clean(call.name()));
        if (tool == null || !tool.available(request.settings())) {
            return AssistantTool.ToolResult.unavailable("Tool unavailable.");
        }
        JsonObject arguments = parseArguments(call.arguments());
        return tool.execute(request, arguments);
    }

    public Set<String> availableNames(AssistantSettings settings) {
        if (settings == null) {
            return Set.of();
        }
        return tools.values().stream()
                .filter(tool -> tool.available(settings))
                .map(AssistantTool::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private AssistantTool stringTool(
            String name,
            String description,
            String capability,
            String parameterName,
            String parameterDescription,
            BiFunction<AssistantService.PreparedRequest, String, AssistantTool.ToolResult> executor
    ) {
        return registeredTool(
                name,
                settings -> settings.toolAllowed(capability),
                ignored -> functionTool(
                        name,
                        description,
                        stringParameter(parameterName, parameterDescription),
                        Set.of(parameterName)
                ),
                (request, arguments) -> executor.apply(request, string(arguments, parameterName))
        );
    }

    private AssistantTool liveTool() {
        return registeredTool(
                "inspect_live",
                this::hasLiveCapability,
                settings -> functionTool(
                        "inspect_live",
                        "Use for current requester/world/server/NPC state when the answer depends on what is true now. The snapshot is frozen on the Paper thread; inspect only the source family materially needed.",
                        enumParameter("source", "Safe live source family.", liveSources(settings)),
                        Set.of("source")
                ),
                (request, arguments) -> inspectLive(request, string(arguments, "source"))
        );
    }

    private AssistantTool registeredTool(
            String name,
            Predicate<AssistantSettings> availability,
            Function<AssistantSettings, JsonObject> definition,
            BiFunction<AssistantService.PreparedRequest, JsonObject, AssistantTool.ToolResult> executor
    ) {
        return new AssistantTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean available(AssistantSettings settings) {
                return settings != null && availability.test(settings);
            }

            @Override
            public JsonObject definition(AssistantSettings settings) {
                return definition.apply(settings);
            }

            @Override
            public ToolResult execute(AssistantService.PreparedRequest request, JsonObject arguments) {
                return executor.apply(request, arguments);
            }
        };
    }

    private void register(Map<String, AssistantTool> target, AssistantTool tool) {
        if (target.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalStateException("Duplicate assistant tool: " + tool.name());
        }
    }

    private AssistantTool.ToolResult searchMemory(AssistantService.PreparedRequest request, String query) {
        if (memoryService == null || !request.settings().toolAllowed("session")) {
            return AssistantTool.ToolResult.unavailable("Memory unavailable.");
        }
        List<MemoryRecord> records = memoryService.search(
                UUID.fromString(request.playerId()), request.npcMemoryId(), query, Set.of(MemoryKind.values()), 12
        ).stream().filter(record -> !record.tags().contains("experience")).toList();
        return memoryObservation(records, false, true);
    }

    private AssistantTool.ToolResult searchTimeline(AssistantService.PreparedRequest request, String key) {
        if (memoryService == null || !request.settings().toolAllowed("session")) {
            return AssistantTool.ToolResult.unavailable("Memory timeline unavailable.");
        }
        List<MemoryRecord> records = memoryService.timeline(
                UUID.fromString(request.playerId()), request.npcMemoryId(), key, 16
        );
        return memoryObservation(records, true, true);
    }

    private AssistantTool.ToolResult searchExperience(AssistantService.PreparedRequest request, String query) {
        if (experienceMemory == null || !request.settings().toolAllowed("session")) {
            return AssistantTool.ToolResult.unavailable("Experience memory unavailable.");
        }
        List<MemoryRecord> records = experienceMemory.recall(
                UUID.fromString(request.playerId()), request.npcMemoryId(), query, 8
        );
        return memoryObservation(records, false, false);
    }

    private AssistantTool.ToolResult memoryObservation(
            List<MemoryRecord> records,
            boolean timeline,
            boolean playerFacingEvidence
    ) {
        if (records == null || records.isEmpty()) {
            String evidenceId = playerFacingEvidence
                    ? timeline ? "memory.timeline.none" : "memory.none"
                    : "";
            return evidenceId.isBlank()
                    ? new AssistantTool.ToolResult("No verified procedural experience matched.", Set.of())
                    : new AssistantTool.ToolResult(
                            "evidence_id=" + evidenceId + "\nNo relevant memory found.", Set.of(evidenceId)
                    );
        }
        StringBuilder output = new StringBuilder();
        Set<String> ids = new HashSet<>();
        for (MemoryRecord record : records) {
            String evidenceId = MemoryEvidenceId.forRecord(record);
            ids.add(evidenceId);
            output.append("evidence_id=").append(evidenceId)
                    .append(" scope=").append(record.scope().name().toLowerCase(Locale.ROOT))
                    .append(" kind=").append(record.kind().name().toLowerCase(Locale.ROOT))
                    .append(" key=").append(record.key())
                    .append(" value=").append(record.value())
                    .append(" confidence=").append(String.format(Locale.ROOT, "%.2f", record.confidence()));
            if (timeline) {
                output.append(" observed=").append(Instant.ofEpochMilli(record.firstObserved()))
                        .append(" confirmed=").append(Instant.ofEpochMilli(record.lastConfirmed()))
                        .append(" expires=")
                        .append(record.expiresAt() <= 0L ? "never" : Instant.ofEpochMilli(record.expiresAt()))
                        .append(" supersedes=").append(record.supersedes().isBlank() ? "none" : record.supersedes());
            }
            output.append('\n');
        }
        if (!playerFacingEvidence) {
            return new AssistantTool.ToolResult("strategy_only=true\n" + clip(output.toString()), Set.of());
        }
        return new AssistantTool.ToolResult(clip(output.toString()), Set.copyOf(ids));
    }

    private AssistantTool.ToolResult searchKnowledge(AssistantService.PreparedRequest request, String query) {
        if (knowledgeIndex == null || !request.settings().toolAllowed("knowledge") || query.isBlank()) {
            return AssistantTool.ToolResult.unavailable("Knowledge search unavailable.");
        }
        List<LocalKnowledgeIndex.KnowledgeChunk> chunks = knowledgeIndex.search(query, request.settings());
        if (chunks.isEmpty()) {
            return new AssistantTool.ToolResult(
                    "evidence_id=knowledge.none\nNo reviewed knowledge matched the focused query.",
                    Set.of("knowledge.none")
            );
        }
        StringBuilder output = new StringBuilder();
        Set<String> ids = new HashSet<>();
        for (LocalKnowledgeIndex.KnowledgeChunk chunk : chunks.stream().limit(8).toList()) {
            ids.add(chunk.id());
            output.append("evidence_id=").append(chunk.id()).append(" title=").append(chunk.title())
                    .append(" authority=").append(chunk.authority())
                    .append(" updated=").append(chunk.updated().isBlank() ? "unknown" : chunk.updated())
                    .append(" source=").append(chunk.source().isBlank() ? "reviewed-local" : chunk.source())
                    .append('\n').append(chunk.text()).append('\n');
        }
        return new AssistantTool.ToolResult(clip(output.toString()), Set.copyOf(ids));
    }

    private AssistantTool.ToolResult inspectLive(AssistantService.PreparedRequest request, String source) {
        String normalized = clean(source).toLowerCase(Locale.ROOT);
        if (!liveSourceAllowed(request.settings(), normalized)) {
            return AssistantTool.ToolResult.unavailable("Live source is not allowed.");
        }
        List<String> values = request.snapshot().values().stream()
                .filter(value -> liveValueMatches(value, normalized))
                .limit(64)
                .toList();
        if (values.isEmpty()) {
            String evidenceId = "live." + normalized + ".none";
            return new AssistantTool.ToolResult(
                    "evidence_id=" + evidenceId + "\nNo value was captured for this live source.", Set.of(evidenceId)
            );
        }
        String evidenceId = "live." + normalized;
        return new AssistantTool.ToolResult(
                "evidence_id=" + evidenceId + "\n" + String.join(" | ", values), Set.of(evidenceId)
        );
    }

    private boolean hasLiveCapability(AssistantSettings settings) {
        return !liveSources(settings).isEmpty();
    }

    private Set<String> liveSources(AssistantSettings settings) {
        Set<String> sources = new HashSet<>();
        if (settings.toolAllowed("requester")) {
            sources.add("requester");
            sources.add("inventory");
        }
        if (settings.toolAllowed("world")) {
            sources.add("world");
            sources.add("target");
        }
        if (settings.toolAllowed("nearby")) {
            sources.add("nearby");
        }
        if (settings.toolAllowed("server")) {
            sources.add("server");
        }
        if (settings.toolAllowed("npc")) {
            sources.add("npc");
        }
        return Set.copyOf(sources);
    }

    private boolean liveSourceAllowed(AssistantSettings settings, String source) {
        return switch (source) {
            case "requester", "inventory" -> settings.toolAllowed("requester");
            case "world", "target" -> settings.toolAllowed("world");
            case "nearby" -> settings.toolAllowed("nearby");
            case "server" -> settings.toolAllowed("server");
            case "npc" -> settings.toolAllowed("npc");
            default -> false;
        };
    }

    private boolean liveValueMatches(String value, String source) {
        String key = value == null ? "" : value.substring(0, Math.max(0, value.indexOf('=') < 0
                ? value.length() : value.indexOf('='))).trim().toLowerCase(Locale.ROOT);
        return switch (source) {
            case "inventory" -> key.startsWith("player_inventory_") || key.startsWith("player_armor")
                    || key.startsWith("player_selected_hotbar");
            case "world" -> key.startsWith("player_biome") || key.startsWith("player_position")
                    || key.startsWith("player_facing") || key.startsWith("player_light")
                    || key.startsWith("player_block") || key.startsWith("block_below")
                    || key.startsWith("world_") || key.equals("weather");
            case "target" -> key.startsWith("target_");
            case "server" -> key.startsWith("server_");
            case "nearby" -> key.startsWith("nearby_");
            case "npc" -> key.startsWith("bot_") || key.startsWith("npc_");
            case "requester" -> key.startsWith("player_") && !liveValueMatches(value, "inventory")
                    && !liveValueMatches(value, "world");
            default -> false;
        };
    }

    private JsonObject parseArguments(String raw) {
        try {
            JsonElement parsed = JsonParser.parseString(raw == null ? "{}" : raw);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    private JsonObject functionTool(
            String name,
            String description,
            JsonObject property,
            Set<String> required
    ) {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        tool.addProperty("strict", true);
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        properties.add(property.get("name").getAsString(), property.getAsJsonObject("schema"));
        parameters.add("properties", properties);
        JsonArray requiredArray = new JsonArray();
        required.forEach(requiredArray::add);
        parameters.add("required", requiredArray);
        parameters.addProperty("additionalProperties", false);
        tool.add("parameters", parameters);
        return tool;
    }

    private JsonObject stringParameter(String name, String description) {
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        schema.addProperty("description", description);
        result.add("schema", schema);
        return result;
    }

    private JsonObject enumParameter(String name, String description, Set<String> values) {
        JsonObject result = stringParameter(name, description);
        JsonArray allowed = new JsonArray();
        values.stream().sorted().forEach(allowed::add);
        result.getAsJsonObject("schema").add("enum", allowed);
        return result;
    }

    private String string(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? clean(object.get(key).getAsString()) : "";
    }

    private String clip(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= MAX_OBSERVATION_CHARACTERS
                ? normalized : normalized.substring(0, MAX_OBSERVATION_CHARACTERS - 1) + "…";
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
