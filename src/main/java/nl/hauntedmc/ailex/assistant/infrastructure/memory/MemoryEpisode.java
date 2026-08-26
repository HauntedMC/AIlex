package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordered aggregate of related observations. Episodes are a retrieval/consolidation view and do not replace the
 * immutable event rows that preserve provenance and timing.
 */
public record MemoryEpisode(
        String id,
        String subjectId,
        String summary,
        List<MemoryEvent> events,
        long firstOccurredAt,
        long lastOccurredAt,
        Set<String> evidenceIds,
        Set<String> tags
) {
    public MemoryEpisode {
        id = clean(id);
        subjectId = clean(subjectId);
        summary = clean(summary);
        events = events == null ? List.of() : events.stream()
                .sorted(Comparator.comparingLong(MemoryEvent::occurredAt))
                .toList();
        evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public static MemoryEpisode of(String id, String summary, List<MemoryEvent> events) {
        List<MemoryEvent> ordered = events == null ? List.of() : events.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingLong(MemoryEvent::occurredAt))
                .toList();
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("episode requires at least one event");
        }
        String subject = ordered.getFirst().subjectId();
        if (ordered.stream().anyMatch(event -> !event.subjectId().equals(subject))) {
            throw new IllegalArgumentException("episode events must share one subject");
        }
        Set<String> evidence = new HashSet<>();
        Set<String> tags = new HashSet<>();
        ordered.forEach(event -> {
            evidence.addAll(event.evidenceIds());
            tags.addAll(event.tags());
        });
        return new MemoryEpisode(
                id,
                subject,
                summary,
                ordered,
                ordered.getFirst().occurredAt(),
                ordered.getLast().occurredAt(),
                Set.copyOf(evidence),
                Set.copyOf(tags)
        );
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
