package nl.hauntedmc.ailex.assistant.proactive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProactiveChatTriggerTest {

    @Test
    void joinTriggerShouldRequireTheExactJoiningPlayerName() {
        ProactiveChatTrigger trigger = ProactiveChatTrigger.join("Alex", "Groet {player_name} persoonlijk en kort.");
        assertTrue(trigger.instruction().contains("Alex"));
        assertTrue(trigger.accepts("Hey Alex, welkom terug!"));
        assertFalse(trigger.accepts("Welkom op de server, veel plezier!"));
        assertFalse(trigger.accepts("Hey Alexandria, welkom terug!"));
    }
}
