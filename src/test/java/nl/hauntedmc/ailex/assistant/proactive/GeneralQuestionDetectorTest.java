package nl.hauntedmc.ailex.assistant.proactive;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneralQuestionDetectorTest {

    @Test
    void shouldIgnoreQuestionsAddressedToAnotherPlayer() {
        Player source = mock(Player.class);
        Player alex = mock(Player.class);
        when(alex.getName()).thenReturn("Alex");
        assertTrue(GeneralQuestionDetector.isGeneralQuestion("Waar vind ik diamonds?", source, List.of(source, alex)));
        assertFalse(GeneralQuestionDetector.isGeneralQuestion("Alex, waar vind ik diamonds?", source, List.of(source, alex)));
        assertFalse(GeneralQuestionDetector.isGeneralQuestion("@Alex waar ben je?", source, List.of(source, alex)));
        assertFalse(GeneralQuestionDetector.isGeneralQuestion("Is dit voor Alex?", source, List.of(source, alex)));
    }

    @Test
    void shouldSuppressContextualQuestionsDuringAnActivePlayerConversation() {
        Player source = mock(Player.class);
        Player alex = mock(Player.class);
        when(alex.getName()).thenReturn("Alex");

        assertFalse(GeneralQuestionDetector.isGeneralQuestion(
                "Maar waarom doe je dat?", source, List.of(source, alex), true
        ));
        assertFalse(GeneralQuestionDetector.isGeneralQuestion(
                "Wat bedoel je?", source, List.of(source, alex), true
        ));
    }

    @Test
    void explicitBroadcastQuestionsShouldStillBeEligibleDuringConversation() {
        Player source = mock(Player.class);
        Player alex = mock(Player.class);
        when(alex.getName()).thenReturn("Alex");

        assertTrue(GeneralQuestionDetector.isGeneralQuestion(
                "Weet iemand hoe claims werken?", source, List.of(source, alex), true
        ));
        assertTrue(GeneralQuestionDetector.isGeneralQuestion(
                "Anyone know where the vote menu is?", source, List.of(source, alex), true
        ));
    }
}
