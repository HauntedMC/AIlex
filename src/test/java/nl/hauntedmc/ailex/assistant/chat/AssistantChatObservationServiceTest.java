package nl.hauntedmc.ailex.assistant.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantChatObservationServiceTest {

    @Test
    void recognizesProductionWhoAskedRecall() {
        assertTrue(AssistantChatObservationService.looksLikeRecentEventRecall(
                "Haunty wie vroeg net of ik jou in de time out hoek mag zetten"
        ));
        assertTrue(AssistantChatObservationService.looksLikeRecentEventRecall(
                "Haunty, who just asked whether Remy could put you in timeout?"
        ));
    }

    @Test
    void ordinaryAddressedConversationRemainsObservable() {
        assertFalse(AssistantChatObservationService.looksLikeRecentEventRecall(
                "Haunty, mag Remy je in de time out hoek zetten?"
        ));
    }
}
