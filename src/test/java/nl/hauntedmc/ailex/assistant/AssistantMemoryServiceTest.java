package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryScope;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantMemoryServiceTest {

    @TempDir
    Path dataDirectory;

    @Test
    void shouldPersistTypedSharedAndPlayerFactsInSqlite() {
        UUID playerId = UUID.randomUUID();
        File dataFolder = dataDirectory.toFile();
        AssistantMemoryService memory = memoryService(dataFolder);
        memory.remember(playerId, "remymine", "shared:Staff members are Alice and Bob.",
                "De staff members zijn Alice en Bob.");
        memory.remember(playerId, "remymine", "player:Ik speel graag Survival en bouw Redstone farms.",
                "Ik speel graag Survival en bouw Redstone farms.");

        assertTrue(memory.summary(playerId).contains("Staff members are Alice and Bob."));
        assertTrue(memory.summary(playerId).contains("Survival en bouw Redstone farms"));
        memory.flush();
        assertTrue(new File(dataFolder, "assistant-memory.db").isFile());
        memory.close();

        AssistantMemoryService reloaded = memoryService(dataFolder);
        assertTrue(reloaded.summary(playerId).contains("Staff members are Alice and Bob."));
        assertTrue(reloaded.summary(playerId).contains("Survival en bouw Redstone farms"));
        reloaded.close();
    }

    @Test
    void shouldRejectSensitiveInventedAndUnauthorizedFacts() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());

        memory.remember(playerId, "remymine", "player:Mijn wachtwoord is secret123.",
                "Mijn wachtwoord is secret123.");
        memory.remember(playerId, "remymine", "player:Speelt altijd Minigames.", "Ik hou van Redstone.");
        memory.remember(playerId, "remymine", "shared:Alice is staff.", "Alice is staff.", false);

        assertFalse(memory.summary(playerId).contains("wachtwoord"));
        assertFalse(memory.summary(playerId).contains("Minigames"));
        assertFalse(memory.summary(playerId).contains("Alice is staff"));
        memory.close();
    }

    @Test
    void shouldSupersedePreferencesWithoutLeavingTwoActiveValues() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());

        memory.remember(playerId, "remymine", "preference:tone=casual", "Gebruik een casual toon.");
        memory.remember(playerId, "remymine", "preference:tone=formal", "Gebruik vanaf nu een formal toon.");

        List<nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryRecord> tone = memory.activeSnapshot().stream()
                .filter(record -> record.kind() == MemoryKind.PREFERENCE && record.key().equals("tone"))
                .toList();
        assertEquals(1, tone.size());
        assertEquals("formal", tone.getFirst().value());
        assertFalse(tone.getFirst().supersedes().isBlank());
        memory.close();
    }

    @Test
    void shouldPersistExplicitLanguageAndHarmlessInterests() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());

        memory.rememberExplicitLanguagePreference(playerId,
                "Ik spreek vanaf nu alleen nog Duits, onthoud mijn voorkeur.");
        memory.remember(playerId, "remymine", "player:likes pizza", "I like pizza.");
        memory.observe(playerId, "Redstone is leuk.");
        memory.observe(playerId, "Redstone again vandaag.");
        memory.remember(playerId, "remymine", "player:redstone fan", "Redstone again vandaag.");

        assertTrue(memory.summary(playerId).contains("language=de"));
        assertEquals("de", memory.preferredLanguage(playerId));
        assertTrue(memory.summary(playerId).contains("likes pizza"));
        assertTrue(memory.summary(playerId).contains("redstone fan"));
        memory.close();
    }

    @Test
    void shouldExposeScopedRelationshipAndEventMemory() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());

        assertNotNull(memory.rememberTrusted(
                MemoryScope.PLAYER_NPC, playerId.toString(), "haunty", MemoryKind.RELATIONSHIP,
                "interaction_count", "4", 1.0, 0.5, "runtime", "conversation-manager", 0L,
                Duration.ofDays(30), Set.of("relationship")
        ));
        assertNotNull(memory.rememberTrusted(
                MemoryScope.EVENT, playerId.toString(), "haunty", MemoryKind.EVENT,
                "chatgame.win.1", "remymine won the Regen chatgame", 1.0, 0.9,
                "event-listener", "chatgame", System.currentTimeMillis(), Duration.ofDays(7), Set.of("chatgame")
        ));

        String summary = memory.summary(playerId, "haunty", "wat gebeurde met regen?");
        assertTrue(summary.contains("interaction_count=4"));
        assertTrue(summary.contains("Regen chatgame"));
        assertFalse(memory.search(playerId, "haunty", "regen", Set.of(MemoryKind.EVENT), 3).isEmpty());
        memory.close();
    }

    @Test
    void shouldMigrateLegacyYamlOnce() throws Exception {
        UUID playerId = UUID.randomUUID();
        File dataFolder = dataDirectory.toFile();
        YamlConfiguration preferences = new YamlConfiguration();
        preferences.set("players." + playerId + ".tone", "casual");
        preferences.set("players." + playerId + ".updated_at", System.currentTimeMillis());
        preferences.save(new File(dataFolder, "assistant-memory.yml"));
        YamlConfiguration longTerm = new YamlConfiguration();
        longTerm.set("shared_facts", List.of("HauntedMC has Survival."));
        longTerm.set("players." + playerId + ".name", "remymine");
        longTerm.set("players." + playerId + ".facts", List.of("likes building farms"));
        longTerm.save(new File(dataFolder, "assistant-long-term-memory.yml"));

        AssistantMemoryService memory = memoryService(dataFolder);

        assertTrue(memory.summary(playerId).contains("tone=casual"));
        assertTrue(memory.summary(playerId).contains("HauntedMC has Survival."));
        assertTrue(memory.summary(playerId).contains("likes building farms"));
        assertTrue(new File(dataFolder, ".assistant-memory-v2-migrated").isFile());
        memory.close();
    }

    private AssistantMemoryService memoryService(File dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.memory.enabled", true);
        config.set("openai.assistant.memory.retention_days", 90);
        config.set("openai.assistant.memory.max_shared_facts", 128);
        config.set("openai.assistant.memory.max_player_facts", 24);
        config.set("openai.assistant.routing.allowed_languages", List.of("nl", "en", "de"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        return new AssistantMemoryService(plugin);
    }
}
