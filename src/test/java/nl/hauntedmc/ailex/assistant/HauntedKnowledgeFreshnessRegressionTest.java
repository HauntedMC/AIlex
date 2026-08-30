package nl.hauntedmc.ailex.assistant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HauntedKnowledgeFreshnessRegressionTest {

    @Test
    void currentConnectionKnowledgeUsesSupportedClientVersionOnly() throws IOException {
        String connection = resource("/knowledge/server-connection.md");
        assertTrue(connection.contains("`play.hauntedmc.nl`"));
        assertTrue(!connection.toLowerCase().contains("bedrock"));
    }

    @Test
    void currentLimitsUseTheCurrentSpawnerMatrix() throws IOException {
        String limits = resource("/knowledge/survival-limits.md");
        assertTrue(limits.contains("| Speler | 2 |"));
        assertTrue(limits.contains("| Supreme | 25 |"));
    }

    @Test
    void accountKnowledgeMustUseTheCurrentInGameTwoFactorFlow() throws IOException {
        String accounts = resource("/knowledge/account-security.md");
        assertTrue(accounts.contains("authenticator app"));
        assertTrue(accounts.contains("`/2fa setup`"));
        assertTrue(accounts.contains("`/2fa <code>`"));
        assertFalse(accounts.contains("QR-code map"));
        assertFalse(accounts.contains("after 30 days"));
    }

    @Test
    void currentDungeonKnowledgeOnlyDescribesVerifiedAccess() throws IOException {
        String dungeons = resource("/knowledge/dungeons.md");
        assertTrue(dungeons.contains("Ancient City"));
        assertTrue(dungeons.contains("Trial Chamber"));
        assertTrue(dungeons.contains("/warp dungeons"));
    }

    @Test
    void rankKnowledgeUsesCurrentFocusedRankPagesAndCurrentCommands() throws IOException {
        String homes = resource("/knowledge/player-homes.md");
        String nightVision = resource("/knowledge/night-vision.md");
        String warps = resource("/knowledge/survival-warps.md");
        String manifest = resource("/knowledge/index.txt");

        assertTrue(homes.contains("2 homes for Speler"));
        assertTrue(homes.contains("40 for Supreme and Supreme+"));
        assertTrue(warps.contains("`/warp farm` from Elite"));
        assertTrue(nightVision.contains("`/nightvision`"));
        assertTrue(nightVision.contains("There is no `/nv` command alias."));
        assertTrue(resource("/knowledge/teleportation.md").contains("list this command for everyone"));
        assertFalse(warps.contains("/wild"));
        assertTrue(manifest.lines().anyMatch("player-homes.md"::equals));
        assertTrue(manifest.lines().anyMatch("creative-plots.md"::equals));
        assertTrue(manifest.lines().anyMatch("dungeons.md"::equals));
    }

    @Test
    void playerFacingKnowledgeMustNotReferenceImaginaryHelpRoutes() throws IOException {
        String manifest = resource("/knowledge/index.txt");
        for (String file : manifest.lines().filter(name -> name.endsWith(".md")).toList()) {
            String article = resource("/knowledge/" + file);
            assertFalse(article.contains("/help/legal/"), "Invalid help route in " + file);
            assertFalse(article.toLowerCase().contains("bedrock"), "Retired platform leaked into " + file);
        }
    }

    @Test
    void installedPublicPluginHelpRemainsPlayerFocusedAndConfigurationAware() throws IOException {
        String manifest = resource("/knowledge/index.txt");
        String voiceChat = resource("/knowledge/voice-chat.md");
        String fishing = resource("/knowledge/fishing.md");
        String particles = resource("/knowledge/player-particles.md");
        String shops = resource("/knowledge/player-shops.md");

        assertTrue(manifest.lines().anyMatch("voice-chat.md"::equals));
        assertTrue(manifest.lines().anyMatch("fishing.md"::equals));
        assertTrue(manifest.lines().anyMatch("player-particles.md"::equals));
        assertTrue(manifest.lines().anyMatch("building-mechanics.md"::equals));
        assertTrue(voiceChat.contains("`/voicechat help`"));
        assertTrue(fishing.contains("`/emf`"));
        assertTrue(particles.contains("`/pp`"));
        assertTrue(shops.contains("QuickShop-Hikari"));
        assertTrue(shops.toLowerCase().contains("live"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
