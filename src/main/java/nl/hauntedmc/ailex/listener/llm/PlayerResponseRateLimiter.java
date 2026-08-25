package nl.hauntedmc.ailex.listener.llm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Limits how often a player can trigger an AI response within a rolling time window.
 */
final class PlayerResponseRateLimiter {

    private final Map<UUID, Deque<Long>> responseTimestamps = new ConcurrentHashMap<>();
    private final Supplier<ResponseRateLimit> limitSupplier;
    private final LongSupplier currentTimeMillis;

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
        ResponseRateLimit limit = limitSupplier.get();
        if (!limit.enabled()) {
            return true;
        }

        long now = currentTimeMillis.getAsLong();
        long oldestAllowed = now - limit.windowMillis();
        Deque<Long> timestamps = responseTimestamps.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= oldestAllowed) {
                timestamps.removeFirst();
            }

            if (timestamps.size() >= limit.maxResponses()) {
                return false;
            }

            timestamps.addLast(now);
            return true;
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
