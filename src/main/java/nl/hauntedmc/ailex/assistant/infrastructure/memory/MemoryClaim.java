package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.Locale;
import java.util.Set;

/**
 * Evidence-aware temporal view over an immutable {@link MemoryRecord}. The durable row stays compact while this view
 * derives the epistemic fields used for temporal truth resolution, provenance and evidence-backed recall.
 */
public record MemoryClaim(
        String id,
        String subject,
        String predicate,
        String object,
        MemoryScope scope,
        MemoryKind kind,
        String sourceType,
        String sourceId,
        double authority,
        double confidence,
        double salience,
        long assertedAt,
        long validFrom,
        long validUntil,
        MemoryClaimStatus status,
        String supersedes,
        Set<String> evidenceIds,
        Set<String> tags
) {
    public MemoryClaim {
        id = clean(id);
        subject = clean(subject);
        predicate = clean(predicate).toLowerCase(Locale.ROOT);
        object = clean(object);
        scope = scope == null ? MemoryScope.SESSION : scope;
        kind = kind == null ? MemoryKind.FACT : kind;
        sourceType = clean(sourceType).toLowerCase(Locale.ROOT);
        sourceId = clean(sourceId);
        authority = Math.clamp(authority, 0.0D, 1.0D);
        confidence = Math.clamp(confidence, 0.0D, 1.0D);
        salience = Math.clamp(salience, 0.0D, 1.0D);
        status = status == null ? MemoryClaimStatus.ACTIVE : status;
        supersedes = clean(supersedes);
        evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public static MemoryClaim from(MemoryRecord record, long now) {
        if (record == null) {
            throw new IllegalArgumentException("record is required");
        }
        String subject = subject(record);
        long validFrom = record.occurredAt() > 0L ? record.occurredAt() : record.firstObserved();
        long validUntil = record.expiresAt();
        MemoryClaimStatus status = record.activeAt(now)
                ? MemoryClaimStatus.ACTIVE
                : record.supersedes().isBlank() ? MemoryClaimStatus.RETRACTED : MemoryClaimStatus.SUPERSEDED;
        Set<String> evidence = record.sourceId().isBlank()
                ? Set.of("memory." + record.id())
                : Set.of("memory." + record.id(), "source." + safeEvidenceId(record.sourceId()));
        return new MemoryClaim(
                record.id(), subject, record.key(), record.value(), record.scope(), record.kind(), record.sourceType(),
                record.sourceId(), sourceAuthority(record), record.confidence(), record.salience(),
                record.lastConfirmed(), validFrom, validUntil, status, record.supersedes(), evidence, record.tags()
        );
    }

    public boolean validAt(long epochMillis) {
        return validFrom <= epochMillis && (validUntil <= 0L || validUntil > epochMillis);
    }

    public String truthKey() {
        return subject + '|' + kind + '|' + predicate;
    }

    private static String subject(MemoryRecord record) {
        return switch (record.scope()) {
            case GLOBAL -> "global";
            case PLAYER -> "player:" + record.subjectId();
            case NPC -> "npc:" + record.subjectId();
            case PLAYER_NPC -> "player:" + record.subjectId() + "->npc:" + record.relationId();
            case WORLD -> "world:" + record.subjectId();
            case SESSION -> "session:" + record.subjectId();
            case EVENT -> record.subjectId().isBlank() ? "event:global" : "event:" + record.subjectId();
        };
    }

    private static double sourceAuthority(MemoryRecord record) {
        String source = record.sourceType().toLowerCase(Locale.ROOT);
        if (source.contains("official") || source.contains("reviewed")) {
            return 1.0D;
        }
        if (source.contains("event-listener") || source.contains("trusted") || source.contains("runtime")) {
            return 0.99D;
        }
        if (source.contains("player-explicit") && record.scope() == MemoryScope.PLAYER) {
            return 0.98D;
        }
        if (source.contains("authorized-player")) {
            return 0.82D;
        }
        return Math.clamp(record.confidence() * 0.92D, 0.30D, 0.95D);
    }

    private static String safeEvidenceId(String value) {
        String safe = clean(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]+", "-");
        return safe.isBlank() ? "unknown" : safe;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
