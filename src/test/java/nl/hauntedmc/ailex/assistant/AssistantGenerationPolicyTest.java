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
        assertTrue(AssistantGenerationPolicy.useStructuredOutput(
                true, AssistantMode.FAST, AssistantIntent.CONVERSATION, "Ik heb twee katten."
        ));
        assertTrue(AssistantGenerationPolicy.useStructuredOutput(
                true, AssistantMode.FAST, AssistantIntent.CONVERSATION, "My current project is a castle."
        ));
    }

    @Test
    void shouldNotStructureAmbientStatementsWithoutDurableSignal() {
        assertFalse(AssistantGenerationPolicy.hasDurableMemorySignal("mooie spawn vandaag"));
        assertFalse(AssistantGenerationPolicy.hasDurableMemorySignal("waar is de shop?"));
    }

    @Test
    void shouldAlwaysStructureGroundedAnswersWhenEnabled() {
        assertTrue(AssistantGenerationPolicy.useStructuredOutput(
                true, AssistantMode.GROUNDED, AssistantIntent.SERVER_FACT, "hoe werkt /claim?"
        ));
    }

    @Test
    void shouldEscalateGroundedOrDeliberateWorkOnlyWithBudgetAndTimeLeft() {
        assertTrue(AssistantGenerationPolicy.mayEscalate(AssistantMode.GROUNDED, 1, 3, 5_000L));
        assertTrue(AssistantGenerationPolicy.mayEscalate(AssistantMode.DELIBERATE, 1, 3, 5_000L));
        assertFalse(AssistantGenerationPolicy.mayEscalate(AssistantMode.FAST, 1, 3, 5_000L));
        assertFalse(AssistantGenerationPolicy.mayEscalate(AssistantMode.GROUNDED, 3, 3, 5_000L));
        assertFalse(AssistantGenerationPolicy.mayEscalate(AssistantMode.GROUNDED, 1, 3, 1_000L));
    }
}
