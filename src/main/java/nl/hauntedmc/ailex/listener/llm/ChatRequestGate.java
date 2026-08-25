package nl.hauntedmc.ailex.listener.llm;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bounds outstanding model requests so chat traffic cannot exhaust executor threads or API capacity.
 */
final class ChatRequestGate {

    private final Set<UUID> playersWithRequest = new HashSet<>();
    private int activeRequests;

    synchronized boolean tryAcquire(UUID playerId, int maximumConcurrentRequests) {
        if (playerId == null || maximumConcurrentRequests < 1
                || activeRequests >= maximumConcurrentRequests || playersWithRequest.contains(playerId)) {
            return false;
        }
        playersWithRequest.add(playerId);
        activeRequests++;
        return true;
    }

    synchronized void release(UUID playerId) {
        if (playerId != null && playersWithRequest.remove(playerId)) {
            activeRequests--;
        }
    }

    synchronized int activeRequests() {
        return activeRequests;
    }
}
