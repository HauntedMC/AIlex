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
        return recordVerifiedOutcome(
                npcId,
                intent,
                inferType(outcome),
                lessonKey,
                lesson,
                outcome,
                evidenceIds
        );
    }

    public MemoryRecord recordVerifiedOutcome(
            String npcId,
            AssistantIntent intent,
            ExperienceType type,
            String lessonKey,
            String lesson,
            String outcome,
            Set<String> evidenceIds
    ) {
        if (memoryService == null || blank(npcId) || blank(lessonKey) || blank(lesson)) {
            return null;
        }
        ExperienceType effectiveType = type == null ? ExperienceType.USER_FEEDBACK : type;
        String normalizedOutcome = clean(outcome).toLowerCase(Locale.ROOT);
        boolean failure = switch (effectiveType) {
            case FAILED_ANSWER, CORRECTION, RETRIEVAL_FAILURE, AMBIGUOUS_INTENT, UNHELPFUL_INTERRUPTION -> true;
            case SUCCESSFUL_TOOL_PATH, USER_FEEDBACK, HANDOFF_SUCCESS -> normalizedOutcome.contains("fail")
                    || normalizedOutcome.contains("reject") || normalizedOutcome.contains("unverified");
        };
        Set<String> tags = new java.util.HashSet<>();
        tags.add("experience");
        tags.add("procedural");
        tags.add("verified");
        tags.add(failure ? "failure" : "success");
        tags.add("experience-" + effectiveType.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        if (intent != null) {
            tags.add("intent-" + intent.name().toLowerCase(Locale.ROOT));
        }
        if (evidenceIds != null) {
            evidenceIds.stream().map(this::safeTag).filter(value -> !value.isBlank()).limit(8).forEach(tags::add);
        }
        String value = compact(
                "type=" + effectiveType.name().toLowerCase(Locale.ROOT)
                        + " | lesson=" + clean(lesson)
                        + " | outcome=" + normalizedOutcome
        );
        return memoryService.rememberTrusted(
                MemoryScope.NPC,
                clean(npcId),
                "",
                MemoryKind.EPISODE,
                "experience." + safeKey(lessonKey),
                value,
                failure ? 0.98D : 0.94D,
                failure ? 0.92D : 0.82D,
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

    /** Success/failure history for one reusable strategy key, used as a conservative test-time learned prior. */
    public StrategyStatistics statistics(UUID playerId, String npcId, String lessonKey) {
        if (memoryService == null || playerId == null || blank(npcId) || blank(lessonKey)) {
            return new StrategyStatistics(0, 0);
        }
        String key = "experience." + safeKey(lessonKey);
        List<MemoryRecord> matches = memoryService.timeline(playerId, npcId, key, 32).stream()
                .filter(record -> record.scope() == MemoryScope.NPC)
                .filter(record -> record.tags().contains("experience"))
                .filter(record -> record.key().equals(key))
                .toList();
        int successes = (int) matches.stream().filter(record -> record.tags().contains("success")).count();
        int failures = (int) matches.stream().filter(record -> record.tags().contains("failure")).count();
        return new StrategyStatistics(successes, failures);
    }

    private ExperienceType inferType(String outcome) {
        String normalized = clean(outcome).toLowerCase(Locale.ROOT);
        if (normalized.contains("correction")) {
            return ExperienceType.CORRECTION;
        }
        if (normalized.contains("retrieval") && (normalized.contains("fail") || normalized.contains("miss"))) {
            return ExperienceType.RETRIEVAL_FAILURE;
        }
        if (normalized.contains("tool") && normalized.contains("success")) {
            return ExperienceType.SUCCESSFUL_TOOL_PATH;
        }
        if (normalized.contains("handoff") && normalized.contains("success")) {
            return ExperienceType.HANDOFF_SUCCESS;
        }
        if (normalized.contains("unverified") || normalized.contains("reject") || normalized.contains("fail")) {
            return ExperienceType.FAILED_ANSWER;
        }
        return ExperienceType.USER_FEEDBACK;
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

    public record StrategyStatistics(int successes, int failures) {
        public StrategyStatistics {
            successes = Math.max(0, successes);
            failures = Math.max(0, failures);
        }

        public double successRate() {
            int total = successes + failures;
            return total == 0 ? 0.5D : (double) successes / total;
        }
    }
}
