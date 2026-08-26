package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.List;

/** Durable storage abstraction so the assistant runtime never depends directly on one database implementation. */
public interface MemoryRepository extends AutoCloseable {

    /** Initializes schema/storage and may be called repeatedly. */
    void initialize();

    /** Loads records that are still active at the supplied epoch-millis clock. */
    List<MemoryRecord> loadActive(long now);

    /** Inserts or replaces one immutable memory version. */
    void upsert(MemoryRecord record);

    /** Deletes old expired versions while keeping a bounded history window. */
    void deleteExpiredBefore(long cutoffEpochMillis);

    @Override
    void close();
}
