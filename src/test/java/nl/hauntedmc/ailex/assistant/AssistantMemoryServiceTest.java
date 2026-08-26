package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryCandidate;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryRecord;
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
    void shouldPersistSemanticSharedAndPlayerMemoryInSqlite() {
        UUID playerId = UUID.randomUUID();
        File dataFolder = dataDirectory.toFile();
        AssistantMemoryService memory = memoryService(dataFolder);

        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("shared", "fact", "staff.members", "Alice and Bob are staff", "upsert"),
                "Alice and Bob are staff.",
                true
        );
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "opinion", "redstone", "Redstone is geweldig", "upsert"),
                "Ik vind Redstone geweldig.",
                false
        );

        assertTrue(memory.summary(playerId).contains("staff.members=Alice and Bob are staff"));
        assertTrue(memory.summary(playerId).contains("redstone=Redstone is geweldig"));
        memory.flush();
        assertTrue(new File(dataFolder, "assistant-memory.db").isFile());
        memory.close();

        AssistantMemoryService reloaded = memoryService(dataFolder);
        assertTrue(reloaded.summary(playerId).contains("staff.members=Alice and Bob are staff"));
        assertTrue(reloaded.summary(playerId).contains("redstone=Redstone is geweldig"));
        reloaded.close();
    }

    @Test
    void shouldRejectSensitiveInventedUnauthorizedAndNonFactualSharedMemory() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());

        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "fact", "account.password", "secret123", "upsert"),
                "Mijn wachtwoord is secret123.",
                false
        );
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "fact", "favorite_gamemode", "Minigames", "upsert"),
                "Ik hou van Redstone.",
                false
        );
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("shared", "fact", "staff.alice", "Alice is staff", "upsert"),
                "Alice is staff.",
                false
        );
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("shared", "opinion", "best_gamemode", "Survival is het leukst", "upsert"),
                "Ik vind Survival het leukst.",
                true
        );

        String summary = memory.summary(playerId);
        assertFalse(summary.contains("secret123"));
        assertFalse(summary.contains("favorite_gamemode=Minigames"));
        assertFalse(summary.contains("staff.alice"));
        assertFalse(summary.contains("best_gamemode"));
        memory.close();
    }

    @Test
    void shouldSupersedeCorrectedSemanticFact() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());

        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "fact", "favorite_gamemode", "Survival", "upsert"),
                "Mijn favoriete gamemode is Survival.",
                false
        );
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "fact", "favorite_gamemode", "Creative", "upsert"),
                "Dat klopt niet, mijn favoriete gamemode is Creative.",
                false
        );

        List<MemoryRecord> active = memory.activeSnapshot().stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER)
                .filter(record -> record.kind() == MemoryKind.FACT)
                .filter(record -> record.key().equals("favorite_gamemode"))
                .toList();
        assertEquals(1, active.size());
        assertEquals("Creative", active.getFirst().value());
        assertFalse(active.getFirst().supersedes().isBlank());
        memory.close();
    }

    @Test
    void shouldKeepOnlyOneSemanticKindForTheSamePlayerKey() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());

        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "fact", "favorite_gamemode", "Survival", "upsert"),
                "Mijn favoriete gamemode is Survival.",
                false
        );
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "preference", "favorite_gamemode", "Creative", "upsert"),
                "Mijn voorkeur is Creative; dat is mijn favoriete gamemode.",
                false
        );

        List<MemoryRecord> active = memory.activeSnapshot().stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER)
                .filter(record -> record.key().equals("favorite_gamemode"))
                .toList();
        assertEquals(1, active.size());
        assertEquals(MemoryKind.PREFERENCE, active.getFirst().kind());
        assertEquals("Creative", active.getFirst().value());
        memory.close();
    }

    @Test
    void shouldRememberGenericPreferencesOpinionsInterestsGoalsAndExplicitLanguage() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());

        memory.rememberExplicitLanguagePreference(playerId,
                "Ik spreek vanaf nu alleen nog Duits, onthoud mijn voorkeur.");
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "preference", "answer_style", "casual", "upsert"),
                "Ik heb liever casual antwoorden.",
                false
        );
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "opinion", "redstone", "Redstone is leuk", "upsert"),
                "Ik vind Redstone leuk.",
                false
        );
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "interest", "building_style", "medieval builds", "upsert"),
                "Ik ben geïnteresseerd in medieval builds.",
                false
        );
        MemoryRecord goal = memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "goal", "current_project", "een groot kasteel bouwen", "upsert"),
                "Mijn doel is een groot kasteel bouwen.",
                false
        );

        String summary = memory.summary(playerId);
        assertEquals("de", memory.preferredLanguage(playerId));
        assertTrue(summary.contains("language=de"));
        assertTrue(summary.contains("answer_style=casual"));
        assertTrue(summary.contains("redstone=Redstone is leuk"));
        assertTrue(summary.contains("building_style=medieval builds"));
        assertTrue(summary.contains("current_project=een groot kasteel bouwen"));
        assertNotNull(goal);
        assertTrue(goal.expiresAt() > System.currentTimeMillis());
        memory.close();
    }

    @Test
    void shouldForgetAnExplicitlyNamedMemoryKeyAcrossSemanticKinds() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "interest", "favorite_gamemode", "Survival", "upsert"),
                "Ik ben fan van Survival, onthoud mijn favoriete gamemode.",
                false
        );

        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "fact", "favorite_gamemode", "", "forget"),
                "Vergeet mijn favoriete gamemode.",
                false
        );

        assertFalse(memory.summary(playerId).contains("favorite_gamemode"));
        memory.close();
    }

    @Test
    void shouldRetrieveAssociatedMemoriesThroughSharedConcepts() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "interest", "building_style", "medieval castle building", "upsert"),
                "Ik ben geïnteresseerd in medieval castle building.",
                false
        );
        memory.rememberCandidate(
                playerId,
                "remymine",
                new MemoryCandidate("player", "goal", "current_project", "castle storage room", "upsert"),
                "Mijn doel is een castle storage room bouwen.",
                false
        );

        List<MemoryRecord> results = memory.search(
                playerId,
                "",
                "medieval building",
                Set.of(MemoryKind.INTEREST, MemoryKind.GOAL),
                8
        );
        assertTrue(results.stream().anyMatch(record -> record.key().equals("building_style")));
        assertTrue(results.stream().anyMatch(record -> record.key().equals("current_project")));
        memory.close();
    }

    @Test
    void shouldExposeScopedRelationshipAndEventMemory() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());

        assertNotNull(memory.rememberTrusted(
                MemoryScope.PLAYER_NPC,
                playerId.toString(),
                "haunty",
                MemoryKind.RELATIONSHIP,
                "interaction_count",
                "4",
                1.0,
                0.5,
                "runtime",
                "conversation-manager",
                0L,
                Duration.ofDays(30),
                Set.of("relationship")
        ));
        assertNotNull(memory.rememberTrusted(
                MemoryScope.EVENT,
                playerId.toString(),
                "haunty",
                MemoryKind.EVENT,
                "chatgame.win.1",
                "remymine won the Regen chatgame",
                1.0,
                0.9,
                "event-listener",
                "chatgame",
                System.currentTimeMillis(),
                Duration.ofDays(7),
                Set.of("chatgame")
        ));

        String summary = memory.summary(playerId, "haunty", "wat gebeurde met regen?");
        assertTrue(summary.contains("interaction_count=4"));
        assertTrue(summary.contains("Regen chatgame"));
        assertFalse(memory.search(playerId, "haunty", "regen", Set.of(MemoryKind.EVENT), 3).isEmpty());
        memory.close();
    }

    private AssistantMemoryService memoryService(File dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.memory.enabled", true);
        config.set("openai.assistant.memory.max_shared_facts", 1024);
        config.set("openai.assistant.memory.max_player_memories", 256);
        config.set("openai.assistant.memory.max_context_characters", 8000);
        config.set("openai.assistant.routing.allowed_languages", List.of("nl", "en", "de"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        return new AssistantMemoryService(plugin);
    }
}
