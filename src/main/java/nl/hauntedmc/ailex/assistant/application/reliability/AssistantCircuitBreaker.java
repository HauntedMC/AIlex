package nl.hauntedmc.ailex.assistant.application.reliability;

/**
 * Compatibility boundary for assistant answer-quality outcomes.
 *
 * <p>Grounding rejection and local deadline exhaustion are not evidence that the shared model provider is down, and
 * therefore must never suppress unrelated players. Provider-level circuit breaking is owned by the OpenAI transport
 * reliability layer, which can distinguish real HTTP/transport failures from answer-quality failures.</p>
 */
public final class AssistantCircuitBreaker {

    public boolean allowsRequest(boolean enabled) {
        return true;
    }

    public void recordSuccess() {
        // Quality success does not control shared provider availability.
    }

    public void recordFailure(boolean enabled) {
        // Intentionally local/no-op: callers currently report grounding and deadline failures through this API.
    }
}
