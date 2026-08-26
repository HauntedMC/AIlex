package nl.hauntedmc.ailex.assistant.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantRequestCoordinatorTest {

    @Test
    void queuesAndSupersedesLatestDirectRequestForBusyPlayer() {
        Deque<Runnable> dispatched = new ArrayDeque<>();
        AssistantRequestCoordinator coordinator = new AssistantRequestCoordinator(dispatched::addLast);
        UUID player = UUID.randomUUID();
        AtomicInteger completed = new AtomicInteger();

        AssistantRequestCoordinator.Submission first = coordinator.submit(
                player, AssistantRequestCoordinator.Priority.DIRECT, completed::incrementAndGet, 1, 4
        );
        AssistantRequestCoordinator.Submission second = coordinator.submit(
                player, AssistantRequestCoordinator.Priority.DIRECT, () -> completed.addAndGet(10), 1, 4
        );
        AssistantRequestCoordinator.Submission third = coordinator.submit(
                player, AssistantRequestCoordinator.Priority.DIRECT, () -> completed.addAndGet(100), 1, 4
        );

        assertEquals(AssistantRequestCoordinator.Disposition.STARTED, first.disposition());
        assertEquals(AssistantRequestCoordinator.Disposition.QUEUED, second.disposition());
        assertEquals(AssistantRequestCoordinator.Disposition.QUEUED_REPLACED, third.disposition());
        assertEquals(second.requestId(), third.supersededRequestId());
        assertEquals(1, coordinator.activeRequests());
        assertEquals(1, coordinator.queuedRequests());

        dispatched.removeFirst().run();
        assertEquals(1, completed.get());
        assertEquals(1, dispatched.size());

        dispatched.removeFirst().run();
        assertEquals(101, completed.get());
        assertEquals(0, coordinator.activeRequests());
        assertEquals(0, coordinator.queuedRequests());
    }

    @Test
    void rejectsProactiveWorkWhileDirectCapacityIsBusy() {
        Deque<Runnable> dispatched = new ArrayDeque<>();
        AssistantRequestCoordinator coordinator = new AssistantRequestCoordinator(dispatched::addLast);

        coordinator.submit(UUID.randomUUID(), AssistantRequestCoordinator.Priority.DIRECT, () -> { }, 1, 4);
        AssistantRequestCoordinator.Submission proactive = coordinator.submit(
                UUID.randomUUID(), AssistantRequestCoordinator.Priority.PROACTIVE, () -> { }, 1, 4
        );

        assertEquals(AssistantRequestCoordinator.Disposition.REJECTED_BUSY, proactive.disposition());
        assertEquals(0, coordinator.queuedRequests());
    }

    @Test
    void rejectsDirectWorkOnlyWhenTheBoundedQueueIsActuallyFull() {
        Deque<Runnable> dispatched = new ArrayDeque<>();
        AssistantRequestCoordinator coordinator = new AssistantRequestCoordinator(dispatched::addLast);
        coordinator.submit(UUID.randomUUID(), AssistantRequestCoordinator.Priority.DIRECT, () -> { }, 1, 1);
        AssistantRequestCoordinator.Submission queued = coordinator.submit(
                UUID.randomUUID(), AssistantRequestCoordinator.Priority.DIRECT, () -> { }, 1, 1
        );
        AssistantRequestCoordinator.Submission rejected = coordinator.submit(
                UUID.randomUUID(), AssistantRequestCoordinator.Priority.DIRECT, () -> { }, 1, 1
        );

        assertTrue(queued.accepted());
        assertEquals(AssistantRequestCoordinator.Disposition.REJECTED_FULL, rejected.disposition());
        assertNotNull(rejected.requestId());
    }
}
