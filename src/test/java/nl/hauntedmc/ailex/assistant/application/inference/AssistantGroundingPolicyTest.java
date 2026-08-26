package nl.hauntedmc.ailex.assistant.application.inference;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantGroundingPolicyTest {

    @Test
    void groundedRoutesFailClosedWhenRetrievalReturnsNothing() {
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
    void evidenceMustMatchTheRouteSourceFamily() {
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.SERVER_FACT, Set.of("knowledge.claims.0")
        ));
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.SERVER_FACT, Set.of("knowledge.none")
        ));
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.SERVER_FACT, Set.of("memory.fact")
        ));
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.LIVE_STATE, Set.of("live.requester.none")
        ));
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.LIVE_STATE, Set.of("knowledge.claims.0")
        ));
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.MEMORY_RECALL, Set.of("memory.none")
        ));
    }

    @Test
    void ordinaryConversationAndGameplayCanUseGeneralModelKnowledge() {
        assertFalse(AssistantGroundingPolicy.requiresGrounding(AssistantIntent.CONVERSATION));
        assertFalse(AssistantGroundingPolicy.requiresGrounding(AssistantIntent.GAMEPLAY_HELP));
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(AssistantIntent.CONVERSATION, Set.of()));
    }
}
