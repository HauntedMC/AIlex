package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantMemoryServiceTest {

    @TempDir
    Path dataDirectory;

    @Test
    void shouldAutomaticallyPersistSharedStaffFactsAndPlayerFacts() {
        UUID playerId = UUID.randomUUID();
        File dataFolder = dataDirectory.toFile();
        AssistantMemoryService memory = memoryService(dataFolder);
        memory.remember(playerId, "remymine", "shared:Staff members are Alice and Bob.",
                "De staff members zijn Alice en Bob.");
        memory.remember(playerId, "remymine", "player:Ik speel graag Survival en bouw Redstone farms.",
                "Ik speel graag Survival en bouw Redstone farms.");

        String summary = memory.summary(playerId);
        assertTrue(memory.isEnabled(playerId));
        assertTrue(summary.contains("Staff members are Alice and Bob."));
        assertTrue(summary.contains("Ik speel graag Survival en bouw Redstone farms."));
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(
                new File(dataFolder, "assistant-long-term-memory.yml")
        );
        assertTrue(saved.getStringList("shared_facts").contains("Staff members are Alice and Bob."));

        AssistantMemoryService reloaded = memoryService(dataFolder);
        assertTrue(reloaded.summary(playerId).contains("Staff members are Alice and Bob."));
        assertTrue(reloaded.summary(playerId).contains("Survival en bouw Redstone farms"));
    }

    @Test
    void shouldRejectSensitiveOrInventedPlayerFacts() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());

        memory.remember(playerId, "remymine", "player:Mijn wachtwoord is secret123.",
                "Mijn wachtwoord is secret123.");
        memory.remember(playerId, "remymine", "player:Speelt altijd Minigames.", "Ik hou van Redstone.");
        memory.remember(playerId, "remymine", "shared:Alice is staff.", "Ik hou van Redstone.");

        assertFalse(memory.summary(playerId).contains("wachtwoord"));
        assertFalse(memory.summary(playerId).contains("Minigames"));
        assertFalse(memory.summary(playerId).contains("Alice is staff"));
    }

    @Test
    void shouldCreatePreferencesFileWhenAnExplicitPreferenceIsSaved() {
        UUID playerId = UUID.randomUUID();
        File dataFolder = dataDirectory.toFile();
        AssistantMemoryService memory = memoryService(dataFolder);

        memory.remember(playerId, "remymine", "preference:tone=casual", "Gebruik een casual toon.");

        assertTrue(new File(dataFolder, "assistant-memory.yml").isFile());
        assertTrue(memory.summary(playerId).contains("tone=casual"));
    }

    @Test
    void shouldPersistHarmlessPersonalInterestFromAParaphrase() {
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());
        UUID playerId = UUID.randomUUID();

        memory.remember(playerId, "remymine", "player:likes pizza", "I like pizza.");

        assertTrue(memory.summary(playerId).contains("likes pizza"));
    }

    @Test
    void shouldPersistARepeatedPersonalTopic() {
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());
        UUID playerId = UUID.randomUUID();
        memory.observe(playerId, "Pizza is heerlijk.");
        memory.observe(playerId, "Pizza again vandaag.");

        memory.remember(playerId, "remymine", "player:pizza fan", "Pizza again vandaag.");

        assertTrue(memory.summary(playerId).contains("pizza fan"));
    }

    @Test
    void shouldRejectSharedFactsFromAnUnauthorizedPlayer() {
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());
        UUID playerId = UUID.randomUUID();

        memory.remember(playerId, "remymine", "shared:Alice is staff.", "Alice is staff.", false);

        assertFalse(memory.summary(playerId).contains("Alice is staff."));
    }

    private AssistantMemoryService memoryService(File dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.memory.enabled", true);
        config.set("openai.assistant.memory.retention_days", 90);
        config.set("openai.assistant.memory.max_shared_facts", 128);
        config.set("openai.assistant.memory.max_player_facts", 24);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        return new AssistantMemoryService(plugin);
    }
}
