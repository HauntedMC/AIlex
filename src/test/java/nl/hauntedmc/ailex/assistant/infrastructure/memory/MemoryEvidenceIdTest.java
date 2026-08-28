package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryEvidenceIdTest {

    @Test
    void encodesTypedFamiliesAndReadsLegacyIds() {
        assertEquals("memory.event.event-id", MemoryEvidenceId.forRecord(record(
                "event-id", MemoryScope.EVENT, MemoryKind.EVENT
        )));
        assertEquals("memory.episode.episode-id", MemoryEvidenceId.forRecord(record(
                "episode-id", MemoryScope.EVENT, MemoryKind.EPISODE
        )));
        assertEquals("memory.shared.shared-id", MemoryEvidenceId.forRecord(record(
                "shared-id", MemoryScope.GLOBAL, MemoryKind.FACT
        )));
        assertEquals("memory.player.player-id", MemoryEvidenceId.forRecord(record(
                "player-id", MemoryScope.PLAYER, MemoryKind.PREFERENCE
        )));

        assertEquals("event-id", MemoryEvidenceId.recordId("memory.event.event-id"));
        assertEquals("legacy-id", MemoryEvidenceId.recordId("memory.legacy-id"));
    }

    private MemoryRecord record(String id, MemoryScope scope, MemoryKind kind) {
        return new MemoryRecord(
                id, scope, "subject", "relation", kind, "key", "value", 1.0D, 1.0D,
                "test", "test", 1L, 1L, 1L, 0L, "", Set.of()
        );
    }
}
