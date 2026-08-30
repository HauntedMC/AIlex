package nl.hauntedmc.ailex.assistant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalCapabilityKnowledgeRegressionTest {

    @Test
    void playerFacingCapabilitiesUseFocusedCurrentSources() throws IOException {
        String autoPickup = resource("/knowledge/auto-pickup.md");
        String graves = resource("/knowledge/player-graves.md");
        String language = resource("/knowledge/player-language.md");
        String messages = resource("/knowledge/commands-messaging.md");
        String ping = resource("/knowledge/player-ping.md");

        List.of(
                "AutoPickup replaces Drop2Inventory",
                "blocks you directly break",
                "never armour, offhand"
        ).forEach(fact -> assertTrue(autoPickup.contains(fact), "Missing verified AutoPickup fact: " + fact));

        assertFalse(autoPickup.contains("free offhand can be used"));
        assertTrue(language.contains("Set it with `/language AUTO`, `/language NL`, or `/language EN`"));
        assertFalse(language.contains("/language DE"));
        assertTrue(messages.contains("`/msg block <player>`"));
        assertTrue(ping.contains("ConnectionInfo is enabled"));
        assertTrue(ping.contains("HauntedMC proxy"));

        List.of("virtual grave", "**10 minutes**", "Partial collection is safe")
                .forEach(fact -> assertTrue(graves.contains(fact), "Missing verified grave fact: " + fact));

        List.of(
                "LuckPerms",
                "Vault",
                "PacketEvents",
                "PlaceholderAPI",
                "NBTAPI",
                "Citizens",
                "WorldGuard",
                "QuickShop-Hikari",
                "InventoryRollbackPlus",
                "LogBlock",
                "Multiverse-Core",
                "DataProvider",
                "DataRegistry"
        ).forEach(component -> assertFalse(
                (autoPickup + graves).contains(component),
                "Player-facing knowledge leaked implementation component: " + component
        ));
    }

    @Test
    void bundledManifestMustInstallFocusedSurvivalKnowledge() throws IOException {
        String manifest = resource("/knowledge/index.txt");
        assertTrue(manifest.lines().anyMatch("auto-pickup.md"::equals));
        assertTrue(manifest.lines().anyMatch("player-graves.md"::equals));
        assertTrue(manifest.lines().anyMatch("custom-portals.md"::equals));
        assertFalse(manifest.lines().anyMatch("survival-deathchests.md"::equals));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
