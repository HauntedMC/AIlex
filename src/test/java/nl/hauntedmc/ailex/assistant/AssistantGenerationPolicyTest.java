package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.application.inference.AssistantGenerationPolicy;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantGenerationPolicyTest {

    @Test
    void shouldKeepOrdinaryFastChatOnPlainTextPath() {
        assertFalse(AssistantGenerationPolicy.useStructuredOutput(
                true, AssistantMode.FAST, AssistantIntent.CONVERSATION, "hey haunty alles goed?"
        ));
    }

    @Test
    void shouldUseStructuredOutputWhenFastMessageContainsDurableMemory() {
        assertTrue(AssistantGenerationPolicy.useStructuredOutput(
                true, AssistantMode.FAST, AssistantIntent.CONVERSATION, "mijn favoriete gamemode is survival"
        ));
    }

    @Test
    void shouldAlwaysStructureGroundedAnswersWhenEnabled() {
        assertTrue(AssistantGenerationPolicy.useStructuredOutput(
                true, AssistantMode.GROUNDED, AssistantIntent.SERVER_FACT, "hoe werkt /claim?"
        ));
    }

    @Test
    void shouldOnlyEscalateGroundedWorkWithBudgetAndTimeLeft() {
        assertTrue(AssistantGenerationPolicy.mayEscalate(AssistantMode.GROUNDED, 1, 3, 5_000L));
        assertFalse(AssistantGenerationPolicy.mayEscalate(AssistantMode.FAST, 1, 3, 5_000L));
        assertFalse(AssistantGenerationPolicy.mayEscalate(AssistantMode.GROUNDED, 3, 3, 5_000L));
        assertFalse(AssistantGenerationPolicy.mayEscalate(AssistantMode.GROUNDED, 1, 3, 1_000L));
    }
}
