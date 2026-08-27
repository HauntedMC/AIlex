package nl.hauntedmc.ailex.assistant.proactive;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProactiveInterventionPolicyTest {

    @Test
    void repeatedInterventionsCanMakeSilenceHigherUtilityThanAnotherAnswer() {
        Player source = player("Alex");
        SocialConversationGraph graph = new SocialConversationGraph();
        long now = 100_000L;
        for (int index = 0; index < 4; index++) {
            graph.recordAilexIntervention(source.getUniqueId(), CommunityGoal.INFORM, now - index * 1_000L, 180_000L);
        }

        InterventionDecision decision = ProactiveInterventionPolicy.evaluateQuestion(
                source, "Hoe werkt /claim?", List.of(source), false, graph, now, settings()
        );

        assertFalse(decision.speak());
        assertEquals(CommunityGoal.SILENCE, decision.goal());
        assertTrue(decision.repetitionPenalty() > 0.7D);
    }

    @Test
    void explicitBroadcastCanOverrideConversationSuppression() {
        Player source = player("Alex");
        InterventionDecision decision = ProactiveInterventionPolicy.evaluateQuestion(
                source, "Weet iemand hoe claims werken?", List.of(source), true,
                new SocialConversationGraph(), 100_000L, settings()
        );

        assertTrue(decision.speak());
        assertEquals(CommunityGoal.INFORM, decision.goal());
        assertTrue(decision.helpfulProbability() > decision.privateConversationProbability());
    }

    private static ProactiveChatSettings.QuestionSettings settings() {
        return new ProactiveChatSettings.QuestionSettings(
                true, 1.0D, 45_000L, 2, 180_000L, 2.5D,
                0.25D, 1.25D, 1.20D, 0.75D, 0.85D
        );
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        return player;
    }
}
