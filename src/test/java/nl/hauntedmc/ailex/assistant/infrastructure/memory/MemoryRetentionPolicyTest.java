package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRetentionPolicyTest {

    private final MemoryRetentionPolicy policy = new MemoryRetentionPolicy();

    @Test
    void durableSemanticMemoryDoesNotDisappearMerelyBecauseItIsOld() {
        long now = Duration.ofDays(500).toMillis();
        MemoryRecord preference = record(
                "pref", MemoryScope.PLAYER, MemoryKind.PREFERENCE, "favorite_gamemode", "survival",
                1L, 1L, 0L, 0.92D, 0.80D, Set.of("semantic")
        );

        assertEquals(MemoryLifecycleStage.MATURE, policy.stage(preference, now));
        assertFalse(policy.shouldExpire(preference, now, List.of(preference)));
    }

    @Test
    void staleLowValueEventCanDecayUnderInterference() {
        long now = Duration.ofDays(90).toMillis();
        MemoryRecord event = record(
                "old", MemoryScope.EVENT, MemoryKind.EVENT, "event.build", "minor build event",
                1L, 1L, 1L, 0.45D, 0.15D, Set.of("event", "build")
        );
        MemoryRecord competing = record(
                "new", MemoryScope.EVENT, MemoryKind.EVENT, "event.build.new", "newer build event",
                now - Duration.ofDays(1).toMillis(), now - Duration.ofDays(1).toMillis(),
                now - Duration.ofDays(1).toMillis(), 0.95D, 0.80D, Set.of("event", "build")
        );

        assertTrue(policy.shouldExpire(event, now, List.of(event, competing)));
    }

    @Test
    void verifiedReuseReactivatesSalienceWithoutChangingConfidenceOrContent() {
        long now = 10_000L;
        MemoryRecord original = record(
                "episode", MemoryScope.EVENT, MemoryKind.EPISODE, "episode.project", "Built the farm",
                1_000L, 2_000L, 1_000L, 0.88D, 0.65D, Set.of("consolidated")
        );

        MemoryRecord updated = policy.reconsolidateVerifiedUse(original, now);

        assertEquals(original.value(), updated.value());
        assertEquals(original.confidence(), updated.confidence());
        assertTrue(updated.salience() > original.salience());
        assertEquals(now, updated.lastConfirmed());
    }

    private static MemoryRecord record(
            String id,
            MemoryScope scope,
            MemoryKind kind,
            String key,
            String value,
            long first,
            long last,
            long occurred,
            double confidence,
            double salience,
            Set<String> tags
    ) {
        return new MemoryRecord(
                id, scope, "player", "npc", kind, key, value, confidence, salience,
                "runtime", "source", first, last, occurred, 0L, "", tags
        );
    }
}
