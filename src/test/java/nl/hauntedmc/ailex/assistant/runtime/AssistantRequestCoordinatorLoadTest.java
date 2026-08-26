package nl.hauntedmc.ailex.assistant.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Deterministic pressure tests for bounded request admission; this is not a timing benchmark. */
class AssistantRequestCoordinatorLoadTest {

    @Test
    void directQueueShouldRemainHardBoundedAndDrainCompletely() {
        Deque<Runnable> dispatched = new ArrayDeque<>();
        AssistantRequestCoordinator coordinator = new AssistantRequestCoordinator(dispatched::addLast);
        AtomicInteger completed = new AtomicInteger();
        int started = 0;
        int queued = 0;
        int rejected = 0;

        for (int index = 0; index < 100; index++) {
            AssistantRequestCoordinator.Submission submission = coordinator.submit(
                    UUID.randomUUID(), UUID.randomUUID(), AssistantRequestCoordinator.Priority.DIRECT,
                    completed::incrementAndGet, 4, 8
            );
            switch (submission.disposition()) {
                case STARTED -> started++;
                case QUEUED -> queued++;
                case REJECTED_FULL -> rejected++;
                default -> throw new AssertionError("Unexpected disposition: " + submission.disposition());
            }
        }

        assertEquals(4, started);
        assertEquals(8, queued);
        assertEquals(88, rejected);
        assertEquals(4, coordinator.activeRequests());
        assertEquals(8, coordinator.queuedRequests());
        assertEquals(4, dispatched.size());

        while (!dispatched.isEmpty()) {
            dispatched.removeFirst().run();
        }

        assertEquals(12, completed.get());
        assertEquals(0, coordinator.activeRequests());
        assertEquals(0, coordinator.queuedRequests());
    }

    @Test
    void proactiveLoadShouldNeverConsumeDirectQueueCapacity() {
        Deque<Runnable> dispatched = new ArrayDeque<>();
        AssistantRequestCoordinator coordinator = new AssistantRequestCoordinator(dispatched::addLast);
        for (int index = 0; index < 4; index++) {
            coordinator.submit(
                    UUID.randomUUID(), UUID.randomUUID(), AssistantRequestCoordinator.Priority.DIRECT,
                    () -> { }, 4, 8
            );
        }

        for (int index = 0; index < 100; index++) {
            AssistantRequestCoordinator.Submission submission = coordinator.submit(
                    UUID.randomUUID(), UUID.randomUUID(), AssistantRequestCoordinator.Priority.PROACTIVE,
                    () -> { }, 4, 8
            );
            assertEquals(AssistantRequestCoordinator.Disposition.REJECTED_BUSY, submission.disposition());
        }

        assertEquals(4, coordinator.activeRequests());
        assertEquals(0, coordinator.queuedRequests());
        assertEquals(4, dispatched.size());
    }
}
