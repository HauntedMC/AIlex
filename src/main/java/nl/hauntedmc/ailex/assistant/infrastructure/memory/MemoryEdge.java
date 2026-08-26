package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Explicit graph edge derived from a relational memory row. This keeps graph retrieval separate from event storage and
 * exposes the evidence, authority and validity needed for future associative traversal.
 */
public record MemoryEdge(
        String id,
        String from,
        String to,
        String predicate,
        String object,
        double authority,
        double confidence,
        long validFrom,
        long validUntil,
        Set<String> evidenceIds,
        Set<String> tags
) {
    public MemoryEdge {
        id = clean(id);
        from = clean(from);
        to = clean(to);
        predicate = clean(predicate).toLowerCase(Locale.ROOT);
        object = clean(object);
        authority = Math.clamp(authority, 0.0D, 1.0D);
        confidence = Math.clamp(confidence, 0.0D, 1.0D);
        evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public static MemoryEdge from(MemoryRecord record) {
        if (record == null || record.relationId().isBlank()) {
            throw new IllegalArgumentException("relational memory record is required");
        }
        MemoryClaim claim = MemoryClaim.from(record, System.currentTimeMillis());
        Set<String> evidence = new HashSet<>(claim.evidenceIds());
        evidence.add("memory." + record.id());
        long validFrom = record.occurredAt() > 0L ? record.occurredAt() : record.firstObserved();
        return new MemoryEdge(
                record.id(), record.subjectId(), record.relationId(), record.key(), record.value(), claim.authority(),
                record.confidence(), validFrom, record.expiresAt(), Set.copyOf(evidence), record.tags()
        );
    }

    public boolean validAt(long epochMillis) {
        return validFrom <= epochMillis && (validUntil <= 0L || validUntil > epochMillis);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
