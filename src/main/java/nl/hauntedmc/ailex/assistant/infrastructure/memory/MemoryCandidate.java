package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.assistant.security.AssistantDataSafety;

import java.util.Locale;

/**
 * Structured, auditable memory proposal emitted by the assistant for an explicit player statement.
 * Candidate values are never persisted blindly: {@link AssistantMemoryService} validates source support,
 * sensitivity, scope permissions and semantic-key conflicts before accepting them.
 */
public record MemoryCandidate(
        String scope,
        String kind,
        String key,
        String value,
        String operation
) {
    public MemoryCandidate {
        scope = normalize(scope, "player");
        kind = normalize(kind, "fact");
        key = clean(key).toLowerCase(Locale.ROOT).replace(' ', '_');
        value = clean(value);
        operation = normalize(operation, "upsert");
        if (AssistantDataSafety.forbiddenDurableMemory(key, value)) {
            key = "";
            value = "";
        }
    }

    public boolean forget() {
        return "forget".equals(operation);
    }

    private static String normalize(String value, String fallback) {
        String normalized = clean(value).toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
