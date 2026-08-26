package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.LocalKnowledgeIndex;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalKnowledgeIndexTest {

    @Test
    void shouldPreferExactCommandKnowledgeOverUnrelatedFacts() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.knowledge.enabled", true);
        config.set("openai.knowledge.prompt", "Official facts\n"
                + "- RANKS: Elite can use /skin.\n"
                + "- CREATIVE: Use /plot claim to claim a plot.\n"
                + "- VOTING: Use /vote once per day.");
        when(plugin.getConfig()).thenReturn(config);

        LocalKnowledgeIndex index = new LocalKnowledgeIndex(plugin);
        List<LocalKnowledgeIndex.KnowledgeChunk> results = index.search(
                "How do I use /plot claim?", AssistantSettings.defaults()
        );

        assertFalse(results.isEmpty());
        assertTrue(results.getFirst().text().contains("/plot claim"));
    }

    @Test
    void shouldIgnoreKnowledgeWhenQueryHasNoUsefulTerms() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.knowledge.enabled", true);
        config.set("openai.knowledge.prompt", "- SURVIVAL: Claims protect builds.");
        when(plugin.getConfig()).thenReturn(config);

        assertTrueEmpty(new LocalKnowledgeIndex(plugin).search("en de", AssistantSettings.defaults()));
    }

    @Test
    void shouldNotTreatGenericServerBrandingAsGameplayEvidence() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.knowledge.enabled", true);
        config.set("openai.knowledge.prompt", "HauntedMC is a Minecraft server.");
        when(plugin.getConfig()).thenReturn(config);

        List<LocalKnowledgeIndex.KnowledgeChunk> results = new LocalKnowledgeIndex(plugin).search(
                "Hoe tem ik een wolf in Minecraft, Haunty?", AssistantSettings.defaults()
        );

        assertTrueEmpty(results);
    }

    private void assertTrueEmpty(List<?> values) {
        assertEquals(0, values.size());
    }
}
