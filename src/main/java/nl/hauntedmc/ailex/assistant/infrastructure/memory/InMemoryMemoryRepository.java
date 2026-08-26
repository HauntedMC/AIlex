package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Non-persistent safety fallback used only when durable storage cannot initialize. */
final class InMemoryMemoryRepository implements MemoryRepository {

    private final Map<String, MemoryRecord> records = new ConcurrentHashMap<>();

    @Override
    public void initialize() {
        // Nothing to initialize.
    }

    @Override
    public List<MemoryRecord> loadActive(long now) {
        return records.values().stream().filter(record -> record.activeAt(now)).toList();
    }

    @Override
    public List<MemoryRecord> loadTimeline(String subjectId, String relationId, String key, int limit) {
        int maximum = Math.clamp(limit, 1, 128);
        String subject = clean(subjectId);
        String relation = clean(relationId);
        String memoryKey = clean(key);
        return records.values().stream()
                .filter(record -> subject.isBlank() || record.subjectId().equals(subject))
                .filter(record -> relation.isBlank() || record.relationId().equals(relation))
                .filter(record -> memoryKey.isBlank() || record.key().equals(memoryKey))
                .sorted(Comparator.comparingLong(MemoryRecord::lastConfirmed).reversed())
                .limit(maximum)
                .toList();
    }

    @Override
    public List<MemoryRecord> loadChangedSince(long sinceEpochMillis, int limit) {
        return records.values().stream()
                .filter(record -> record.lastConfirmed() > sinceEpochMillis || record.expiresAt() > sinceEpochMillis)
                .sorted(Comparator.comparingLong(this::changeClock))
                .limit(Math.clamp(limit, 1, 2_048))
                .toList();
    }

    @Override
    public void upsert(MemoryRecord record) {
        records.put(record.id(), record);
    }

    @Override
    public void deleteExpiredBefore(long cutoffEpochMillis) {
        new ArrayList<>(records.entrySet()).forEach(entry -> {
            long expiresAt = entry.getValue().expiresAt();
            if (expiresAt > 0L && expiresAt < cutoffEpochMillis) {
                records.remove(entry.getKey(), entry.getValue());
            }
        });
    }

    @Override
    public void close() {
        records.clear();
    }

    private long changeClock(MemoryRecord record) {
        return Math.max(record.lastConfirmed(), record.expiresAt());
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
