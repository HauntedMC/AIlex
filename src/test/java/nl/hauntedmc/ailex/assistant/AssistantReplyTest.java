package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.domain.AssistantReply;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void plainChatPreservesUsefulBoundedLines() {
        AssistantReply reply = AssistantReply.fromPlainText("Eerste regel\n\nTweede regel\nDerde regel");

        assertTrue(reply.valid());
        assertEquals(List.of("Eerste regel", "Tweede regel", "Derde regel"), reply.lines());
    }

    @Test
    void accidentalLegacyJsonEnvelopeMustNeverLeakIntoMinecraftChat() {
        AssistantReply reply = AssistantReply.fromPlainText(
                "{\"response\":\"Ik kan dat niet verifiëren.\",\"evidence\":[]}"
        );

        assertTrue(reply.valid());
        assertEquals(List.of("Ik kan dat niet verifiëren."), reply.lines());
        assertFalse(reply.lines().getFirst().contains("\"response\""));
        assertFalse(reply.lines().getFirst().contains("\"evidence\""));
    }

    @Test
    void malformedLegacyEnvelopeMatchingProductionScreenshotMustBeRecovered() {
        AssistantReply reply = AssistantReply.fromPlainText(
                "\"response\":\"Ik kan niet verifiëren of de nieuwe Haunty-versie live is.\",\"evidence\":[]"
        );

        assertTrue(reply.valid());
        assertEquals(List.of("Ik kan niet verifiëren of de nieuwe Haunty-versie live is."), reply.lines());
        assertFalse(reply.lines().getFirst().contains("\"response\""));
        assertFalse(reply.lines().getFirst().contains("\"evidence\""));
    }

    @Test
    void accidentalCurrentStructuredEnvelopeOnPlainPathIsUnwrapped() {
        AssistantReply reply = AssistantReply.fromPlainText(
                "{\"lines\":[\"Hoi!\",\"Waarmee kan ik helpen?\"],\"evidence_ids\":[]}"
        );

        assertTrue(reply.valid());
        assertEquals(List.of("Hoi!", "Waarmee kan ik helpen?"), reply.lines());
    }

    @Test
    void unknownJsonProtocolOnPlainPathFailsClosed() {
        AssistantReply reply = AssistantReply.fromPlainText("{\"evidence\":[],\"confidence\":\"low\"}");

        assertFalse(reply.valid());
        assertTrue(reply.lines().isEmpty());
    }
}
