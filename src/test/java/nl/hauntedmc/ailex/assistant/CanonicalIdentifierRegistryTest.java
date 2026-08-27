package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.CanonicalIdentifierRegistry;
import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.LocalKnowledgeIndex;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CanonicalIdentifierRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void exactUnknownDiscordChannelProducesNegativeEvidenceWhenKindIsComplete() throws Exception {
        CanonicalIdentifierRegistry registry = registry("""
                @complete\tdiscord-channel
                discord-channel\t#announcements\tannouncements,aankondigingen\tOfficial announcements.
                """);

        List<LocalKnowledgeIndex.KnowledgeChunk> evidence = registry.evidenceFor("Bestaat #aankondigingen?");

        assertEquals(1, evidence.size());
        assertTrue(evidence.getFirst().id().startsWith("entity.missing.discord-channel"));
        assertTrue(evidence.getFirst().text().contains("`#aankondigingen` is not registered"));
    }

    @Test
    void naturalAliasResolvesToCanonicalIdentifierWithoutCreatingTranslatedIdentifier() throws Exception {
        CanonicalIdentifierRegistry registry = registry("""
                @complete\tdiscord-channel
                discord-channel\t#announcements\tannouncements,aankondigingen,aankondigingen kanaal\tOfficial announcements.
                """);

        List<LocalKnowledgeIndex.KnowledgeChunk> evidence = registry.evidenceFor("Wat is het aankondigingen kanaal?");

        assertEquals(1, evidence.size());
        assertTrue(evidence.getFirst().text().contains("`#announcements`"));
    }

    @Test
    void generatedKnowledgeCarriesCompletenessAndCanonicalNames() throws Exception {
        CanonicalIdentifierRegistry registry = registry("""
                @complete\trank
                rank\tSpeler\tplayer,speler\tBase rank.
                rank\tElite\telite\tPublic rank.
                """);

        registry.writeKnowledgeSnapshot();

        String generated = Files.readString(
                tempDir.resolve("knowledge/canonical-identifiers.generated.md"), StandardCharsets.UTF_8
        );
        assertTrue(generated.contains("rank — COMPLETE"));
        assertTrue(generated.contains("`Speler`"));
        assertTrue(generated.contains("`Elite`"));
        assertTrue(generated.contains("source: knowledge/entities.tsv"));
    }

    private CanonicalIdentifierRegistry registry(String content) throws Exception {
        Path knowledge = tempDir.resolve("knowledge");
        Files.createDirectories(knowledge);
        Files.writeString(knowledge.resolve("entities.tsv"), content, StandardCharsets.UTF_8);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        return new CanonicalIdentifierRegistry(plugin);
    }
}
