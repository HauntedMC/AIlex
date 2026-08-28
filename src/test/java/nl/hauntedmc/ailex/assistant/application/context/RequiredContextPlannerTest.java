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
    void casualConversationShouldNotCaptureUnneededLongTermContext() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.CONVERSATION, AssistantMode.FAST, "hey alles goed?", AssistantSettings.defaults()
        );

        assertFalse(plan.knowledge());
        assertFalse(plan.durableMemory());
        assertFalse(plan.eventMemory());
        assertTrue(plan.liveSources().isEmpty());
    }

    @Test
    void personalizedConversationShouldRetrieveDurableMemory() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.CONVERSATION,
                AssistantMode.FAST,
                "wat zou je mij aanraden voor mijn project?",
                AssistantSettings.defaults()
        );

        assertTrue(plan.durableMemory());
        assertFalse(plan.knowledge());
        assertFalse(plan.eventMemory());
    }

    @Test
    void selfMemoryRecallShouldRemainPlayerScoped() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.MEMORY_RECALL,
                AssistantMode.GROUNDED,
                "wat weet je over mij?",
                AssistantSettings.defaults()
        );

        assertTrue(plan.durableMemory());
        assertFalse(plan.eventMemory());
        assertTrue(plan.liveSources().isEmpty());
    }

    @Test
    void topicalMemoryRecallShouldNotPullUnrelatedPublicConversations() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.MEMORY_RECALL,
                AssistantMode.GROUNDED,
                "wat heb je onthouden over eten?",
                AssistantSettings.defaults()
        );

        assertTrue(plan.durableMemory());
        assertFalse(plan.eventMemory());
        assertTrue(plan.liveSources().isEmpty());
    }

    @Test
    void namedPlayerMemoryRecallShouldIncludePublicNpcObservations() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.MEMORY_RECALL,
                AssistantMode.GROUNDED,
                "wat weet je over stuyvert haunty",
                AssistantSettings.defaults()
        );

        assertTrue(plan.durableMemory());
        assertTrue(plan.eventMemory());
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
    void heldItemQuestionShouldUseCompactRequesterStateWithoutDurableProfile() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.LIVE_STATE,
                AssistantMode.GROUNDED,
                "wat heb ik in mijn hand?",
                AssistantSettings.defaults()
        );

        assertEquals(Set.of(RequiredContextPlanner.LiveSource.REQUESTER), plan.liveSources());
        assertFalse(plan.liveSources().contains(RequiredContextPlanner.LiveSource.INVENTORY));
        assertFalse(plan.durableMemory());
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
        assertFalse(plan.durableMemory());
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
    void serverFactShouldRetrieveKnowledgeAndSharedMemoryWithoutLiveSnapshot() {
        RequiredContextPlanner.Plan plan = planner.plan(
                AssistantIntent.SERVER_FACT,
                AssistantMode.GROUNDED,
                "hoe werkt /claim?",
                AssistantSettings.defaults()
        );

        assertTrue(plan.knowledge());
        assertTrue(plan.durableMemory());
        assertFalse(plan.live());
    }
}
