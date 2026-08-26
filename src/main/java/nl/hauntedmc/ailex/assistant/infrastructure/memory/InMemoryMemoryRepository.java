package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.ArrayList;
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
}
