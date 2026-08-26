package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryTruthResolverTest {

    private final MemoryTruthResolver resolver = new MemoryTruthResolver();

    @Test
    void newerExplicitCorrectionShouldWinAtCurrentTime() {
        long now = 10_000_000L;
        MemoryRecord oldValue = record("old", "favorite_gamemode", "survival", 1_000L, 0L, "");
        MemoryRecord newValue = record("new", "favorite_gamemode", "creative", 9_000_000L, 0L, "old");

        MemoryTruthResolver.ResolvedClaim resolved = resolver.resolve(List.of(oldValue, newValue), now).getFirst();

        assertEquals("creative", resolved.primary().object());
        assertFalse(resolved.disputed());
        assertEquals(1, resolved.alternatives().size());
    }

    @Test
    void historicalResolutionShouldRespectValidityWindow() {
        long correctionTime = Duration.ofDays(10).toMillis();
        MemoryRecord oldValue = record("old", "favorite_gamemode", "survival", 1_000L, correctionTime, "");
        MemoryRecord newValue = record(
                "new", "favorite_gamemode", "creative", correctionTime, 0L, "old"
        );

        MemoryTruthResolver.ResolvedClaim before = resolver.resolve(
                List.of(oldValue, newValue), Duration.ofDays(5).toMillis()
        ).getFirst();
        MemoryTruthResolver.ResolvedClaim after = resolver.resolve(
                List.of(oldValue, newValue), Duration.ofDays(20).toMillis()
        ).getFirst();

        assertEquals("survival", before.primary().object());
        assertEquals("creative", after.primary().object());
    }

    @Test
    void nearTiedConflictingClaimsShouldBeExposedAsDisputed() {
        long now = 100_000L;
        MemoryRecord first = record("one", "home_server", "survival", 99_000L, 0L, "");
        MemoryRecord second = new MemoryRecord(
                "two", MemoryScope.PLAYER, "player", "", MemoryKind.FACT, "home_server", "creative",
                0.93D, 0.72D, "player-explicit", "player", 99_500L, 99_500L, 0L, 0L, "", Set.of()
        );

        MemoryTruthResolver.ResolvedClaim resolved = resolver.resolve(List.of(first, second), now).getFirst();
        assertTrue(resolved.disputed());
    }

    private static MemoryRecord record(
            String id,
            String key,
            String value,
            long assertedAt,
            long expiresAt,
            String supersedes
    ) {
        return new MemoryRecord(
                id, MemoryScope.PLAYER, "player", "", MemoryKind.FACT, key, value,
                0.93D, 0.72D, "player-explicit", "player", assertedAt, assertedAt, 0L, expiresAt,
                supersedes, Set.of()
        );
    }
}
