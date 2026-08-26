package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryTemporalQueryTest {

    @Test
    void yesterdayExcludesCurrentAndOlderEvents() {
        long now = System.currentTimeMillis();
        MemoryTemporalQuery query = MemoryTemporalQuery.parse("what happened yesterday?", now);

        assertTrue(query.constrained());
        assertTrue(query.matches(event("yesterday", now - Duration.ofHours(24).toMillis())));
        assertFalse(query.matches(event("current", now)));
        assertFalse(query.matches(event("old", now - Duration.ofDays(5).toMillis())));
    }

    @Test
    void ordinarySemanticQueryRemainsTemporallyUnconstrained() {
        long now = System.currentTimeMillis();
        MemoryTemporalQuery query = MemoryTemporalQuery.parse("my castle project", now);

        assertFalse(query.constrained());
        assertTrue(query.matches(event("old", now - Duration.ofDays(200).toMillis())));
    }

    private static MemoryRecord event(String id, long occurredAt) {
        return new MemoryRecord(
                id, MemoryScope.EVENT, "player", "", MemoryKind.EVENT, "event." + id, id,
                1.0D, 0.8D, "test", id, occurredAt, occurredAt, occurredAt, 0L, "", Set.of("event")
        );
    }
}
