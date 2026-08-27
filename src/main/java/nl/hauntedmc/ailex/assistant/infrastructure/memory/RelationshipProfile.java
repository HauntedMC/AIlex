package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.List;
import java.util.Set;

/** Player↔AIlex longitudinal relationship view built only from explicit/observed memory, never inferred traits. */
public record RelationshipProfile(
        long firstInteractionAt,
        long lastInteractionAt,
        int interactionCount,
        String familiarity,
        String preferredLanguage,
        List<String> knownInterests,
        List<String> currentGoals,
        List<String> currentProjects,
        List<String> sharedEpisodes,
        List<String> interactionPreferences,
        List<String> unresolvedCommitments,
        Set<String> evidenceIds
) {
    public RelationshipProfile {
        interactionCount = Math.max(0, interactionCount);
        familiarity = clean(familiarity);
        preferredLanguage = clean(preferredLanguage);
        knownInterests = copy(knownInterests);
        currentGoals = copy(currentGoals);
        currentProjects = copy(currentProjects);
        sharedEpisodes = copy(sharedEpisodes);
        interactionPreferences = copy(interactionPreferences);
        unresolvedCommitments = copy(unresolvedCommitments);
        evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
    }

    public boolean knownPlayer() {
        return interactionCount > 0 || firstInteractionAt > 0L;
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
