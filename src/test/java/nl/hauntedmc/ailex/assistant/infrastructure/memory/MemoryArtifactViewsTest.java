package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryArtifactViewsTest {

    @Test
    void eventAndEpisodePreserveTimingAndEvidenceLineage() {
        MemoryEvent first = MemoryEvent.from(record(
                "e1", MemoryScope.EVENT, MemoryKind.EVENT, "player", "", "project.start", "Started castle",
                1_000L, 0L, "event-listener", "project"
        ));
        MemoryEvent second = MemoryEvent.from(record(
                "e2", MemoryScope.EVENT, MemoryKind.EVENT, "player", "", "project.progress", "Built castle roof",
                2_000L, 0L, "event-listener", "project"
        ));

        MemoryEpisode episode = MemoryEpisode.of("episode.castle", "Castle project", List.of(second, first));

        assertEquals(1_000L, episode.firstOccurredAt());
        assertEquals(2_000L, episode.lastOccurredAt());
        assertEquals(List.of("e1", "e2"), episode.events().stream().map(MemoryEvent::id).toList());
        assertTrue(episode.evidenceIds().contains("memory.e1"));
        assertTrue(episode.evidenceIds().contains("memory.e2"));
    }

    @Test
    void relationshipEdgeCarriesAuthorityAndValidity() {
        MemoryRecord relationship = record(
                "r1", MemoryScope.PLAYER_NPC, MemoryKind.RELATIONSHIP, "player", "1", "interaction_count", "4",
                5_000L, 10_000L, "assistant-runtime", "accepted-chat"
        );

        MemoryEdge edge = MemoryEdge.from(relationship);

        assertEquals("player", edge.from());
        assertEquals("1", edge.to());
        assertEquals("interaction_count", edge.predicate());
        assertTrue(edge.authority() > 0.9D);
        assertTrue(edge.validAt(6_000L));
        assertFalse(edge.validAt(10_000L));
        assertTrue(edge.evidenceIds().contains("memory.r1"));
    }

    private static MemoryRecord record(
            String id,
            MemoryScope scope,
            MemoryKind kind,
            String subject,
            String relation,
            String key,
            String value,
            long occurredAt,
            long expiresAt,
            String sourceType,
            String sourceId
    ) {
        return new MemoryRecord(
                id, scope, subject, relation, kind, key, value, 0.95D, 0.8D,
                sourceType, sourceId, occurredAt, occurredAt, occurredAt, expiresAt, "", Set.of("test")
        );
    }
}
