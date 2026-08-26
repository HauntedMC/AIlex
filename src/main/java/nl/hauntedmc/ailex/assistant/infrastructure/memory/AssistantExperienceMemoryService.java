package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores evidence-grounded procedural experience separately from player identity.
 *
 * <p>Experience is encoded as NPC-scoped episodic memory tagged {@code experience/procedural}. It is never created
 * merely because the model criticised itself: callers must provide a deterministic verifier outcome, a trusted
 * correction, or another externally grounded signal.</p>
 */
public final class AssistantExperienceMemoryService {

    private static final Duration EXPERIENCE_TTL = Duration.ofDays(180);
    private final AssistantMemoryService memoryService;

    public AssistantExperienceMemoryService(AssistantMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    public MemoryRecord recordVerifiedOutcome(
            String npcId,
            AssistantIntent intent,
            String lessonKey,
            String lesson,
            String outcome,
            Set<String> evidenceIds
    ) {
        if (memoryService == null || blank(npcId) || blank(lessonKey) || blank(lesson)) {
            return null;
        }
        String normalizedOutcome = clean(outcome).toLowerCase(Locale.ROOT);
        boolean failure = normalizedOutcome.contains("fail") || normalizedOutcome.contains("reject")
                || normalizedOutcome.contains("unverified") || normalizedOutcome.contains("correction");
        Set<String> tags = new java.util.HashSet<>();
        tags.add("experience");
        tags.add("procedural");
        tags.add("verified");
        tags.add(failure ? "failure" : "success");
        if (intent != null) {
            tags.add("intent-" + intent.name().toLowerCase(Locale.ROOT));
        }
        if (evidenceIds != null) {
            evidenceIds.stream().map(this::safeTag).filter(value -> !value.isBlank()).limit(8).forEach(tags::add);
        }
        String value = compact("lesson=" + clean(lesson) + " | outcome=" + normalizedOutcome);
        return memoryService.rememberTrusted(
                MemoryScope.NPC,
                clean(npcId),
                "",
                MemoryKind.EPISODE,
                "experience." + safeKey(lessonKey),
                value,
                failure ? 0.98D : 0.92D,
                failure ? 0.92D : 0.78D,
                "runtime-verified-experience",
                normalizedOutcome,
                System.currentTimeMillis(),
                EXPERIENCE_TTL,
                Set.copyOf(tags)
        );
    }

    public List<MemoryRecord> recall(UUID playerId, String npcId, String query, int maximumResults) {
        if (memoryService == null || playerId == null || blank(npcId) || maximumResults <= 0) {
            return List.of();
        }
        return memoryService.search(
                playerId,
                npcId,
                query,
                Set.of(MemoryKind.EPISODE),
                Math.clamp(maximumResults * 4, 4, 64)
        ).stream()
                .filter(record -> record.scope() == MemoryScope.NPC)
                .filter(record -> record.tags().contains("experience"))
                .limit(Math.clamp(maximumResults, 1, 16))
                .toList();
    }

    private String safeKey(String value) {
        String safe = clean(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-");
        if (safe.length() > 64) {
            safe = safe.substring(0, 64);
        }
        return safe.isBlank() ? "general" : safe;
    }

    private String safeTag(String value) {
        String safe = clean(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        return safe.length() <= 48 ? safe : safe.substring(0, 48);
    }

    private String compact(String value) {
        String normalized = clean(value);
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 299) + "…";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
