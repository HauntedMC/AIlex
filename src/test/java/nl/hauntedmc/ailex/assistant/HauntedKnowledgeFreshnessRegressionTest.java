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
        assertTrue(limits.contains("| Speler | 2 |"));
        assertTrue(limits.contains("| Supreme | 25 |"));
        assertTrue(limits.contains("old 100-spawners/base value is superseded"));
    }

    @Test
    void accountKnowledgeMustKeepWebsiteMfaAndInGameTwoFactorSeparate() throws IOException {
        String accounts = resource("/knowledge/website-accounts-and-mfa.md");
        assertTrue(accounts.contains("## Website account/MFA"));
        assertTrue(accounts.contains("## In-game 2FA"));
        assertTrue(accounts.contains("`/2fa <code>`"));
        assertTrue(accounts.contains("not interchangeable"));
    }

    @Test
    void currentDungeonKnowledgeMustPreferRenewedDungeonState() throws IOException {
        String dungeons = resource("/knowledge/dungeons.md");
        assertTrue(dungeons.contains("Ancient City"));
        assertTrue(dungeons.contains("`/dungeons team`"));
        assertTrue(dungeons.contains("future work"));
        assertTrue(dungeons.contains("do **not** call them live"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
