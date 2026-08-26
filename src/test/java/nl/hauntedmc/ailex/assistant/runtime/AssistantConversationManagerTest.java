package nl.hauntedmc.ailex.assistant.runtime;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantConversationManagerTest {

    @Test
    void keepsCompactDialogueStateAndTracksPendingAnswers() {
        AtomicLong clock = new AtomicLong(1_000L);
        AssistantConversationManager manager = new AssistantConversationManager(clock::get);
        UUID player = UUID.randomUUID();

        manager.recordUser(player, 7, "remymine", "wat gaat er mis haunty");
        AssistantConversationManager.Snapshot pending = manager.snapshot(player, 7, 60_000L);

        assertTrue(pending.active());
        assertTrue(pending.pendingAnswer());
        assertEquals("wat gaat er mis haunty", pending.previousUserMessage());
        assertTrue(pending.promptContext().contains("pending_answer=true"));

        clock.addAndGet(500L);
        manager.recordAssistant(player, 7, "Haunty", "De chatgame lijkt vastgelopen.", AssistantIntent.EVENT_RECALL);
        AssistantConversationManager.Snapshot answered = manager.snapshot(player, 7, 60_000L);

        assertFalse(answered.pendingAnswer());
        assertEquals(AssistantIntent.EVENT_RECALL, answered.previousIntent());
        assertEquals("De chatgame lijkt vastgelopen.", answered.previousAssistantMessage());
    }

    @Test
    void recognisesShortContinuationWithoutAnotherNpcMention() {
        AtomicLong clock = new AtomicLong(1_000L);
        AssistantConversationManager manager = new AssistantConversationManager(clock::get);
        UUID player = UUID.randomUUID();
        manager.recordUser(player, 3, "remymine", "wat gaat er mis haunty");
        manager.recordAssistant(player, 3, "Haunty", "Tryme lijkt vastgelopen.", AssistantIntent.EVENT_RECALL);

        AssistantConversationManager.Snapshot snapshot = manager.snapshot(player, 3, 60_000L);

        assertTrue(manager.isLikelyFollowUp("waarom?", snapshot));
        assertTrue(manager.isLikelyFollowUp("nee ik bedoel die vorige ronde", snapshot));
        assertFalse(manager.isLikelyFollowUp(
                "Ik ga nu een volledig andere lange discussie beginnen over iets dat niets met het gesprek te maken heeft.",
                snapshot
        ));
    }

    @Test
    void expiresInactiveDialogueAndSelectsLatestActiveNpc() {
        AtomicLong clock = new AtomicLong(10_000L);
        AssistantConversationManager manager = new AssistantConversationManager(clock::get);
        UUID player = UUID.randomUUID();
        manager.recordUser(player, 1, "player", "eerste");
        clock.addAndGet(500L);
        manager.recordUser(player, 2, "player", "tweede");

        AssistantConversationManager.ActiveTarget active = manager.activeTarget(player, 2_000L);
        assertNotNull(active);
        assertEquals(2, active.npcId());

        clock.addAndGet(3_000L);
        assertNull(manager.activeTarget(player, 2_000L));
        assertFalse(manager.snapshot(player, 2, 2_000L).active());
    }
}
