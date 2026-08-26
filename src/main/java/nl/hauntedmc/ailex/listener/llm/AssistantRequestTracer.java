package nl.hauntedmc.ailex.listener.llm;

import nl.hauntedmc.ailex.util.LoggerUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded request lifecycle tracing used for reliability diagnostics without logging prompt text. */
public final class AssistantRequestTracer {

    public enum State {
        RECEIVED,
        PREPARED,
        STARTED,
        QUEUED,
        SUPERSEDED,
        COMPLETED,
        REJECTED,
        UPSTREAM_FAILED,
        DELIVERY_FAILED
    }

    private static final int MAX_RECENT = 256;
    private final Map<UUID, MutableTrace> active = new ConcurrentHashMap<>();
    private final Deque<TraceSnapshot> recent = new ArrayDeque<>();

    public UUID start(String requester, String npc, String kind) {
        UUID requestId = UUID.randomUUID();
        MutableTrace trace = new MutableTrace(
                requestId,
                safe(requester),
                safe(npc),
                safe(kind),
                State.RECEIVED,
                System.nanoTime(),
                System.nanoTime(),
                ""
        );
        active.put(requestId, trace);
        log(trace, "");
        return requestId;
    }

    public void transition(UUID requestId, State state, String detail) {
        MutableTrace trace = active.get(requestId);
        if (trace == null) {
            return;
        }
        trace.state = state;
        trace.updatedAtNanos = System.nanoTime();
        trace.detail = safe(detail);
        log(trace, trace.detail);
        if (terminal(state)) {
            finish(trace);
        }
    }

    public List<TraceSnapshot> recent(int limit) {
        int safeLimit = Math.clamp(limit, 1, MAX_RECENT);
        synchronized (recent) {
            List<TraceSnapshot> snapshots = new ArrayList<>(recent);
            int from = Math.max(0, snapshots.size() - safeLimit);
            return List.copyOf(snapshots.subList(from, snapshots.size()));
        }
    }

    public int activeCount() {
        return active.size();
    }

    private void finish(MutableTrace trace) {
        active.remove(trace.requestId, trace);
        TraceSnapshot snapshot = trace.snapshot();
        synchronized (recent) {
            recent.addLast(snapshot);
            while (recent.size() > MAX_RECENT) {
                recent.removeFirst();
            }
        }
    }

    private boolean terminal(State state) {
        return state == State.SUPERSEDED
                || state == State.COMPLETED
                || state == State.REJECTED
                || state == State.UPSTREAM_FAILED
                || state == State.DELIVERY_FAILED;
    }

    private void log(MutableTrace trace, String detail) {
        long ageMillis = Math.max(0L, (System.nanoTime() - trace.createdAtNanos) / 1_000_000L);
        String suffix = detail == null || detail.isBlank() ? "" : " detail=" + safe(detail);
        LoggerUtils.logInfo("[AIlex request] request=" + shortId(trace.requestId)
                + " requester=" + trace.requester
                + " npc=" + trace.npc
                + " kind=" + trace.kind
                + " state=" + trace.state.name().toLowerCase(Locale.ROOT)
                + " age_ms=" + ageMillis
                + suffix);
    }

    private String safe(String value) {
        return (value == null ? "" : value).replaceAll("\\s+", " ").trim()
                .replace('<', '‹').replace('>', '›').replace('"', '\'');
    }

    private String shortId(UUID requestId) {
        return requestId.toString().substring(0, 8);
    }

    public record TraceSnapshot(
            UUID requestId,
            String requester,
            String npc,
            String kind,
            State state,
            long latencyMillis,
            String detail
    ) {
    }

    private static final class MutableTrace {
        private final UUID requestId;
        private final String requester;
        private final String npc;
        private final String kind;
        private State state;
        private final long createdAtNanos;
        private long updatedAtNanos;
        private String detail;

        private MutableTrace(
                UUID requestId,
                String requester,
                String npc,
                String kind,
                State state,
                long createdAtNanos,
                long updatedAtNanos,
                String detail
        ) {
            this.requestId = requestId;
            this.requester = requester;
            this.npc = npc;
            this.kind = kind;
            this.state = state;
            this.createdAtNanos = createdAtNanos;
            this.updatedAtNanos = updatedAtNanos;
            this.detail = detail;
        }

        private TraceSnapshot snapshot() {
            return new TraceSnapshot(
                    requestId,
                    requester,
                    npc,
                    kind,
                    state,
                    Math.max(0L, (updatedAtNanos - createdAtNanos) / 1_000_000L),
                    detail
            );
        }
    }
}
