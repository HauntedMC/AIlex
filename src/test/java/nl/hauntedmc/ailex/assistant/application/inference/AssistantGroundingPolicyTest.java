package nl.hauntedmc.ailex.assistant.application.inference;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantGroundingPolicyTest {

    @Test
    void groundedRoutesFailClosedWhenNoObservationWasPerformed() {
        for (AssistantIntent intent : Set.of(
                AssistantIntent.SERVER_FACT,
                AssistantIntent.KNOWLEDGE_DISCOVERY,
                AssistantIntent.LIVE_STATE,
                AssistantIntent.MEMORY_RECALL,
                AssistantIntent.EVENT_RECALL,
                AssistantIntent.SUPPORT
        )) {
            assertTrue(AssistantGroundingPolicy.requiresGrounding(intent));
            assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(intent, Set.of()), intent.name());
        }
    }

    @Test
    void positiveEvidenceMustMatchTheRouteSourceFamily() {
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.SERVER_FACT, Set.of("knowledge.claims.0")
        ));
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.SERVER_FACT, Set.of("knowledge.none")
        ));
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.SERVER_FACT, Set.of("memory.player.fact")
        ));

        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.LIVE_STATE, Set.of("live.requester")
        ));
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.LIVE_STATE, Set.of("live.requester.none")
        ));
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.LIVE_STATE, Set.of("knowledge.claims.0")
        ));

        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.MEMORY_RECALL, Set.of("memory.player.fact")
        ));
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.EVENT_RECALL, Set.of("memory.event.1234")
        ));
    }

    @Test
    void scopedMemoryMissCanGroundOnlyAMemoryAbsenceAnswer() {
        assertEquals(EvidenceClass.AUTHORITATIVE_MEMORY_ABSENCE,
                AssistantEpistemicPolicy.classify("memory.none"));
        assertEquals(EvidenceClass.AUTHORITATIVE_MEMORY_ABSENCE,
                AssistantEpistemicPolicy.classify("memory.timeline.none"));
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.MEMORY_RECALL, Set.of("memory.none")
        ));
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.EVENT_RECALL, Set.of("memory.timeline.none")
        ));
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.SERVER_FACT, Set.of("memory.none")
        ));
    }

    @Test
    void typedMemoryIdsPreserveEventAndSharedProvenance() {
        assertEquals(EvidenceClass.PLAYER_MEMORY,
                AssistantEpistemicPolicy.classify("memory.player.abc"));
        assertEquals(EvidenceClass.SHARED_MEMORY,
                AssistantEpistemicPolicy.classify("memory.shared.abc"));
        assertEquals(EvidenceClass.EVENT_MEMORY,
                AssistantEpistemicPolicy.classify("memory.event.abc"));
        assertEquals(EvidenceClass.EVENT_MEMORY,
                AssistantEpistemicPolicy.classify("memory.episode.abc"));
    }

    @Test
    void ordinaryConversationAndGameplayCanUseGeneralModelKnowledge() {
        assertFalse(AssistantGroundingPolicy.requiresGrounding(AssistantIntent.CONVERSATION));
        assertFalse(AssistantGroundingPolicy.requiresGrounding(AssistantIntent.GAMEPLAY_HELP));
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(AssistantIntent.CONVERSATION, Set.of()));
    }
}
