package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.LocalKnowledgeIndex;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalKnowledgeIndexTest {

    @TempDir
    Path tempDir;

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
    void shouldExpandKnownConceptsAcrossDutchAndEnglish() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.knowledge.enabled", true);
        config.set("openai.knowledge.prompt", "Official facts\n"
                + "- ECONOMY: Check your balance with /balance.\n"
                + "- VOTING: Daily voting gives rewards.");
        when(plugin.getConfig()).thenReturn(config);

        List<LocalKnowledgeIndex.KnowledgeChunk> results = new LocalKnowledgeIndex(plugin).search(
                "Waar zie ik mijn saldo?", AssistantSettings.defaults()
        );

        assertFalse(results.isEmpty());
        assertTrue(results.getFirst().text().contains("/balance"));
    }

    @Test
    void shouldSuppressNearDuplicateEvidenceChunks() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.knowledge.enabled", true);
        config.set("openai.knowledge.prompt", "- CLAIMS: Use /claim to protect your build.\n"
                + "- CLAIMS: Use /claim to protect your build.\n"
                + "- CLAIMS: Use /claim to protect your build.");
        when(plugin.getConfig()).thenReturn(config);

        List<LocalKnowledgeIndex.KnowledgeChunk> results = new LocalKnowledgeIndex(plugin).search(
                "hoe protect ik mijn claim", AssistantSettings.defaults()
        );

        assertEquals(1, results.size());
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

    @Test
    void canonicalNegativeLookupShouldFlowThroughTheProductionKnowledgeIndex() throws Exception {
        Path knowledge = tempDir.resolve("knowledge");
        Files.createDirectories(knowledge);
        Files.writeString(knowledge.resolve("entities.tsv"), """
                @complete\tdiscord-channel
                discord-channel\t#announcements\tannouncements,aankondigingen\tOfficial announcements.
                """, StandardCharsets.UTF_8);

        JavaPlugin plugin = externalKnowledgePlugin();
        LocalKnowledgeIndex index = new LocalKnowledgeIndex(plugin);
        List<LocalKnowledgeIndex.KnowledgeChunk> results = index.search(
                "Bestaat #aankondigingen?", AssistantSettings.defaults()
        );

        assertTrue(results.stream().anyMatch(chunk -> chunk.id().startsWith("entity.missing.discord-channel.")));
        assertTrue(results.stream().anyMatch(chunk -> chunk.id().equals("entity.discord-channel.announcements")));
        assertTrue(results.stream().anyMatch(chunk -> chunk.text().contains("`#aankondigingen` is not registered")));
        assertTrue(results.stream().anyMatch(chunk -> chunk.text().contains("`#announcements`")));
    }

    @Test
    void operatorReadmeMustNeverBecomePlayerFacingRagEvidence() throws Exception {
        Path knowledge = tempDir.resolve("knowledge");
        Files.createDirectories(knowledge);
        Files.writeString(
                knowledge.resolve("README.md"),
                "INTERNAL_EXAMPLE_TOKEN means players receive 999 secret rewards.",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                knowledge.resolve("server.md"),
                "---\nid: server.real\ntitle: Real fact\nauthority: official\n---\nUse /help for server help.",
                StandardCharsets.UTF_8
        );

        JavaPlugin plugin = externalKnowledgePlugin();
        LocalKnowledgeIndex index = new LocalKnowledgeIndex(plugin);

        assertTrueEmpty(index.search("INTERNAL_EXAMPLE_TOKEN", AssistantSettings.defaults()));
        assertFalse(index.search("server help", AssistantSettings.defaults()).isEmpty());
    }

    private JavaPlugin externalKnowledgePlugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.knowledge.enabled", true);
        config.set("openai.knowledge.external.enabled", true);
        config.set("openai.knowledge.external.directory", "knowledge");
        config.set("openai.assistant.retrieval.semantic_embeddings.enabled", false);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        return plugin;
    }

    private void assertTrueEmpty(List<?> values) {
        assertEquals(0, values.size());
    }
}
