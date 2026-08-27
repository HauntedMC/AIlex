package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssistantIntentProductionRegressionTest {

    @Test
    void addressedMinecraftVersionQuestionIsGameplayNotHauntedMcReleaseMetadata() {
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "haunty wat is minecraft versie 12.1"
        );

        assertEquals(AssistantIntent.GAMEPLAY_HELP, analysis.intent());
        assertEquals(AssistantMode.GROUNDED, analysis.mode());
    }

    @Test
    void explicitHauntyReleaseQuestionRemainsServerFact() {
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "is de nieuwe Haunty versie al live?"
        );

        assertEquals(AssistantIntent.SERVER_FACT, analysis.intent());
        assertEquals(AssistantMode.GROUNDED, analysis.mode());
    }

    @Test
    void capabilityQuestionUsesKnowledgeDiscoveryInsteadOfUngroundedFastChat() {
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "Haunty welke functies heb je allemaal?"
        );

        assertEquals(AssistantIntent.KNOWLEDGE_DISCOVERY, analysis.intent());
        assertEquals(AssistantMode.GROUNDED, analysis.mode());
    }
}
