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

    private final Map<UUID, Deque<Reservation>> responseTimestamps = new ConcurrentHashMap<>();
    private final Supplier<ResponseRateLimit> limitSupplier;
    private final LongSupplier currentTimeMillis;
    private final AtomicLong lastCleanupMillis = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong reservationSequence = new AtomicLong();

    public PlayerResponseRateLimiter(Supplier<ResponseRateLimit> limitSupplier, LongSupplier currentTimeMillis) {
        this.limitSupplier = limitSupplier;
        this.currentTimeMillis = currentTimeMillis;
    }

    public boolean tryAcquire(UUID playerId) {
        return tryAcquire(playerId, false);
    }

    /** Legacy boolean API. Callers that may fail after admission should prefer {@link #acquire} so they can refund. */
    public boolean tryAcquire(UUID playerId, boolean bypassRateLimit) {
        return acquire(playerId, bypassRateLimit).acquired();
    }

    /**
     * Reserves one response slot for a request. The returned permit identifies that exact reservation so failed,
     * superseded or undeliverable requests can release it without disturbing another concurrent request by the player.
     */
    public Permit acquire(UUID playerId, boolean bypassRateLimit) {
        if (playerId == null) {
            return Permit.rejected();
        }
        if (bypassRateLimit) {
            return Permit.uncounted(playerId);
        }
        ResponseRateLimit limit = limitSupplier.get();
        if (!limit.enabled()) {
            return Permit.uncounted(playerId);
        }

        long now = currentTimeMillis.getAsLong();
        long oldestAllowed = now - limit.windowMillis();
        removeExpiredBuckets(now, oldestAllowed);
        Deque<Reservation> timestamps = responseTimestamps.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());

        synchronized (timestamps) {
            removeExpiredTimestamps(timestamps, oldestAllowed);
            if (timestamps.size() >= limit.maxResponses()) {
                return Permit.rejected();
            }
            long reservationId = reservationSequence.incrementAndGet();
            timestamps.addLast(new Reservation(reservationId, now));
            return Permit.counted(playerId, reservationId);
        }
    }

    /** Releases an exact counted reservation. Calling this repeatedly for the same permit is harmless. */
    public void refund(Permit permit) {
        if (permit == null || !permit.acquired() || !permit.counted() || permit.playerId() == null) {
            return;
        }
        Deque<Reservation> timestamps = responseTimestamps.get(permit.playerId());
        if (timestamps == null) {
            return;
        }
        synchronized (timestamps) {
            timestamps.removeIf(entry -> entry.id() == permit.reservationId());
            if (timestamps.isEmpty()) {
                responseTimestamps.remove(permit.playerId(), timestamps);
            }
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
        Deque<Reservation> timestamps = responseTimestamps.get(playerId);
        if (timestamps == null) {
            return 0L;
        }

        synchronized (timestamps) {
            removeExpiredTimestamps(timestamps, oldestAllowed);
            if (timestamps.size() < limit.maxResponses() || timestamps.isEmpty()) {
                return 0L;
            }
            return Math.max(0L, timestamps.peekFirst().timestampMillis() + limit.windowMillis() - now);
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

    private void removeExpiredTimestamps(Deque<Reservation> timestamps, long oldestAllowed) {
        while (!timestamps.isEmpty() && timestamps.peekFirst().timestampMillis() <= oldestAllowed) {
            timestamps.removeFirst();
        }
    }

    public record Permit(UUID playerId, long reservationId, boolean acquired, boolean counted) {
        private static Permit rejected() {
            return new Permit(null, 0L, false, false);
        }

        private static Permit uncounted(UUID playerId) {
            return new Permit(playerId, 0L, true, false);
        }

        private static Permit counted(UUID playerId, long reservationId) {
            return new Permit(playerId, reservationId, true, true);
        }
    }

    private record Reservation(long id, long timestampMillis) {
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
