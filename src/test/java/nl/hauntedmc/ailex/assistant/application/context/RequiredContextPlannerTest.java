package nl.hauntedmc.ailex.assistant.application.context;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredContextPlannerTest {

    private final RequiredContextPlanner planner = new RequiredContextPlanner();

    @Test
    void casualConversationShouldNotCaptureWorldOrKnowledge() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.CONVERSATION, AssistantMode.FAST, "hey alles goed?", AssistantSettings.defaults()
        );

        assertFalse(plan.knowledge());
        assertFalse(plan.eventMemory());
        assertTrue(plan.liveSources().isEmpty());
    }

    @Test
    void eventRecallShouldUseEpisodicMemoryWithoutWorldSnapshot() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.EVENT_RECALL,
                AssistantMode.GROUNDED,
                "wat gebeurde er net met die chatgame?",
                AssistantSettings.defaults()
        );

        assertTrue(plan.eventMemory());
        assertTrue(plan.durableMemory());
        assertTrue(plan.liveSources().isEmpty());
    }

    @Test
    void heldItemQuestionShouldUseCompactRequesterStateWithoutFullInventoryScan() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.LIVE_STATE,
                AssistantMode.GROUNDED,
                "wat heb ik in mijn hand?",
                AssistantSettings.defaults()
        );

        assertEquals(Set.of(RequiredContextPlanner.LiveSource.REQUESTER), plan.liveSources());
        assertFalse(plan.liveSources().contains(RequiredContextPlanner.LiveSource.INVENTORY));
    }

    @Test
    void customPlayerFeatureQuestionShouldRequestRequesterContext() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.LIVE_STATE,
                AssistantMode.GROUNDED,
                "wat is mijn rank en saldo?",
                AssistantSettings.defaults()
        );

        assertEquals(Set.of(RequiredContextPlanner.LiveSource.REQUESTER), plan.liveSources());
    }

    @Test
    void nearbyQuestionShouldNotAutomaticallyCaptureServerState() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.LIVE_STATE,
                AssistantMode.GROUNDED,
                "welke spelers zijn dichtbij?",
                AssistantSettings.defaults()
        );

        assertTrue(plan.liveSources().contains(RequiredContextPlanner.LiveSource.NEARBY));
        assertFalse(plan.liveSources().contains(RequiredContextPlanner.LiveSource.SERVER));
    }

    @Test
    void lagQuestionShouldRequestServerPerformanceOnly() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.LIVE_STATE,
                AssistantMode.GROUNDED,
                "heeft de server lag?",
                AssistantSettings.defaults()
        );

        assertEquals(Set.of(RequiredContextPlanner.LiveSource.SERVER), plan.liveSources());
    }

    @Test
    void serverFactShouldRetrieveKnowledgeWithoutLiveSnapshot() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.SERVER_FACT,
                AssistantMode.GROUNDED,
                "hoe werkt /claim?",
                AssistantSettings.defaults()
        );

        assertTrue(plan.knowledge());
        assertFalse(plan.live());
    }
}
