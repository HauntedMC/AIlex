package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.AdaptiveEvidencePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveEvidencePolicyTest {

    @Test
    void shouldUseSmallEvidenceSetForClearExactIdentifierQuery() {
        AdaptiveEvidencePolicy.Budget budget = AdaptiveEvidencePolicy.select(
                "Hoe gebruik ik /plot claim?", 64, 140_000, 12.0D, 4.0D
        );

        assertEquals(4, budget.maxChunks());
        assertTrue(budget.maxCharacters() <= 20_000);
    }

    @Test
    void shouldRetainModerateRecallForShortOrAmbiguousQueries() {
        AdaptiveEvidencePolicy.Budget shortQuery = AdaptiveEvidencePolicy.select(
                "ranks", 64, 140_000, 4.0D, 3.8D
        );
        AdaptiveEvidencePolicy.Budget ambiguous = AdaptiveEvidencePolicy.select(
                "hoe werkt het systeem met ranks perks economy rewards", 64, 140_000, 5.0D, 4.8D
        );

        assertEquals(8, shortQuery.maxChunks());
        assertEquals(6, ambiguous.maxChunks());
    }

    @Test
    void shouldUseMuchMoreEvidenceForExplicitOverviewQuestions() {
        AdaptiveEvidencePolicy.Budget dutch = AdaptiveEvidencePolicy.select(
                "wat kan ik allemaal op survival, geef een volledig overzicht van alle functies", 64, 140_000,
                5.0D, 4.8D
        );
        AdaptiveEvidencePolicy.Budget english = AdaptiveEvidencePolicy.select(
                "give me a complete overview of all features on survival", 64, 140_000, 5.0D, 4.8D
        );

        assertEquals(20, dutch.maxChunks());
        assertEquals(100_000, dutch.maxCharacters());
        assertEquals(20, english.maxChunks());
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
