package nl.hauntedmc.ailex.listener.llm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Priority-aware admission controller for assistant work.
 *
 * <p>Direct player requests are never silently dropped because another request is active. The newest
 * queued request for a player supersedes an older queued request, while proactive work is best-effort
 * and is the first work discarded under pressure.</p>
 */
final class AssistantRequestCoordinator {

    enum Priority {
        DIRECT(100),
        FOLLOW_UP(90),
        PROACTIVE(10);

        private final int weight;

        Priority(int weight) {
            this.weight = weight;
        }
    }

    enum Disposition {
        STARTED,
        QUEUED,
        QUEUED_REPLACED,
        REJECTED_BUSY,
        REJECTED_FULL
    }

    record Submission(UUID requestId, Disposition disposition, UUID supersededRequestId) {
        boolean accepted() {
            return disposition == Disposition.STARTED
                    || disposition == Disposition.QUEUED
                    || disposition == Disposition.QUEUED_REPLACED;
        }
    }

    private final Consumer<Runnable> dispatcher;
    private final PriorityQueue<QueuedWork> queue = new PriorityQueue<>(Comparator
            .comparingInt((QueuedWork work) -> work.priority().weight).reversed()
            .thenComparingLong(QueuedWork::sequence));
    private final Map<UUID, QueuedWork> queuedByOwner = new HashMap<>();
    private final Set<UUID> activeOwners = new HashSet<>();
    private long sequence;
    private int activeRequests;

    AssistantRequestCoordinator(Consumer<Runnable> dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    synchronized Submission submit(
            UUID ownerId,
            Priority priority,
            Runnable action,
            int maximumConcurrentRequests,
            int maximumQueuedRequests
    ) {
        return submit(UUID.randomUUID(), ownerId, priority, action, maximumConcurrentRequests, maximumQueuedRequests);
    }

    synchronized Submission submit(
            UUID requestId,
            UUID ownerId,
            Priority priority,
            Runnable action,
            int maximumConcurrentRequests,
            int maximumQueuedRequests
    ) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(action, "action");
        int maxConcurrent = Math.max(1, maximumConcurrentRequests);
        int maxQueued = Math.max(0, maximumQueuedRequests);
        QueuedWork work = new QueuedWork(requestId, ownerId, priority, action, sequence++);

        if (!activeOwners.contains(ownerId) && activeRequests < maxConcurrent) {
            start(work);
            return new Submission(requestId, Disposition.STARTED, null);
        }

        if (priority == Priority.PROACTIVE) {
            return new Submission(requestId, Disposition.REJECTED_BUSY, null);
        }

        QueuedWork previous = queuedByOwner.get(ownerId);
        if (previous != null) {
            queue.remove(previous);
            queue.add(work);
            queuedByOwner.put(ownerId, work);
            return new Submission(requestId, Disposition.QUEUED_REPLACED, previous.requestId());
        }

        if (maxQueued == 0 || queue.size() >= maxQueued) {
            return new Submission(requestId, Disposition.REJECTED_FULL, null);
        }

        queue.add(work);
        queuedByOwner.put(ownerId, work);
        return new Submission(requestId, Disposition.QUEUED, null);
    }

    synchronized int activeRequests() {
        return activeRequests;
    }

    synchronized int queuedRequests() {
        return queue.size();
    }

    synchronized boolean hasActiveRequest(UUID ownerId) {
        return activeOwners.contains(ownerId);
    }

    synchronized List<UUID> queuedRequestIds() {
        return queue.stream().sorted(queue.comparator()).map(QueuedWork::requestId).toList();
    }

    private void start(QueuedWork work) {
        activeRequests++;
        activeOwners.add(work.ownerId());
        dispatch(work);
    }

    private void dispatch(QueuedWork work) {
        dispatcher.accept(() -> {
            try {
                work.action().run();
            } finally {
                complete(work.ownerId());
            }
        });
    }

    private void complete(UUID ownerId) {
        QueuedWork next = null;
        synchronized (this) {
            if (activeOwners.remove(ownerId)) {
                activeRequests = Math.max(0, activeRequests - 1);
            }
            next = pollEligible();
            if (next != null) {
                queuedByOwner.remove(next.ownerId(), next);
                activeRequests++;
                activeOwners.add(next.ownerId());
            }
        }
        if (next != null) {
            dispatch(next);
        }
    }

    private QueuedWork pollEligible() {
        if (queue.isEmpty()) {
            return null;
        }
        List<QueuedWork> blocked = new ArrayList<>();
        QueuedWork selected = null;
        while (!queue.isEmpty()) {
            QueuedWork candidate = queue.poll();
            if (!activeOwners.contains(candidate.ownerId())) {
                selected = candidate;
                break;
            }
            blocked.add(candidate);
        }
        queue.addAll(blocked);
        return selected;
    }

    private record QueuedWork(
            UUID requestId,
            UUID ownerId,
            Priority priority,
            Runnable action,
            long sequence
    ) {
    }
}
