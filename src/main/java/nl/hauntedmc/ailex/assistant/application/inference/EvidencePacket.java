package nl.hauntedmc.ailex.assistant.application.inference;

import java.util.HashSet;
import java.util.Set;

/**
 * Normalized evidence envelope used by deterministic grounding. IDs retain provenance by namespace: {@code live.*},
 * {@code memory.*}, or reviewed-knowledge IDs. Negative retrieval observations are evidence of absence only and can
 * justify abstention; they are never positive factual support.
 */
public record EvidencePacket(Set<String> ids) {

    public EvidencePacket {
        ids = ids == null ? Set.of() : ids.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @SafeVarargs
    public static EvidencePacket combine(Set<String>... groups) {
        Set<String> combined = new HashSet<>();
        if (groups != null) {
            for (Set<String> group : groups) {
                if (group != null) {
                    combined.addAll(group);
                }
            }
        }
        return new EvidencePacket(Set.copyOf(combined));
    }

    public boolean supportsAll(Set<String> requestedIds) {
        return requestedIds == null || requestedIds.isEmpty() || ids.containsAll(requestedIds);
    }

    public boolean hasLiveEvidence() {
        return ids.stream().anyMatch(id -> id.startsWith("live."));
    }

    public boolean hasMemoryEvidence() {
        return ids.stream().anyMatch(id -> id.startsWith("memory."));
    }

    public boolean hasKnowledgeEvidence() {
        return ids.stream().anyMatch(id -> !id.startsWith("live.") && !id.startsWith("memory."));
    }

    public boolean hasPositiveLiveEvidence() {
        return ids.stream().anyMatch(id -> id.startsWith("live.") && !negative(id));
    }

    public boolean hasPositiveMemoryEvidence() {
        return ids.stream().anyMatch(id -> id.startsWith("memory.") && !negative(id));
    }

    public boolean hasPositiveKnowledgeEvidence() {
        return ids.stream().anyMatch(id -> !id.startsWith("live.") && !id.startsWith("memory.") && !negative(id));
    }

    public boolean negativeOnly() {
        return !ids.isEmpty() && ids.stream().allMatch(EvidencePacket::negative);
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    private static boolean negative(String id) {
        return id != null && (id.equals("knowledge.none")
                || id.equals("memory.none")
                || id.equals("memory.timeline.none")
                || id.startsWith("live.") && id.endsWith(".none"));
    }
}
