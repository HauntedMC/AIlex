package nl.hauntedmc.ailex.assistant.application.inference;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;

import java.util.Set;

/** Deterministic evidence-class contract for player-facing factual routes. */
public final class AssistantGroundingPolicy {

    private AssistantGroundingPolicy() {
    }

    public static boolean requiresGrounding(AssistantIntent intent) {
        return switch (intent == null ? AssistantIntent.CONVERSATION : intent) {
            case SERVER_FACT, KNOWLEDGE_DISCOVERY, LIVE_STATE, MEMORY_RECALL, EVENT_RECALL, SUPPORT -> true;
            default -> false;
        };
    }

    /**
     * Required factual routes must have positive evidence from their provenance family. Negative lookup observations are
     * useful planner context but cannot validate a player-facing factual answer; they therefore force the normal safe
     * fallback/abstention path.
     */
    public static boolean hasRequiredEvidence(AssistantIntent intent, Set<String> evidenceIds) {
        return hasRequiredEvidence(intent, new EvidencePacket(evidenceIds));
    }

    public static boolean hasRequiredEvidence(AssistantIntent intent, EvidencePacket packet) {
        boolean required = requiresGrounding(intent);
        if (!required) {
            return true;
        }
        if (packet == null || packet.isEmpty()) {
            return false;
        }
        return switch (intent) {
            case LIVE_STATE -> packet.hasPositiveLiveEvidence();
            case MEMORY_RECALL, EVENT_RECALL -> packet.hasPositiveMemoryEvidence();
            case SERVER_FACT, KNOWLEDGE_DISCOVERY, SUPPORT -> packet.hasPositiveKnowledgeEvidence();
            default -> true;
        };
    }
}
