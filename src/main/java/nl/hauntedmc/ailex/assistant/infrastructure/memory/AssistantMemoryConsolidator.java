package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic memory consolidation. It compresses repeated, already-trusted events into bounded episode summaries;
 * it never asks a model to invent a summary and never promotes ordinary chat merely because it was observed often.
 */
public final class AssistantMemoryConsolidator {

    private static final int MINIMUM_EVENTS = 3;
    private static final int MAX_GROUPS_PER_RUN = 24;
    private static final long LOOKBACK_MILLIS = Duration.ofDays(21).toMillis();
    private static final Duration EPISODE_TTL = Duration.ofDays(180);

    private final AssistantMemoryService memory;

    public AssistantMemoryConsolidator(AssistantMemoryService memory) {
        this.memory = memory;
    }

    /**
     * Consolidates sufficiently repeated active events into deterministic episode records.
     *
     * <p>The operation reads only already-trusted event memory and writes through the normal durable-memory safety
     * boundary. Absolute timestamps stay in typed record metadata instead of being copied into the free-text summary,
     * which avoids both redundant context and accidental identifier-like data shapes.</p>
     *
     * @return counts describing the snapshot, candidate groups, and successfully stored episodes
     */
    public ConsolidationReport consolidate() {
        if (memory == null) {
            return new ConsolidationReport(0, 0, 0);
        }
        long now = System.currentTimeMillis();
        List<MemoryRecord> snapshot = memory.activeSnapshot();
        Set<String> existingKeys = snapshot.stream()
                .filter(record -> record.kind() == MemoryKind.EPISODE && record.tags().contains("consolidated"))
                .map(record -> record.scope() + "|" + record.subjectId() + "|" + record.relationId() + "|" + record.key())
                .collect(java.util.stream.Collectors.toSet());
        Map<GroupKey, List<MemoryRecord>> groups = new HashMap<>();
        for (MemoryRecord record : snapshot) {
            if (record.kind() != MemoryKind.EVENT || record.tags().contains("consolidated")) {
                continue;
            }
            long occurred = record.occurredAt() > 0L ? record.occurredAt() : record.lastConfirmed();
            if (occurred <= 0L || now - occurred > LOOKBACK_MILLIS) {
                continue;
            }
            String topic = primaryTopic(record);
            if (topic.isBlank()) {
                continue;
            }
            groups.computeIfAbsent(new GroupKey(record.subjectId(), record.relationId(), topic), ignored -> new ArrayList<>())
                    .add(record);
        }

        int considered = 0;
        int created = 0;
        for (Map.Entry<GroupKey, List<MemoryRecord>> entry : groups.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<GroupKey, List<MemoryRecord>> value) -> value.getValue().size())
                        .reversed())
                .limit(MAX_GROUPS_PER_RUN)
                .toList()) {
            List<MemoryRecord> events = entry.getValue().stream()
                    .sorted(Comparator.comparingLong(record -> record.occurredAt() > 0L
                            ? record.occurredAt() : record.lastConfirmed()))
                    .toList();
            if (events.size() < MINIMUM_EVENTS) {
                continue;
            }
            considered++;
            long first = eventTime(events.getFirst());
            long last = eventTime(events.getLast());
            String key = episodeKey(entry.getKey(), last);
            String identity = MemoryScope.EVENT + "|" + entry.getKey().subjectId() + "|"
                    + entry.getKey().relationId() + "|" + key;
            if (existingKeys.contains(identity)) {
                continue;
            }
            Set<String> tags = new HashSet<>();
            tags.add("consolidated");
            tags.add("episode-summary");
            tags.add(entry.getKey().topic());
            events.stream().flatMap(record -> record.tags().stream()).limit(16).forEach(tags::add);
            MemoryRecord episode = memory.rememberTrusted(
                    MemoryScope.EVENT,
                    entry.getKey().subjectId(),
                    entry.getKey().relationId(),
                    MemoryKind.EPISODE,
                    key,
                    episodeSummary(entry.getKey().topic(), events, first, last),
                    1.0D,
                    Math.min(0.96D, 0.65D + events.size() * 0.04D),
                    "runtime-consolidation",
                    evidenceSource(events),
                    last,
                    EPISODE_TTL,
                    Set.copyOf(tags)
            );
            if (episode != null) {
                created++;
                existingKeys.add(identity);
            }
        }
        return new ConsolidationReport(snapshot.size(), considered, created);
    }

    private String episodeSummary(String topic, List<MemoryRecord> events, long first, long last) {
        List<String> unique = events.stream().map(MemoryRecord::value).distinct().limit(3).toList();
        String details = String.join("; ", unique);
        String value = "Repeated " + topic + " episode (" + events.size() + " events over "
                + compactDuration(Math.max(0L, last - first)) + "): " + details;
        return value.length() <= 320 ? value : value.substring(0, 319) + "…";
    }

    private String compactDuration(long durationMillis) {
        Duration duration = Duration.ofMillis(durationMillis);
        long hours = duration.toHours();
        if (hours >= 48L) {
            return duration.toDays() + " days";
        }
        if (hours >= 1L) {
            return hours + " hours";
        }
        return Math.max(1L, duration.toMinutes()) + " minutes";
    }

    private String evidenceSource(List<MemoryRecord> events) {
        return events.stream().map(MemoryRecord::id).limit(8).reduce((left, right) -> left + ',' + right).orElse("events");
    }

    private String episodeKey(GroupKey group, long last) {
        java.time.ZonedDateTime date = Instant.ofEpochMilli(last).atZone(ZoneOffset.UTC);
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        String stable = (group.subjectId() + '|' + group.relationId() + '|' + group.topic()).toLowerCase(Locale.ROOT);
        return "episode.summary." + Integer.toHexString(stable.hashCode()) + '.' + date.getYear() + "w" + week;
    }

    private String primaryTopic(MemoryRecord record) {
        Set<String> valueTerms = topicTerms(record.value());
        String anchoredTopic = topicTerms(record.key()).stream()
                .filter(valueTerms::contains)
                .filter(tag -> !Set.of("event", "session", "join", "quit", "world", "project").contains(tag))
                .sorted()
                .findFirst()
                .orElse("");
        if (!anchoredTopic.isBlank()) {
            return anchoredTopic;
        }
        String specificTag = record.tags().stream()
                .filter(tag -> !Set.of("event", "session", "join", "quit", "world", "project").contains(tag))
                .filter(tag -> !tag.startsWith("world:"))
                .map(this::safeTopic)
                .filter(tag -> !tag.isBlank())
                .sorted()
                .findFirst()
                .orElse("");
        if (!specificTag.isBlank()) {
            return specificTag;
        }
        String key = record.key();
        int separator = key.indexOf('.');
        return safeTopic(separator > 0 ? key.substring(0, separator) : key);
    }

    private Set<String> topicTerms(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(term -> term.length() >= 3)
                .map(this::safeTopic)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String safeTopic(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-");
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48);
    }

    private long eventTime(MemoryRecord record) {
        return record.occurredAt() > 0L ? record.occurredAt() : record.lastConfirmed();
    }

    public record ConsolidationReport(int activeRecords, int candidateGroups, int episodesCreated) {
    }

    private record GroupKey(String subjectId, String relationId, String topic) {
    }
}
