package nl.hauntedmc.ailex.assistant.application.reliability;

/** Prevents repeated upstream failures from consuming every AI worker. */
public final class AssistantCircuitBreaker {

    private static final int FAILURE_THRESHOLD = 3;
    private static final long COOLDOWN_MILLIS = 30_000L;
    private int consecutiveFailures;
    private long openUntilMillis;

    public synchronized boolean allowsRequest(boolean enabled) {
        return !enabled || System.currentTimeMillis() >= openUntilMillis;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openUntilMillis = 0L;
    }

    public synchronized void recordFailure(boolean enabled) {
        if (!enabled) {
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            openUntilMillis = System.currentTimeMillis() + COOLDOWN_MILLIS;
        }
    }
}
