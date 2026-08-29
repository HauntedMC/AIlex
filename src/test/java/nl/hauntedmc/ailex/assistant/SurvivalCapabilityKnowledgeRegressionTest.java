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
    void playerFacingCapabilityKnowledgeMustExposeSystemsWithoutLeakingImplementationInventory() throws IOException {
        String knowledge = resource("/knowledge/survival-player-capabilities.md");

        List.of(
                "Rank-dependent backpacks",
                "crate/key reward system",
                "expanded fishing content",
                "mob-kill money rewards",
                "direct player-to-player trade subsystem",
                "multiplayer sleep/night-skip system",
                "pets, morph/disguise-style cosmetics, particle cosmetics",
                "proximity voice chat support",
                "block/action history and inventory rollback evidence"
        ).forEach(fact -> assertTrue(knowledge.contains(fact), "Missing player-facing capability: " + fact));

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
                knowledge.contains(component),
                "Player-facing knowledge leaked implementation component: " + component
        ));
    }

    @Test
    void bundledManifestMustInstallSurvivalCapabilityKnowledge() throws IOException {
        String manifest = resource("/knowledge/index.txt");
        assertTrue(manifest.lines().anyMatch("survival-player-capabilities.md"::equals));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
