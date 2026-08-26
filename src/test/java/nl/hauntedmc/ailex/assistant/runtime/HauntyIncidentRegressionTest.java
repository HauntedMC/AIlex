package nl.hauntedmc.ailex.assistant.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the production incident where repeated addressed Haunty turns disappeared while busy. */
class HauntyIncidentRegressionTest {

    @Test
    void pendingHauntyConversationShouldRecogniseTheQuestionMarkAsAContinuation() {
        AtomicLong clock = new AtomicLong(1_000L);
        AssistantConversationManager conversations = new AssistantConversationManager(clock::get);
        UUID player = UUID.randomUUID();

        conversations.recordUser(player, 7, "remymine", "wat gaat er mis haunty");
        AssistantConversationManager.Snapshot unresolved = conversations.snapshot(player, 7, 60_000L);

        assertTrue(unresolved.pendingAnswer());
        assertTrue(conversations.isLikelyFollowUp("haunty?", unresolved));
        AssistantConversationManager.ActiveTarget target = conversations.activeTarget(player, 60_000L);
        assertEquals(7, target.npcId());
        assertTrue(target.snapshot().pendingAnswer());
    }

    @Test
    void repeatedAddressedTurnsWhileBusyShouldQueueAndKeepTheNewestInsteadOfDisappearing() {
        Deque<Runnable> dispatched = new ArrayDeque<>();
        AssistantRequestCoordinator coordinator = new AssistantRequestCoordinator(dispatched::addLast);
        UUID player = UUID.randomUUID();
        AtomicInteger delivered = new AtomicInteger();

        AssistantRequestCoordinator.Submission first = coordinator.submit(
                UUID.randomUUID(), player, AssistantRequestCoordinator.Priority.DIRECT,
                () -> delivered.addAndGet(1), 1, 2
        );
        AssistantRequestCoordinator.Submission second = coordinator.submit(
                UUID.randomUUID(), player, AssistantRequestCoordinator.Priority.FOLLOW_UP,
                () -> delivered.addAndGet(10), 1, 2
        );
        AssistantRequestCoordinator.Submission third = coordinator.submit(
                UUID.randomUUID(), player, AssistantRequestCoordinator.Priority.FOLLOW_UP,
                () -> delivered.addAndGet(100), 1, 2
        );

        assertEquals(AssistantRequestCoordinator.Disposition.STARTED, first.disposition());
        assertEquals(AssistantRequestCoordinator.Disposition.QUEUED, second.disposition());
        assertEquals(AssistantRequestCoordinator.Disposition.QUEUED_REPLACED, third.disposition());
        assertEquals(second.requestId(), third.supersededRequestId());
        assertEquals(1, coordinator.activeRequests());
        assertEquals(1, coordinator.queuedRequests());

        dispatched.removeFirst().run();
        assertEquals(1, delivered.get());
        assertEquals(1, dispatched.size());

        dispatched.removeFirst().run();
        assertEquals(101, delivered.get());
        assertEquals(0, coordinator.activeRequests());
        assertEquals(0, coordinator.queuedRequests());
    }
}
