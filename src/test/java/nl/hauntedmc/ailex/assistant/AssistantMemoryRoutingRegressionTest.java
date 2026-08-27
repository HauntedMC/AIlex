package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantMemoryRoutingRegressionTest {

    @Test
    void favoriteBlockDeclarationIsMemoryConversationNotLiveState() {
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "Haunty mijn lievelings block is netherite block"
        );

        assertEquals(AssistantIntent.CONVERSATION, analysis.intent());
        assertEquals(AssistantMode.FAST, analysis.mode());
        assertTrue(AssistantIntentClassifier.isMemoryWriteStatement(
                "Haunty mijn lievelings block is netherite block"
        ));
    }

    @Test
    void actualCurrentBlockQuestionRemainsLiveState() {
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "Haunty welk block kijk ik naar?"
        );

        assertEquals(AssistantIntent.LIVE_STATE, analysis.intent());
        assertFalse(AssistantIntentClassifier.isMemoryWriteStatement("Haunty welk block kijk ik naar?"));
    }

    @Test
    void explicitRememberDoesNotTurnIntoRecallInsideActiveDialogue() {
        AssistantDialogueContext dialogue = new AssistantDialogueContext(
                true,
                false,
                AssistantIntent.MEMORY_RECALL,
                "wat weet je over mij",
                "Nog niet veel.",
                ""
        );

        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "Haunty onthou dat ik al sinds 2013 hier speel",
                dialogue
        );

        assertEquals(AssistantIntent.CONVERSATION, analysis.intent());
        assertEquals(AssistantMode.FAST, analysis.mode());
        assertTrue(AssistantIntentClassifier.isMemoryWriteStatement(
                "Haunty onthou dat ik al sinds 2013 hier speel"
        ));
    }

    @Test
    void volunteeredAppearanceFactIsAWriteSignal() {
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "Haunty onthou ik heb bruin haar"
        );

        assertEquals(AssistantIntent.CONVERSATION, analysis.intent());
        assertTrue(AssistantIntentClassifier.isMemoryWriteStatement("Haunty onthou ik heb bruin haar"));
    }

    @Test
    void productionRecentEventQuestionRoutesThroughEventMemoryInActiveDialogue() {
        AssistantDialogueContext dialogue = new AssistantDialogueContext(
                true,
                false,
                AssistantIntent.CONVERSATION,
                "klopt helemaal",
                "Mooi!",
                ""
        );

        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "Haunty wie vroeg net of ik jou in de time out hoek mag zetten",
                dialogue
        );

        assertEquals(AssistantIntent.EVENT_RECALL, analysis.intent());
        assertEquals(AssistantMode.GROUNDED, analysis.mode());
    }
}
