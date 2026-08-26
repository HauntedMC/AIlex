package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Typed observation view over a durable event record. Events describe what happened; they are deliberately distinct
 * from {@link MemoryClaim}, which describes what AIlex currently believes to be true.
 */
public record MemoryEvent(
        String id,
        String subjectId,
        String relationId,
        String eventType,
        String summary,
        long occurredAt,
        long validUntil,
        String sourceType,
        String sourceId,
        Set<String> evidenceIds,
        Set<String> tags
) {
    public MemoryEvent {
        id = clean(id);
        subjectId = clean(subjectId);
        relationId = clean(relationId);
        eventType = clean(eventType).toLowerCase(Locale.ROOT);
        summary = clean(summary);
        sourceType = clean(sourceType).toLowerCase(Locale.ROOT);
        sourceId = clean(sourceId);
        evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public static MemoryEvent from(MemoryRecord record) {
        if (record == null || (record.kind() != MemoryKind.EVENT && record.kind() != MemoryKind.EPISODE)) {
            throw new IllegalArgumentException("event or episode memory record is required");
        }
        Set<String> evidence = new HashSet<>();
        evidence.add("memory." + record.id());
        if (!record.sourceId().isBlank()) {
            evidence.add("source." + safeEvidenceId(record.sourceId()));
        }
        long occurred = record.occurredAt() > 0L ? record.occurredAt() : record.firstObserved();
        return new MemoryEvent(
                record.id(), record.subjectId(), record.relationId(), record.key(), record.value(), occurred,
                record.expiresAt(), record.sourceType(), record.sourceId(), Set.copyOf(evidence), record.tags()
        );
    }

    public boolean activeAt(long epochMillis) {
        return validUntil <= 0L || validUntil > epochMillis;
    }

    private static String safeEvidenceId(String value) {
        String safe = clean(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]+", "-");
        return safe.isBlank() ? "unknown" : safe;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
