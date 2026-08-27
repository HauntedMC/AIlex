package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Compact topic-structured view over retrieved memory. It reduces repeated key/value prompt material while preserving
 * exact memory evidence identifiers so summarized organization never becomes an untraceable new source.
 */
public final class MemoryTopicView {

    /**
     * Groups an already-ranked memory result set into bounded prompt topics.
     * Grouping changes presentation only: records retain their original evidence identity and provenance.
     */
    public List<Topic> organize(List<MemoryRecord> records, int maximumTopics, int maximumItemsPerTopic) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<String, List<MemoryRecord>> groups = new LinkedHashMap<>();
        records.stream()
                .sorted(Comparator.comparingDouble(MemoryRecord::salience).reversed()
                        .thenComparing(Comparator.comparingLong(MemoryRecord::lastConfirmed).reversed()))
                .forEach(record -> groups.computeIfAbsent(topic(record), ignored -> new ArrayList<>()).add(record));
        return groups.entrySet().stream()
                .map(entry -> new Topic(
                        entry.getKey(),
                        entry.getValue().stream().limit(Math.clamp(maximumItemsPerTopic, 1, 12)).toList()
                ))
                .sorted(Comparator.comparingDouble(Topic::maximumSalience).reversed())
                .limit(Math.clamp(maximumTopics, 1, 16))
                .toList();
    }

    /**
     * Renders topic-organized memory for model context while retaining every underlying typed memory citation.
     * The character limit is a hard prompt-size ceiling and never causes a synthetic summary to become new evidence.
     */
    public String render(List<MemoryRecord> records, int maximumCharacters) {
        StringBuilder output = new StringBuilder();
        for (Topic topic : organize(records, 10, 5)) {
            if (!output.isEmpty()) {
                output.append('\n');
            }
            output.append("topic=").append(topic.name()).append(':');
            for (MemoryRecord record : topic.records()) {
                output.append(" [evidence_id=").append(MemoryEvidenceId.forRecord(record))
                        .append(" ").append(record.key()).append('=').append(record.value()).append(']');
                if (output.length() >= maximumCharacters) {
                    return output.substring(0, Math.max(0, maximumCharacters - 1)) + "…";
                }
            }
        }
        return output.toString();
    }

    private String topic(MemoryRecord record) {
        if (record.tags().contains("experience")) {
            return "experience";
        }
        String key = record.key().toLowerCase(Locale.ROOT);
        int separator = key.indexOf('.');
        if (separator > 1) {
            return key.substring(0, separator);
        }
        Set<String> preferred = Set.of("project", "goal", "language", "gamemode", "build", "economy", "social",
                "event", "preference", "interest", "relationship");
        for (String tag : record.tags()) {
            if (preferred.contains(tag)) {
                return tag;
            }
        }
        return record.kind().name().toLowerCase(Locale.ROOT);
    }

    /** One presentation group; contained records remain the authoritative evidence units. */
    public record Topic(String name, List<MemoryRecord> records) {
        public Topic {
            name = name == null || name.isBlank() ? "general" : name;
            records = records == null ? List.of() : List.copyOf(records);
        }

        public double maximumSalience() {
            return records.stream().mapToDouble(MemoryRecord::salience).max().orElse(0.0D);
        }
    }
}
