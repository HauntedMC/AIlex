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
        String normalizedPrompt = prompt.replaceAll("\\s+", " ");
        assertTrue(normalizedPrompt.contains("procedural experience"));
        assertTrue(normalizedPrompt.contains("Physical actions are proposals only"));
    }

    @Test
    void stableContractTreatsExactServerIdentifiersAsGroundedProperNames() {
        String normalizedPrompt = AssistantPromptComposer.stableContractForTest().replaceAll("\\s+", " ");

        assertTrue(normalizedPrompt.contains("Discord channel names"));
        assertTrue(normalizedPrompt.contains("must occur in trusted evidence before you mention them"));
        assertTrue(normalizedPrompt.contains("never translate, localize, pluralize or guess them"));
        assertTrue(normalizedPrompt.contains("Discord channel name must stay English"));
    }

    @Test
    void stableContractDoesNotWasteTokensDescribingStructuredJsonFields() {
        String prompt = AssistantPromptComposer.stableContractForTest();

        assertFalse(prompt.contains("claim_evidence"));
        assertFalse(prompt.contains("memory_candidates"));
        assertFalse(prompt.contains("action_proposals"));
    }
}
