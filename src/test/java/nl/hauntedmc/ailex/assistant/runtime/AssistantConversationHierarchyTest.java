package nl.hauntedmc.ailex.assistant.runtime;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantConversationHierarchyTest {

    @Test
    void shouldRetainTopicsAndCompressedMidtermStateWhenRecentTurnsRollOver() {
        AtomicLong clock = new AtomicLong(1_000L);
        AssistantConversationManager manager = new AssistantConversationManager(clock::get);
        UUID player = UUID.randomUUID();

        for (int index = 0; index < 20; index++) {
            manager.recordUser(
                    player, 4, "player", "lottery trekking rewards gesprek nummer " + index
            );
            manager.recordAssistant(
                    player, 4, "Haunty", "antwoord over de lottery " + index, AssistantIntent.SERVER_FACT
            );
            clock.incrementAndGet();
        }

        AssistantConversationManager.Snapshot snapshot = manager.snapshot(player, 4, 60_000L);

        assertTrue(snapshot.promptContext().contains("session_topics="));
        assertTrue(snapshot.promptContext().contains("lottery"));
        assertTrue(snapshot.promptContext().contains("trekking"));
        assertTrue(snapshot.promptContext().contains("midterm_dialogue="));
        assertTrue(snapshot.promptContext().contains("user: lottery trekking rewards gesprek nummer"));
        assertTrue(snapshot.promptContext().contains("assistant: antwoord over de lottery"));
    }

    @Test
    void longTurnsShouldRollIntoMidtermBeforeTheSerializedWindowCanClipThem() {
        AtomicLong clock = new AtomicLong(1_000L);
        AssistantConversationManager manager = new AssistantConversationManager(clock::get);
        UUID player = UUID.randomUUID();

        for (int index = 0; index < 4; index++) {
            String userMarker = index == 0 ? "OLDEST_MARKER " : "user-" + index + ' ';
            String assistantMarker = index == 3 ? "NEWEST_MARKER " : "assistant-" + index + ' ';
            manager.recordUser(player, 9, "player", userMarker + "context ".repeat(95));
            manager.recordAssistant(
                    player,
                    9,
                    "Haunty",
                    assistantMarker + "answer ".repeat(110),
                    AssistantIntent.CONTEXT_FOLLOWUP
            );
            clock.incrementAndGet();
        }

        AssistantConversationManager.Snapshot snapshot = manager.snapshot(player, 9, 60_000L);

        assertTrue(snapshot.promptContext().contains("midterm_dialogue="));
        assertTrue(snapshot.promptContext().contains("OLDEST_MARKER"));
        assertTrue(snapshot.promptContext().contains("NEWEST_MARKER"));
        assertTrue(snapshot.promptContext().length() <= 8_000);
    }
}
