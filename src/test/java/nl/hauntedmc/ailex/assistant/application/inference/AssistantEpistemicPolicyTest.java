package nl.hauntedmc.ailex.assistant.application.inference;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantEpistemicPolicyTest {

    @Test
    void negativeObservationsNeverGroundFactualRoutes() {
        assertFalse(AssistantEpistemicPolicy.canGround(
                AssistantIntent.LIVE_STATE, new EvidencePacket(Set.of("live.server.none"))
        ));
        assertFalse(AssistantEpistemicPolicy.canGround(
                AssistantIntent.SERVER_FACT, new EvidencePacket(Set.of("knowledge.none"))
        ));
    }

    @Test
    void routeRequiresCorrectPositiveEvidenceFamily() {
        assertTrue(AssistantEpistemicPolicy.canGround(
                AssistantIntent.LIVE_STATE, new EvidencePacket(Set.of("live.server.tps"))
        ));
        assertFalse(AssistantEpistemicPolicy.canGround(
                AssistantIntent.LIVE_STATE, new EvidencePacket(Set.of("knowledge.server-overview"))
        ));
        assertTrue(AssistantEpistemicPolicy.canGround(
                AssistantIntent.SERVER_FACT, new EvidencePacket(Set.of("knowledge.claims"))
        ));
    }
}
