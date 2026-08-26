package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.Locale;
import java.util.Set;

/**
 * Typed memory unit with provenance and lifecycle metadata.
 *
 * @param id stable record identifier
 * @param scope logical ownership boundary
 * @param subjectId primary subject (player UUID, NPC id/name, world, event id, or empty for global)
 * @param relationId optional second subject for relational memories
 * @param kind semantic memory kind
 * @param key canonical semantic key used for supersession
 * @param value concise player-safe value
 * @param confidence source confidence from 0..1
 * @param salience retrieval importance from 0..1
 * @param sourceType source class, for example player-explicit or event-listener
 * @param sourceId source identifier when available
 * @param firstObserved first observation epoch millis
 * @param lastConfirmed latest confirmation epoch millis
 * @param occurredAt event/episode occurrence epoch millis, or zero for timeless facts
 * @param expiresAt expiry epoch millis, or zero for no automatic expiry
 * @param supersedes previous record id for the same semantic identity
 * @param tags compact retrieval labels
 */
public record MemoryRecord(
        String id,
        MemoryScope scope,
        String subjectId,
        String relationId,
        MemoryKind kind,
        String key,
        String value,
        double confidence,
        double salience,
        String sourceType,
        String sourceId,
        long firstObserved,
        long lastConfirmed,
        long occurredAt,
        long expiresAt,
        String supersedes,
        Set<String> tags
) {
    public MemoryRecord {
        id = clean(id);
        scope = scope == null ? MemoryScope.SESSION : scope;
        subjectId = clean(subjectId);
        relationId = clean(relationId);
        kind = kind == null ? MemoryKind.FACT : kind;
        key = clean(key).toLowerCase(Locale.ROOT);
        value = clean(value);
        confidence = Math.clamp(confidence, 0.0D, 1.0D);
        salience = Math.clamp(salience, 0.0D, 1.0D);
        sourceType = clean(sourceType).toLowerCase(Locale.ROOT);
        sourceId = clean(sourceId);
        supersedes = clean(supersedes);
        tags = tags == null ? Set.of() : tags.stream()
                .map(MemoryRecord::clean)
                .filter(tag -> !tag.isBlank())
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Stable in-memory identity used to select the currently active value. */
    public String identityKey() {
        return scope + "|" + subjectId + "|" + relationId + "|" + kind + "|" + key;
    }

    /** True while the record is not expired at the supplied clock time. */
    public boolean activeAt(long now) {
        return expiresAt <= 0L || expiresAt > now;
    }

    /** Creates a historical superseded copy that expires immediately. */
    public MemoryRecord expireAt(long timestamp) {
        return new MemoryRecord(
                id, scope, subjectId, relationId, kind, key, value, confidence, salience, sourceType, sourceId,
                firstObserved, lastConfirmed, occurredAt, timestamp, supersedes, tags
        );
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
