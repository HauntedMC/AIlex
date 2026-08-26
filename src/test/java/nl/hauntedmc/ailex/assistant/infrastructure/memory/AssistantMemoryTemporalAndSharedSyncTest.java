package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantMemoryTemporalAndSharedSyncTest {

    @Test
    void correctedValueStartsAtCorrectionTimeInsteadOfRewritingHistory() throws Exception {
        FakeRepository repository = new FakeRepository(false);
        try (AssistantMemoryService service = new AssistantMemoryService(plugin(), repository)) {
            UUID player = UUID.randomUUID();
            service.rememberCandidate(
                    player, "Player",
                    new MemoryCandidate("player", "preference", "favorite_gamemode", "survival", "upsert"),
                    "Mijn favoriete gamemode is survival", false
            );
            Thread.sleep(5L);
            service.rememberCandidate(
                    player, "Player",
                    new MemoryCandidate("player", "preference", "favorite_gamemode", "creative", "upsert"),
                    "Mijn favoriete gamemode is creative", false
            );
            service.flush();

            List<MemoryRecord> timeline = service.timeline(player, "1", "favorite_gamemode", 8);
            assertEquals(2, timeline.size());
            MemoryRecord newest = timeline.getFirst();
            MemoryRecord oldest = timeline.getLast();
            assertEquals("creative", newest.value());
            assertEquals("survival", oldest.value());
            assertTrue(newest.firstObserved() > oldest.firstObserved());
            assertEquals("survival", service.resolveClaims(
                    player, "1", "favorite_gamemode", oldest.firstObserved(), 1
            ).getFirst().primary().object());
            assertEquals("creative", service.resolveClaims(
                    player, "1", "favorite_gamemode", System.currentTimeMillis(), 1
            ).getFirst().primary().object());
        }
    }

    @Test
    void sharedReadsUseHotIndexAndSequenceSyncPagesWithoutDroppingBursts() throws Exception {
        FakeRepository repository = new FakeRepository(true);
        try (AssistantMemoryService service = new AssistantMemoryService(plugin(), repository)) {
            Thread.sleep(100L);
            int baselineReads = repository.changeReads.get();
            service.search(UUID.randomUUID(), "1", "anything", Set.of(MemoryKind.FACT), 8);
            assertEquals(baselineReads, repository.changeReads.get(), "player read path must not poll MySQL");

            for (int index = 1; index <= 2_050; index++) {
                repository.addChange(index, record(index));
            }
            int beforeSync = repository.changeReads.get();
            service.synchronizeSharedMemoryNow();

            assertEquals(beforeSync + 2, repository.changeReads.get());
            assertEquals(2_050, service.activeSnapshot().size());
        }
    }

    private static JavaPlugin plugin() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.memory.enabled", true);
        config.set("openai.assistant.memory.storage.shared_sync_seconds", 60);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        return plugin;
    }

    private static MemoryRecord record(int index) {
        long now = System.currentTimeMillis();
        return new MemoryRecord(
                "00000000-0000-0000-0000-" + String.format("%012d", index),
                MemoryScope.GLOBAL, "", "", MemoryKind.FACT,
                "fact." + index, "value " + index, 0.99D, 0.8D,
                "runtime-trusted", "test", now, now, 0L, 0L, "", Set.of("test")
        );
    }

    private static final class FakeRepository implements MemoryRepository {
        private final boolean shared;
        private final Map<String, MemoryRecord> records = new ConcurrentHashMap<>();
        private final List<SharedChange> changes = new ArrayList<>();
        private final AtomicInteger changeReads = new AtomicInteger();

        private FakeRepository(boolean shared) {
            this.shared = shared;
        }

        void addChange(long sequence, MemoryRecord record) {
            records.put(record.id(), record);
            synchronized (changes) {
                changes.add(new SharedChange(sequence, record));
            }
        }

        @Override
        public void initialize() {
        }

        @Override
        public List<MemoryRecord> loadActive(long now) {
            return records.values().stream().filter(record -> record.activeAt(now)).toList();
        }

        @Override
        public List<MemoryRecord> loadTimeline(String subjectId, String relationId, String key, int limit) {
            return records.values().stream()
                    .filter(record -> subjectId.isBlank() || record.subjectId().equals(subjectId))
                    .filter(record -> relationId.isBlank() || record.relationId().equals(relationId))
                    .filter(record -> key.isBlank() || record.key().equals(key))
                    .sorted(Comparator.comparingLong(MemoryRecord::lastConfirmed).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public long latestChangeSequence() {
            synchronized (changes) {
                return changes.stream().mapToLong(SharedChange::sequence).max().orElse(0L);
            }
        }

        @Override
        public List<SharedChange> loadChangesAfter(long sequence, int limit) {
            changeReads.incrementAndGet();
            synchronized (changes) {
                return changes.stream()
                        .filter(change -> change.sequence() > sequence)
                        .sorted(Comparator.comparingLong(SharedChange::sequence))
                        .limit(limit)
                        .toList();
            }
        }

        @Override
        public boolean shared() {
            return shared;
        }

        @Override
        public void upsert(MemoryRecord record) {
            records.put(record.id(), record);
        }

        @Override
        public void deleteExpiredBefore(long cutoffEpochMillis) {
        }

        @Override
        public void close() {
        }
    }
}
