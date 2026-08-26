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
}
