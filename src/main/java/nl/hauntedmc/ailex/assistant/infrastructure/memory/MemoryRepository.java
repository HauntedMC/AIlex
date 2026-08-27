package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.List;

/** Durable storage abstraction so the assistant runtime never depends directly on one database implementation. */
public interface MemoryRepository extends AutoCloseable {

    /** Initializes schema/storage and may be called repeatedly. */
    void initialize();

    /** Loads records that are still active at the supplied epoch-millis clock. */
    List<MemoryRecord> loadActive(long now);

    /** Loads historical versions for temporal questions, newest-first and bounded by the requested limit. */
    List<MemoryRecord> loadTimeline(String subjectId, String relationId, String key, int limit);

    /** Monotonic repository-owned change cursor. Wall-clock timestamps are not safe cross-runtime cursors. */
    default long latestChangeSequence() {
        return 0L;
    }

    /** Loads ordered shared changes strictly after the supplied cursor. */
    default List<SharedChange> loadChangesAfter(long sequence, int limit) {
        return List.of();
    }

    /** Whether this repository is shared between simultaneously running AIlex instances. */
    default boolean shared() {
        return false;
    }

    /** Inserts or replaces one immutable memory version. */
    void upsert(MemoryRecord record);

    /** Deletes old expired versions while keeping a bounded history window. */
    void deleteExpiredBefore(long cutoffEpochMillis);

    @Override
    void close();

    /** One ordered shared-memory mutation. */
    record SharedChange(long sequence, MemoryRecord record) {
        public SharedChange {
            sequence = Math.max(0L, sequence);
            if (record == null) {
                throw new IllegalArgumentException("record is required");
            }
        }
    }
}
