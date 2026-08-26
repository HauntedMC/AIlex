package nl.hauntedmc.ailex.listener.llm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Limits how often a player can trigger an AI response within a rolling time window.
 */
final class PlayerResponseRateLimiter {

    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final Map<UUID, Deque<Long>> responseTimestamps = new ConcurrentHashMap<>();
    private final Supplier<ResponseRateLimit> limitSupplier;
    private final LongSupplier currentTimeMillis;
    private final AtomicLong lastCleanupMillis = new AtomicLong(Long.MIN_VALUE);

    PlayerResponseRateLimiter(Supplier<ResponseRateLimit> limitSupplier, LongSupplier currentTimeMillis) {
        this.limitSupplier = limitSupplier;
        this.currentTimeMillis = currentTimeMillis;
    }

    /**
     * Reserve one response for a player when the configured limit allows it.
     *
     * @param playerId the player triggering the response
     * @return true when a response may be requested
     */
    boolean tryAcquire(UUID playerId) {
        return tryAcquire(playerId, false);
    }

    /**
     * Reserve one response unless the caller has the configured staff bypass.
     * Bypassed responses do not consume a regular player's rate-limit slot.
     */
    boolean tryAcquire(UUID playerId, boolean bypassRateLimit) {
        if (bypassRateLimit) {
            return true;
        }
        ResponseRateLimit limit = limitSupplier.get();
        if (!limit.enabled()) {
            return true;
        }

        long now = currentTimeMillis.getAsLong();
        long oldestAllowed = now - limit.windowMillis();
        removeExpiredBuckets(now, oldestAllowed);
        Deque<Long> timestamps = responseTimestamps.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());

        synchronized (timestamps) {
            removeExpiredTimestamps(timestamps, oldestAllowed);

            if (timestamps.size() >= limit.maxResponses()) {
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }

    /**
     * Returns the earliest time at which a player may trigger another response.
     *
     * @param playerId the player to inspect
     * @return remaining wait time in milliseconds, or zero when no wait applies
     */
    long retryAfterMillis(UUID playerId) {
        ResponseRateLimit limit = limitSupplier.get();
        if (!limit.enabled() || playerId == null) {
            return 0L;
        }

        long now = currentTimeMillis.getAsLong();
        long oldestAllowed = now - limit.windowMillis();
        removeExpiredBuckets(now, oldestAllowed);
        Deque<Long> timestamps = responseTimestamps.get(playerId);
        if (timestamps == null) {
            return 0L;
        }

        synchronized (timestamps) {
            removeExpiredTimestamps(timestamps, oldestAllowed);
            if (timestamps.size() < limit.maxResponses() || timestamps.isEmpty()) {
                return 0L;
            }
            return Math.max(0L, timestamps.peekFirst() + limit.windowMillis() - now);
        }
    }

    private void removeExpiredBuckets(long now, long oldestAllowed) {
        long previousCleanup = lastCleanupMillis.get();
        if (previousCleanup != Long.MIN_VALUE && now - previousCleanup < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanupMillis.compareAndSet(previousCleanup, now)) {
            return;
        }

        responseTimestamps.forEach((playerId, timestamps) -> {
            synchronized (timestamps) {
                removeExpiredTimestamps(timestamps, oldestAllowed);
                if (timestamps.isEmpty()) {
                    responseTimestamps.remove(playerId, timestamps);
                }
            }
        });
    }

    private void removeExpiredTimestamps(Deque<Long> timestamps, long oldestAllowed) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= oldestAllowed) {
            timestamps.removeFirst();
        }
    }

    record ResponseRateLimit(boolean enabled, int maxResponses, long windowMillis) {

        ResponseRateLimit {
            if (maxResponses < 1) {
                throw new IllegalArgumentException("maxResponses must be at least 1");
            }
            if (windowMillis < 1) {
                throw new IllegalArgumentException("windowMillis must be at least 1");
            }
        }
    }
}
