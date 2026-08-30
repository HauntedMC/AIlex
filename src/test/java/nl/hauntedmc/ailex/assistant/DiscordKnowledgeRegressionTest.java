package nl.hauntedmc.ailex.assistant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordKnowledgeRegressionTest {

    @Test
    void bundledDiscordKnowledgeMustRoutePlayersToTheOfficialLiveServer() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/knowledge/community-discord.md")) {
            assertNotNull(stream);
            String knowledge = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(knowledge.contains("`/discord`"));
            assertTrue(knowledge.contains("https://www.hauntedmc.nl/discord"));
            assertTrue(knowledge.contains("live Discord server"));
        }
    }

    @Test
    void bundledManifestMustInstallDiscordKnowledge() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/knowledge/index.txt")) {
            assertNotNull(stream);
            String manifest = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(manifest.lines().anyMatch("community-discord.md"::equals));
        }
    }
}
