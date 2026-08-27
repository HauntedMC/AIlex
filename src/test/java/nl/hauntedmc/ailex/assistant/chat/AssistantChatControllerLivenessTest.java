package nl.hauntedmc.ailex.assistant.chat;

import nl.hauntedmc.ailex.npc.NPC;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantChatControllerLivenessTest {

    @Test
    void chatAvailabilityMustNotDependOnPhysicalCitizensSpawnState() {
        NPC npc = mock(NPC.class);
        when(npc.isChatEnabled()).thenReturn(true);
        when(npc.isSpawned()).thenReturn(false);

        assertTrue(AssistantChatController.chatAvailable(npc));
    }

    @Test
    void explicitlyDisabledNpcMustRemainUnavailableForChat() {
        NPC npc = mock(NPC.class);
        when(npc.isChatEnabled()).thenReturn(false);
        when(npc.isSpawned()).thenReturn(true);

        assertFalse(AssistantChatController.chatAvailable(npc));
        assertFalse(AssistantChatController.chatAvailable(null));
    }
}
