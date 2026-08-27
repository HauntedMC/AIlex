package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.AdaptiveEvidencePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveEvidencePolicyTest {

    @Test
    void shouldUseSmallEvidenceSetForClearExactIdentifierQuery() {
        AdaptiveEvidencePolicy.Budget budget = AdaptiveEvidencePolicy.select(
                "Hoe gebruik ik /plot claim?", 12, 32_000, 12.0D, 4.0D
        );

        assertEquals(4, budget.maxChunks());
        assertTrue(budget.maxCharacters() <= 20_000);
    }

    @Test
    void shouldRetainMoreRecallForBroadOrAmbiguousQueries() {
        AdaptiveEvidencePolicy.Budget broad = AdaptiveEvidencePolicy.select(
                "ranks", 12, 32_000, 4.0D, 3.8D
        );
        AdaptiveEvidencePolicy.Budget ambiguous = AdaptiveEvidencePolicy.select(
                "hoe werkt het systeem met ranks perks economy rewards", 12, 32_000, 5.0D, 4.8D
        );

        assertEquals(8, broad.maxChunks());
        assertEquals(6, ambiguous.maxChunks());
        assertTrue(broad.maxCharacters() >= ambiguous.maxCharacters());
    }

    @Test
    void shouldNeverExceedOperatorConfiguredHardCeilings() {
        AdaptiveEvidencePolicy.Budget budget = AdaptiveEvidencePolicy.select(
                "tell me about everything", 3, 7_500, 1.0D, 0.99D
        );

        assertEquals(3, budget.maxChunks());
        assertEquals(7_500, budget.maxCharacters());
    }
}
