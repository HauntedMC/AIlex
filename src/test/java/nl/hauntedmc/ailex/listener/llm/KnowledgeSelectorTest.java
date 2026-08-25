package nl.hauntedmc.ailex.listener.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeSelectorTest {

    @Test
    void shouldPreferQuestionRelevantKnowledgeSectionsWithinTheBudget() {
        String knowledge = "Official server facts\n"
                + "- RANKS: Elite has /skin and Legend has /fly.\n"
                + "- VOTING: Vote once per day with /vote.\n"
                + "- CREATIVE: Use /plot claim to claim a plot.";

        String selected = KnowledgeSelector.select(knowledge, "How do I use /plot claim?", 110);

        assertTrue(selected.contains("CREATIVE"));
        assertFalse(selected.contains("VOTING"));
        assertTrue(selected.length() <= 110);
    }

    @Test
    void shouldPreserveSimpleKnowledgeThatAlreadyFitsTheBudget() {
        assertTrue(KnowledgeSelector.select("HauntedMC has Survival.", "anything", 100)
                .contains("HauntedMC has Survival."));
    }
}
