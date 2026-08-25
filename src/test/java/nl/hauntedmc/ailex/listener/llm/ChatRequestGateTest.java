package nl.hauntedmc.ailex.listener.llm;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRequestGateTest {

    @Test
    void shouldAllowOnlyOneOutstandingRequestPerPlayerAndRespectGlobalLimit() {
        ChatRequestGate gate = new ChatRequestGate();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        assertTrue(gate.tryAcquire(firstPlayer, 1));
        assertFalse(gate.tryAcquire(firstPlayer, 1));
        assertFalse(gate.tryAcquire(secondPlayer, 1));
        assertEquals(1, gate.activeRequests());

        gate.release(firstPlayer);
        assertTrue(gate.tryAcquire(secondPlayer, 1));
        assertEquals(1, gate.activeRequests());
    }

    @Test
    void shouldIgnoreReleaseForPlayerWithoutARequest() {
        ChatRequestGate gate = new ChatRequestGate();

        gate.release(UUID.randomUUID());

        assertEquals(0, gate.activeRequests());
    }
}
