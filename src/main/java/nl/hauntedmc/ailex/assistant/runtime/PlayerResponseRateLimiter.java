package nl.hauntedmc.ailex.assistant.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Limits how often a player can trigger an AI response within a rolling time window. */
public class PlayerResponseRateLimiter {

    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final Map<UUID, Deque<Long>> responseTimestamps = new ConcurrentHashMap<>();
    private final Supplier<ResponseRateLimit> limitSupplier;
    private final LongSupplier currentTimeMillis;
    private final AtomicLong lastCleanupMillis = new AtomicLong(Long.MIN_VALUE);

    public PlayerResponseRateLimiter(Supplier<ResponseRateLimit> limitSupplier, LongSupplier currentTimeMillis) {
        this.limitSupplier = limitSupplier;
        this.currentTimeMillis = currentTimeMillis;
    }

    public boolean tryAcquire(UUID playerId) {
        return tryAcquire(playerId, false);
    }

    public boolean tryAcquire(UUID playerId, boolean bypassRateLimit) {
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

    public long retryAfterMillis(UUID playerId) {
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

    public record ResponseRateLimit(boolean enabled, int maxResponses, long windowMillis) {
        public ResponseRateLimit {
            if (maxResponses < 1) {
                throw new IllegalArgumentException("maxResponses must be at least 1");
            }
            if (windowMillis < 1) {
                throw new IllegalArgumentException("windowMillis must be at least 1");
            }
        }
    }
}
