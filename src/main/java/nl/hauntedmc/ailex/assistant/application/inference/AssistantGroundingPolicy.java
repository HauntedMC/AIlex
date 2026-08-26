package nl.hauntedmc.ailex.assistant.application.inference;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;

import java.util.Set;

/** Deterministic evidence-class contract for player-facing factual routes. */
public final class AssistantGroundingPolicy {

    private AssistantGroundingPolicy() {
    }

    public static boolean requiresGrounding(AssistantIntent intent) {
        return !AssistantEpistemicPolicy.requiredPositiveClasses(intent).isEmpty();
    }

    /**
     * Required factual routes must have positive evidence from their authoritative provenance family. Negative lookup
     * observations are planner context only and force abstention/retrieval rather than validating an answer.
     */
    public static boolean hasRequiredEvidence(AssistantIntent intent, Set<String> evidenceIds) {
        return hasRequiredEvidence(intent, new EvidencePacket(evidenceIds));
    }

    public static boolean hasRequiredEvidence(AssistantIntent intent, EvidencePacket packet) {
        return AssistantEpistemicPolicy.canGround(intent, packet);
    }
}
