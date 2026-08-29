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
    void currentOverviewMustUseLatestClientVersionAndBedrockSupport() throws IOException {
        String overview = resource("/knowledge/server-overview.md");
        assertTrue(overview.contains("**26.2**"));
        assertTrue(overview.contains("newest Bedrock versions"));
        assertFalse(overview.contains("recommended version **1.21.11**"));
    }

    @Test
    void currentLimitsMustNotRestoreSupersededHundredSpawnerRule() throws IOException {
        String limits = resource("/knowledge/dynmap-and-limits.md");
        assertTrue(limits.contains("`Speler`: 2 spawners"));
        assertTrue(limits.contains("`Supreme`: 25 spawners"));
        assertTrue(limits.contains("100 spawners per base is superseded"));
    }

    @Test
    void accountKnowledgeMustKeepWebsiteMfaAndInGameTwoFactorSeparate() throws IOException {
        String accounts = resource("/knowledge/website-accounts-and-mfa.md");
        assertTrue(accounts.contains("Website email MFA"));
        assertTrue(accounts.contains("In-game authenticator 2FA"));
        assertTrue(accounts.contains("`/2fa <code>`"));
    }

    @Test
    void currentDungeonKnowledgeMustPreferRenewedDungeonState() throws IOException {
        String dungeons = resource("/knowledge/dungeons.md");
        assertTrue(dungeons.contains("Ancient City"));
        assertTrue(dungeons.contains("`/dungeons team`"));
        assertTrue(dungeons.contains("announced future dungeons"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
