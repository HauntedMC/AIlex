package nl.hauntedmc.ailex.assistant.application.agent;

import com.google.gson.JsonObject;
import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import java.util.Set;

/**
 * One deterministic read-only capability exposed to the bounded AIlex planner.
 * Implementations own their schema, permission predicate and execution path; no model output can bypass this boundary.
 */
public interface AssistantTool {

    String name();

    boolean available(AssistantSettings settings);

    JsonObject definition(AssistantSettings settings);

    ToolResult execute(AssistantService.PreparedRequest request, JsonObject arguments);

    record ToolResult(String output, Set<String> evidenceIds) {
        public ToolResult {
            output = output == null ? "" : output.trim();
            evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        }

        public static ToolResult unavailable(String message) {
            return new ToolResult(message, Set.of());
        }
    }
}
