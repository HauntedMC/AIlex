package nl.hauntedmc.ailex.assistant.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerResponseRateLimiterTest {

    @Test
    void shouldRejectTheEleventhResponseWithinOneHourForTheSamePlayer() {
        AtomicLong now = new AtomicLong(0L);
        PlayerResponseRateLimiter limiter = new PlayerResponseRateLimiter(
                () -> new PlayerResponseRateLimiter.ResponseRateLimit(true, 10, 60L * 60L * 1000L), now::get
        );
        UUID playerId = UUID.randomUUID();
        for (int response = 0; response < 10; response++) {
            assertTrue(limiter.tryAcquire(playerId));
        }
        assertFalse(limiter.tryAcquire(playerId));
    }

    @Test
    void shouldAllowAnotherResponseAfterTheRollingWindowExpires() {
        AtomicLong now = new AtomicLong(0L);
        PlayerResponseRateLimiter limiter = new PlayerResponseRateLimiter(
                () -> new PlayerResponseRateLimiter.ResponseRateLimit(true, 1, 1_000L), now::get
        );
        UUID playerId = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(playerId));
        assertFalse(limiter.tryAcquire(playerId));
        now.set(1_000L);
        assertTrue(limiter.tryAcquire(playerId));
    }

    @Test
    void shouldKeepLimitsSeparateForEachPlayer() {
        PlayerResponseRateLimiter limiter = new PlayerResponseRateLimiter(
                () -> new PlayerResponseRateLimiter.ResponseRateLimit(true, 1, 1_000L), () -> 0L
        );
        assertTrue(limiter.tryAcquire(UUID.randomUUID()));
        assertTrue(limiter.tryAcquire(UUID.randomUUID()));
    }

    @Test
    void shouldAllowAllResponsesWhenTheLimitIsDisabled() {
        PlayerResponseRateLimiter limiter = new PlayerResponseRateLimiter(
                () -> new PlayerResponseRateLimiter.ResponseRateLimit(false, 1, 1_000L), () -> 0L
        );
        UUID playerId = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(playerId));
        assertTrue(limiter.tryAcquire(playerId));
    }

    @Test
    void shouldAllowStaffBypassWithoutConsumingAPlayerResponseSlot() {
        PlayerResponseRateLimiter limiter = new PlayerResponseRateLimiter(
                () -> new PlayerResponseRateLimiter.ResponseRateLimit(true, 1, 1_000L), () -> 0L
        );
        UUID playerId = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(playerId, true));
        assertTrue(limiter.tryAcquire(playerId, true));
        assertTrue(limiter.tryAcquire(playerId));
        assertFalse(limiter.tryAcquire(playerId));
    }

    @Test
    void shouldReportHowLongThePlayerMustWaitAfterReachingTheLimit() {
        AtomicLong now = new AtomicLong(100L);
        PlayerResponseRateLimiter limiter = new PlayerResponseRateLimiter(
                () -> new PlayerResponseRateLimiter.ResponseRateLimit(true, 1, 1_000L), now::get
        );
        UUID playerId = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(playerId));
        now.set(400L);
        assertEquals(700L, limiter.retryAfterMillis(playerId));
    }

    @Test
    void failedRequestCanRefundItsResponseSlot() {
        PlayerResponseRateLimiter limiter = new PlayerResponseRateLimiter(
                () -> new PlayerResponseRateLimiter.ResponseRateLimit(true, 1, 10_000L), () -> 100L
        );
        UUID playerId = UUID.randomUUID();
        PlayerResponseRateLimiter.Permit permit = limiter.acquire(playerId, false);

        assertTrue(permit.acquired());
        assertTrue(permit.counted());
        assertFalse(limiter.tryAcquire(playerId));

        limiter.refund(permit);
        assertTrue(limiter.tryAcquire(playerId));
    }

    @Test
    void refundRemovesOnlyTheMatchingConcurrentReservation() {
        PlayerResponseRateLimiter limiter = new PlayerResponseRateLimiter(
                () -> new PlayerResponseRateLimiter.ResponseRateLimit(true, 2, 10_000L), () -> 100L
        );
        UUID playerId = UUID.randomUUID();
        PlayerResponseRateLimiter.Permit first = limiter.acquire(playerId, false);
        PlayerResponseRateLimiter.Permit second = limiter.acquire(playerId, false);

        assertFalse(limiter.tryAcquire(playerId));
        limiter.refund(first);
        assertTrue(limiter.tryAcquire(playerId));
        assertFalse(limiter.tryAcquire(playerId));

        // Refunding the same permit again must not free the second or replacement reservation.
        limiter.refund(first);
        assertFalse(limiter.tryAcquire(playerId));
        assertTrue(second.counted());
    }

    @Test
    void bypassAndDisabledPermitsAreAcceptedButNeverCounted() {
        UUID playerId = UUID.randomUUID();
        PlayerResponseRateLimiter bypassLimiter = new PlayerResponseRateLimiter(
                () -> new PlayerResponseRateLimiter.ResponseRateLimit(true, 1, 10_000L), () -> 100L
        );
        PlayerResponseRateLimiter.Permit bypass = bypassLimiter.acquire(playerId, true);
        assertTrue(bypass.acquired());
        assertFalse(bypass.counted());
        bypassLimiter.refund(bypass);
        assertTrue(bypassLimiter.tryAcquire(playerId));

        PlayerResponseRateLimiter disabledLimiter = new PlayerResponseRateLimiter(
                () -> new PlayerResponseRateLimiter.ResponseRateLimit(false, 1, 10_000L), () -> 100L
        );
        PlayerResponseRateLimiter.Permit disabled = disabledLimiter.acquire(playerId, false);
        assertTrue(disabled.acquired());
        assertFalse(disabled.counted());
    }
}
