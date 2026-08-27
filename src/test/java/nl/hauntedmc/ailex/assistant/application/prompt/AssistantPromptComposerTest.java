package nl.hauntedmc.ailex.assistant.application.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantPromptComposerTest {

    @Test
    void stableContractSeparatesEpistemologyMemoryCapabilitiesAndInteraction() {
        String prompt = AssistantPromptComposer.stableContractForTest();

        assertTrue(prompt.contains("EPISTEMIC CONTRACT"));
        assertTrue(prompt.contains("MEMORY CONTRACT"));
        assertTrue(prompt.contains("CAPABILITY CONTRACT"));
        assertTrue(prompt.contains("INTERACTION CONTRACT"));
        assertTrue(prompt.contains("procedural experience"));
        assertTrue(prompt.contains("Physical actions are proposals only"));
    }

    @Test
    void stableContractDoesNotWasteTokensDescribingStructuredJsonFields() {
        String prompt = AssistantPromptComposer.stableContractForTest();

        assertFalse(prompt.contains("claim_evidence"));
        assertFalse(prompt.contains("memory_candidates"));
        assertFalse(prompt.contains("action_proposals"));
    }
}
