package nl.hauntedmc.ailex.assistant.application.reliability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantCircuitBreakerTest {

    @Test
    void answerQualityFailuresNeverBlockUnrelatedPlayers() {
        AssistantCircuitBreaker breaker = new AssistantCircuitBreaker();

        for (int failure = 0; failure < 100; failure++) {
            breaker.recordFailure(true);
        }

        assertTrue(breaker.allowsRequest(true));
        breaker.recordSuccess();
        assertTrue(breaker.allowsRequest(true));
    }
}
