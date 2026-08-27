package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Builds longitudinal player↔AIlex relationship state exclusively from explicit player memory, runtime interaction
 * counters and shared episodes. It deliberately excludes inferred mood, personality, affection or private traits.
 */
public final class AssistantRelationshipMemoryService {

    private final AssistantMemoryService memory;

    public AssistantRelationshipMemoryService(AssistantMemoryService memory) {
        this.memory = memory;
    }

    public RelationshipProfile profile(UUID playerId, String npcId) {
        if (memory == null || playerId == null || npcId == null || npcId.isBlank()) {
            return empty();
        }
        String player = playerId.toString();
        List<MemoryRecord> records = memory.activeSnapshot().stream()
                .filter(record -> visibleToRelationship(record, player, npcId))
                .toList();
        Set<String> evidence = new HashSet<>();
        records.forEach(record -> evidence.add(MemoryEvidenceId.forRecord(record)));

        int interactions = value(records, MemoryScope.PLAYER_NPC, MemoryKind.RELATIONSHIP, "interaction_count")
                .map(this::integer).orElse(0);
        long first = value(records, MemoryScope.PLAYER_NPC, MemoryKind.RELATIONSHIP, "first_interaction_at")
                .map(this::longValue).orElse(0L);
        long last = value(records, MemoryScope.PLAYER_NPC, MemoryKind.RELATIONSHIP, "last_interaction_at")
                .map(this::longValue).orElse(0L);
        String language = records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == MemoryKind.PREFERENCE)
                .filter(record -> record.key().equals("language"))
                .map(MemoryRecord::value).findFirst().orElse("");

        List<String> interests = playerValues(records, MemoryKind.INTEREST, 8);
        List<String> goals = playerValues(records, MemoryKind.GOAL, 8);
        List<String> projects = records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER)
                .filter(record -> record.key().contains("project") || record.tags().contains("project"))
                .map(record -> record.key() + '=' + record.value()).distinct().limit(8).toList();
        List<String> preferences = records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == MemoryKind.PREFERENCE)
                .filter(record -> !record.key().equals("language"))
                .filter(record -> responsePreference(record.key()))
                .map(record -> record.key() + '=' + record.value()).distinct().limit(8).toList();
        List<String> episodes = records.stream()
                .filter(record -> record.scope() == MemoryScope.EVENT)
                .filter(record -> npcId.equals(record.relationId()))
                .filter(record -> record.kind() == MemoryKind.EVENT || record.kind() == MemoryKind.EPISODE)
                .filter(record -> !record.tags().contains("experience"))
                .map(MemoryRecord::value).distinct().limit(6).toList();
        List<String> commitments = records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == MemoryKind.GOAL)
                .filter(record -> record.key().contains("commit") || record.key().contains("follow")
                        || record.key().contains("todo") || record.tags().contains("commitment"))
                .map(record -> record.key() + '=' + record.value()).distinct().limit(6).toList();

        return new RelationshipProfile(
                first, last, interactions, familiarity(interactions), language, interests, goals, projects,
                episodes, preferences, commitments, Set.copyOf(evidence)
        );
    }

    public String promptContext(UUID playerId, String npcId) {
        RelationshipProfile profile = profile(playerId, npcId);
        if (!profile.knownPlayer()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add("familiarity=" + profile.familiarity());
        parts.add("interaction_count=" + profile.interactionCount());
        if (!profile.preferredLanguage().isBlank()) {
            parts.add("preferred_language=" + profile.preferredLanguage());
        }
        append(parts, "interests", profile.knownInterests());
        append(parts, "goals", profile.currentGoals());
        append(parts, "projects", profile.currentProjects());
        append(parts, "shared_episodes", profile.sharedEpisodes());
        append(parts, "interaction_preferences", profile.interactionPreferences());
        append(parts, "unresolved_commitments", profile.unresolvedCommitments());
        String context = String.join(" | ", parts);
        return context.length() <= 1_600 ? context : context.substring(0, 1_599) + "…";
    }

    private boolean visibleToRelationship(MemoryRecord record, String playerId, String npcId) {
        return switch (record.scope()) {
            case PLAYER -> record.subjectId().equals(playerId);
            case PLAYER_NPC -> record.subjectId().equals(playerId) && record.relationId().equals(npcId);
            case EVENT -> record.subjectId().equals(playerId)
                    && (record.relationId().isBlank() || record.relationId().equals(npcId));
            case NPC -> record.subjectId().equals(npcId) && record.tags().contains("experience");
            case GLOBAL, WORLD, SESSION -> false;
        };
    }

    private java.util.Optional<String> value(
            List<MemoryRecord> records,
            MemoryScope scope,
            MemoryKind kind,
            String key
    ) {
        return records.stream().filter(record -> record.scope() == scope && record.kind() == kind && record.key().equals(key))
                .map(MemoryRecord::value).findFirst();
    }

    private List<String> playerValues(List<MemoryRecord> records, MemoryKind kind, int limit) {
        return records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == kind)
                .map(record -> record.key() + '=' + record.value()).distinct().limit(limit).toList();
    }

    private boolean responsePreference(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("response") || normalized.contains("answer") || normalized.contains("detail")
                || normalized.contains("style") || normalized.contains("joke") || normalized.contains("humor")
                || normalized.contains("tone") || normalized.contains("length");
    }

    private String familiarity(int interactions) {
        if (interactions >= 100) {
            return "long_term_regular";
        }
        if (interactions >= 25) {
            return "regular";
        }
        if (interactions >= 5) {
            return "familiar";
        }
        if (interactions > 0) {
            return "acquainted";
        }
        return "new";
    }

    private void append(List<String> parts, String name, List<String> values) {
        if (values != null && !values.isEmpty()) {
            parts.add(name + "=[" + String.join("; ", values) + ']');
        }
    }

    private int integer(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long longValue(String value) {
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private RelationshipProfile empty() {
        return new RelationshipProfile(0L, 0L, 0, "new", "", List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), Set.of());
    }
}
