package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.time.Duration;
import java.util.List;

/**
 * Human-inspired but deterministic memory lifecycle policy. Salience, confidence, reinforcement and age protect useful
 * traces while dense competing events introduce interference. Durable semantic facts/preferences are never forgotten
 * merely because time passed; decay applies only to transient/episodic traces unless an explicit forget/correction occurs.
 */
public final class MemoryRetentionPolicy {

    private static final Duration EPISODIC_HALF_LIFE = Duration.ofDays(45);
    private static final Duration EVENT_HALF_LIFE = Duration.ofDays(10);
    private static final Duration SESSION_HALF_LIFE = Duration.ofHours(8);

    /**
     * Derives a lifecycle label from stored evidence without mutating the record.
     * The stage describes retention maturity, not factual confidence or source authority.
     */
    public MemoryLifecycleStage stage(MemoryRecord record, long now) {
        if (record == null) {
            return MemoryLifecycleStage.DECAYING;
        }
        long confirmations = Math.max(0L, record.lastConfirmed() - record.firstObserved());
        double ageScore = freshness(record, now);
        if (record.kind() == MemoryKind.EPISODE || record.tags().contains("consolidated")) {
            if (record.confidence() >= 0.90D && record.salience() >= 0.70D && confirmations > Duration.ofDays(1).toMillis()) {
                return MemoryLifecycleStage.MATURE;
            }
            return ageScore >= 0.30D ? MemoryLifecycleStage.CONSOLIDATED : MemoryLifecycleStage.DECAYING;
        }
        if (record.kind() == MemoryKind.EVENT || record.scope() == MemoryScope.SESSION) {
            return ageScore >= 0.20D ? MemoryLifecycleStage.BUFFERED : MemoryLifecycleStage.DECAYING;
        }
        return MemoryLifecycleStage.MATURE;
    }

    /**
     * Scores how strongly a transient trace should be retained at the supplied time.
     * Competing related traces lower the score; durable semantic memories are protected with a score of {@code 1.0}.
     */
    public double retentionScore(MemoryRecord record, long now, int competingTraceCount) {
        if (record == null) {
            return 0.0D;
        }
        if (durableSemantic(record)) {
            return 1.0D;
        }
        double reinforcement = Math.min(1.0D,
                Math.max(0L, record.lastConfirmed() - record.firstObserved()) / (double) Duration.ofDays(7).toMillis());
        double interference = Math.min(0.45D, Math.max(0, competingTraceCount - 1) * 0.035D);
        double consolidationBonus = record.kind() == MemoryKind.EPISODE || record.tags().contains("consolidated")
                ? 0.16D : 0.0D;
        double score = record.salience() * 0.34D
                + record.confidence() * 0.24D
                + freshness(record, now) * 0.24D
                + reinforcement * 0.10D
                + consolidationBonus
                - interference;
        return Math.clamp(score, 0.0D, 1.0D);
    }

    /**
     * Returns whether a transient trace is weak enough to expire under age plus topic interference.
     * Explicitly verified traces and durable semantic memory cannot be removed by this passive policy.
     */
    public boolean shouldExpire(MemoryRecord record, long now, List<MemoryRecord> competingTraces) {
        if (record == null || durableSemantic(record) || record.tags().contains("verified")) {
            return false;
        }
        int competitors = competingTraces == null ? 0 : (int) competingTraces.stream()
                .filter(other -> other != null && !other.id().equals(record.id()))
                .filter(other -> other.scope() == record.scope())
                .filter(other -> other.subjectId().equals(record.subjectId()))
                .filter(other -> sharesTopic(record, other))
                .count();
        return stage(record, now) == MemoryLifecycleStage.DECAYING
                && retentionScore(record, now, competitors) < 0.28D;
    }

    /**
     * Reactivates salience after an externally grounded successful use.
     * Confidence, value, provenance and occurrence time are intentionally unchanged: retrieval success is not new truth.
     */
    public MemoryRecord reconsolidateVerifiedUse(MemoryRecord record, long now) {
        if (record == null || !record.activeAt(now)) {
            return record;
        }
        double salience = Math.min(1.0D, record.salience() + (record.kind() == MemoryKind.EVENT ? 0.035D : 0.02D));
        return new MemoryRecord(
                record.id(), record.scope(), record.subjectId(), record.relationId(), record.kind(), record.key(),
                record.value(), record.confidence(), salience, record.sourceType(), record.sourceId(), record.firstObserved(),
                now, record.occurredAt(), record.expiresAt(), record.supersedes(), record.tags()
        );
    }

    private double freshness(MemoryRecord record, long now) {
        long age = Math.max(0L, now - Math.max(record.lastConfirmed(), record.occurredAt()));
        Duration halfLife = record.scope() == MemoryScope.SESSION ? SESSION_HALF_LIFE
                : record.kind() == MemoryKind.EVENT ? EVENT_HALF_LIFE : EPISODIC_HALF_LIFE;
        return 1.0D / (1.0D + age / (double) halfLife.toMillis());
    }

    private boolean durableSemantic(MemoryRecord record) {
        return switch (record.kind()) {
            case FACT, PREFERENCE, OPINION, INTEREST, GOAL, RELATIONSHIP -> record.scope() != MemoryScope.SESSION;
            case EVENT, EPISODE -> false;
        };
    }

    private boolean sharesTopic(MemoryRecord left, MemoryRecord right) {
        if (left.key().equals(right.key())) {
            return true;
        }
        for (String tag : left.tags()) {
            if (tag.length() >= 3 && right.tags().contains(tag)) {
                return true;
            }
        }
        return false;
    }
}
