package nl.hauntedmc.ailex.assistant.application.routing;

import nl.hauntedmc.ailex.assistant.application.context.RequiredContextPlanner;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.SemanticEmbeddingProvider;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticNeedPlannerTest {

    @Test
    void paraphrasedInventoryQuestionRefinesConversationPriorToLiveRequesterNeed() {
        SemanticNeedPlanner planner = new SemanticNeedPlanner(new FakeEmbeddings());
        AssistantSettings settings = settings();
        AssistantIntentClassifier.Analysis prior = new AssistantIntentClassifier.Analysis(
                AssistantIntent.CONVERSATION, AssistantMode.FAST, "en"
        );
        RequiredContextPlanner.Plan priorPlan = new RequiredContextPlanner.Plan(false, false, false, Set.of());

        SemanticNeedPlanner.Decision decision = planner.refine(
                "Is there anything weird about what I'm carrying?", prior, priorPlan, settings, 0.30D, 0.02D
        );
        RequiredContextPlanner.Plan merged = planner.mergePlan(priorPlan, decision, settings);

        assertTrue(decision.semanticallyRefined());
        assertEquals(AssistantIntent.LIVE_STATE, decision.intent());
        assertTrue(decision.needs().contains(SemanticNeedPlanner.Need.INVENTORY));
        assertTrue(merged.liveSources().contains(RequiredContextPlanner.LiveSource.INVENTORY));
        assertTrue(merged.liveSources().contains(RequiredContextPlanner.LiveSource.REQUESTER));
    }

    @Test
    void deterministicSafetyRouteCannotBeOverriddenBySemanticSimilarity() {
        SemanticNeedPlanner planner = new SemanticNeedPlanner(new FakeEmbeddings());
        AssistantIntentClassifier.Analysis prior = new AssistantIntentClassifier.Analysis(
                AssistantIntent.SAFETY, AssistantMode.HANDOFF, "en"
        );

        SemanticNeedPlanner.Decision decision = planner.refine(
                "What am I carrying?", prior,
                new RequiredContextPlanner.Plan(false, false, false, Set.of()), settings(), 0.1D, 0.0D
        );

        assertFalse(decision.semanticallyRefined());
        assertEquals(AssistantIntent.SAFETY, decision.intent());
        assertEquals(AssistantMode.HANDOFF, decision.mode());
    }

    private static AssistantSettings settings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.tools.allowed", List.of("knowledge", "requester", "world", "server", "session"));
        return AssistantSettings.from(config);
    }

    private static final class FakeEmbeddings implements SemanticEmbeddingProvider {
        @Override
        public List<double[]> embed(List<String> texts) {
            List<double[]> result = new ArrayList<>();
            for (String text : texts) {
                String value = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
                if (value.contains("carrying") || value.contains("inventory") || value.contains("wearing")
                        || value.contains("holding")) {
                    result.add(new double[]{1.0D, 0.0D, 0.0D});
                } else if (value.contains("slow") || value.contains("tps") || value.contains("mspt")) {
                    result.add(new double[]{0.0D, 1.0D, 0.0D});
                } else {
                    result.add(new double[]{0.0D, 0.0D, 1.0D});
                }
            }
            return result;
        }

        @Override
        public boolean available() {
            return true;
        }
    }
}
