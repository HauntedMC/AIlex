package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.domain.AssistantReply;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantReplyTest {

    @Test
    void evidenceBearingReplyRequiresEveryLineToBeGrounded() {
        AssistantReply reply = new AssistantReply(
                List.of("Survival gebruikt claims.", "Je kunt ze uitbreiden."),
                Set.of("knowledge.survival"),
                "high",
                "",
                List.of(),
                Map.of(0, Set.of("knowledge.survival")),
                true
        );

        assertFalse(reply.valid());
        assertFalse(reply.allLinesGrounded());
    }

    @Test
    void fullyMappedEvidenceBearingReplyRemainsValid() {
        AssistantReply reply = new AssistantReply(
                List.of("Survival gebruikt claims.", "Je kunt ze uitbreiden."),
                Set.of("knowledge.survival"),
                "high",
                "",
                List.of(),
                Map.of(
                        0, Set.of("knowledge.survival"),
                        1, Set.of("knowledge.survival")
                ),
                true
        );

        assertTrue(reply.valid());
        assertTrue(reply.allLinesGrounded());
    }

    @Test
    void plainChatDoesNotNeedArtificialEvidence() {
        AssistantReply reply = AssistantReply.fromPlainText("Hoi!");
        assertTrue(reply.valid());
        assertFalse(reply.allLinesGrounded());
    }
}
