package nl.hauntedmc.ailex.assistant.application.inference;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidencePacketTest {

    @Test
    void packetClassifiesEvidenceByProvenanceNamespace() {
        EvidencePacket packet = EvidencePacket.combine(
                Set.of("knowledge.claims"),
                Set.of("live.requester"),
                Set.of("memory.123")
        );

        assertTrue(packet.hasKnowledgeEvidence());
        assertTrue(packet.hasLiveEvidence());
        assertTrue(packet.hasMemoryEvidence());
        assertTrue(packet.hasPositiveKnowledgeEvidence());
        assertTrue(packet.hasPositiveLiveEvidence());
        assertTrue(packet.hasPositiveMemoryEvidence());
        assertTrue(packet.supportsAll(Set.of("knowledge.claims", "memory.123")));
        assertFalse(packet.supportsAll(Set.of("invented.source")));
        assertFalse(packet.negativeOnly());
    }

    @Test
    void retrievalMissesRemainAbsenceEvidenceAndCannotGroundOtherFactualRoutes() {
        EvidencePacket knowledgeMiss = new EvidencePacket(Set.of("knowledge.none"));
        EvidencePacket liveMiss = new EvidencePacket(Set.of("live.requester.none"));
        EvidencePacket memoryMiss = new EvidencePacket(Set.of("memory.none"));

        assertTrue(knowledgeMiss.negativeOnly());
        assertTrue(liveMiss.negativeOnly());
        assertTrue(memoryMiss.negativeOnly());
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(AssistantIntent.SERVER_FACT, knowledgeMiss));
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(AssistantIntent.LIVE_STATE, liveMiss));
        assertTrue(AssistantGroundingPolicy.hasRequiredEvidence(AssistantIntent.MEMORY_RECALL, memoryMiss));
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(AssistantIntent.SERVER_FACT, memoryMiss));
        assertFalse(AssistantGroundingPolicy.hasRequiredEvidence(
                AssistantIntent.SERVER_FACT, new EvidencePacket(Set.of())
        ));
    }
}
