package nl.hauntedmc.ailex.assistant.infrastructure.knowledge;

import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NeuralHybridRetrievalTest {

    @Test
    void semanticSignalShouldRecoverAParaphraseWithNoUsefulLexicalOverlap() {
        JavaPlugin plugin = pluginWithKnowledge("Official facts\n"
                + "- CLAIMS: Claims protect builds from griefing.\n"
                + "- VOTING: Voting gives daily rewards.");
        SemanticEmbeddingProvider embeddings = new SemanticEmbeddingProvider() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<double[]> embed(List<String> inputs) {
                List<double[]> result = new ArrayList<>();
                for (String input : inputs) {
                    if (input.contains("strangers changing my house") || input.contains("Claims protect builds")) {
                        result.add(new double[]{1.0D, 0.0D});
                    } else {
                        result.add(new double[]{0.0D, 1.0D});
                    }
                }
                return List.copyOf(result);
            }
        };

        List<LocalKnowledgeIndex.KnowledgeChunk> results = new LocalKnowledgeIndex(plugin, embeddings).search(
                "How do I stop strangers changing my house?", AssistantSettings.defaults()
        );

        assertFalse(results.isEmpty());
        assertTrue(results.getFirst().text().contains("Claims protect builds"));
    }

    @Test
    void unavailableEmbeddingProviderShouldFallBackToLexicalRetrieval() {
        JavaPlugin plugin = pluginWithKnowledge("Official facts\n- VOTING: Use /vote for voting rewards.");
        SemanticEmbeddingProvider unavailable = new SemanticEmbeddingProvider() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public List<double[]> embed(List<String> inputs) {
                throw new AssertionError("embed must not be called when unavailable");
            }
        };

        List<LocalKnowledgeIndex.KnowledgeChunk> results = new LocalKnowledgeIndex(plugin, unavailable).search(
                "How do I use /vote?", AssistantSettings.defaults()
        );

        assertFalse(results.isEmpty());
        assertTrue(results.getFirst().text().contains("/vote"));
    }

    private static JavaPlugin pluginWithKnowledge(String knowledge) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.knowledge.enabled", true);
        config.set("openai.knowledge.prompt", knowledge);
        config.set("openai.knowledge.external.enabled", false);
        when(plugin.getConfig()).thenReturn(config);
        return plugin;
    }
}
