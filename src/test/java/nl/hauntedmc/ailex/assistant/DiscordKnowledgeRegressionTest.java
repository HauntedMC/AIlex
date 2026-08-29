package nl.hauntedmc.ailex.assistant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordKnowledgeRegressionTest {

    @Test
    void bundledDiscordKnowledgeMustExposeCanonicalChannelNames() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/knowledge/discord.md")) {
            assertNotNull(stream);
            String knowledge = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(knowledge.contains("`#announcements`"));
            assertTrue(knowledge.contains("`#changelogs`"));
            assertTrue(knowledge.contains("`#support`"));
            assertTrue(knowledge.contains("`#aankondigingen` is **not** a verified channel"));
            assertTrue(knowledge.contains("never translate"));
        }
    }

    @Test
    void bundledManifestMustInstallDiscordKnowledge() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/knowledge/index.txt")) {
            assertNotNull(stream);
            String manifest = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(manifest.lines().anyMatch("discord.md"::equals));
        }
    }
}
