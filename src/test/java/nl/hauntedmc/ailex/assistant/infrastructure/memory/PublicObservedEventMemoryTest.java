package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.AIlexPlugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicObservedEventMemoryTest {

    @TempDir
    Path dataDirectory;

    @Test
    void publicNpcObservedChatIsRecallableByAnotherPlayerWithoutExposingPrivateMemory() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.memory.enabled", true);
        config.set("openai.assistant.memory.max_shared_facts", 1024);
        config.set("openai.assistant.memory.max_player_memories", 256);
        config.set("openai.assistant.memory.max_context_characters", 8000);
        config.set("openai.assistant.routing.allowed_languages", List.of("nl", "en"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());

        AssistantMemoryService memory = new AssistantMemoryService(plugin);
        when(plugin.getAssistantMemoryService()).thenReturn(memory);
        AssistantEventMemoryService events = new AssistantEventMemoryService(plugin, memory);
        UUID darkBlueBlaze = UUID.randomUUID();
        UUID remymine = UUID.randomUUID();

        events.recordObservedPublicChat(
                darkBlueBlaze,
                "DarkBlueBlaze",
                "42",
                "Haunty",
                "Haunty, mag Remy je in de time out hoek zetten?"
        );
        memory.rememberCandidate(
                darkBlueBlaze,
                "DarkBlueBlaze",
                new MemoryCandidate("player", "preference", "favorite_gamemode", "Skyblock", "upsert"),
                "Mijn favoriete gamemode is Skyblock",
                false
        );

        List<MemoryRecord> publicEvents = memory.search(
                remymine, "42", "time out hoek", Set.of(MemoryKind.EVENT), 8
        );
        assertTrue(publicEvents.stream().anyMatch(record ->
                record.value().contains("DarkBlueBlaze") && record.value().contains("time out hoek")
        ));

        List<MemoryRecord> leakedPrivate = memory.search(
                remymine, "42", "favorite gamemode Skyblock", Set.of(MemoryKind.PREFERENCE), 8
        );
        assertTrue(leakedPrivate.isEmpty());
        assertFalse(memory.search(
                darkBlueBlaze, "42", "favorite gamemode Skyblock", Set.of(MemoryKind.PREFERENCE), 8
        ).isEmpty());
        memory.close();
    }
}
