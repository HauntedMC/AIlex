package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExplicitPlayerMemoryServiceTest {

    @TempDir
    Path dataDirectory;

    @Test
    void storesProductionFavoriteBlockDeclarationImmediately() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());
        ExplicitPlayerMemoryService explicit = new ExplicitPlayerMemoryService(memory);

        ExplicitPlayerMemoryService.Result result = explicit.observe(
                playerId, "remymine", "Haunty mijn lievelings block is netherite block"
        );

        assertEquals(1, result.accepted());
        List<MemoryRecord> records = memory.search(
                playerId, "", "favorite block netherite", Set.of(MemoryKind.PREFERENCE), 8
        );
        assertTrue(records.stream().anyMatch(record ->
                record.key().equals("favorite_block") && record.value().equalsIgnoreCase("netherite block")
        ));
        memory.close();
    }

    @Test
    void storesExplicitPlayHistoryAndAppearanceFacts() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());
        ExplicitPlayerMemoryService explicit = new ExplicitPlayerMemoryService(memory);

        assertEquals(1, explicit.observe(
                playerId, "remymine", "Haunty onthou dat ik al sinds 2013 hier speel"
        ).accepted());
        assertEquals(1, explicit.observe(
                playerId, "remymine", "Haunty onthou ik heb bruin haar"
        ).accepted());

        String summary = memory.summary(playerId, "", "2013 bruin haar");
        assertTrue(summary.contains("plays_since=2013"));
        assertTrue(summary.contains("hair_color=bruin"));
        memory.close();
    }

    @Test
    void correctionSupersedesTheCurrentFavoriteAndForgetRemovesIt() {
        UUID playerId = UUID.randomUUID();
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());
        ExplicitPlayerMemoryService explicit = new ExplicitPlayerMemoryService(memory);

        assertEquals(1, explicit.observe(
                playerId, "remymine", "Haunty mijn lievelings block is netherite block"
        ).accepted());
        assertEquals(1, explicit.observe(
                playerId, "remymine", "Nee, mijn lievelings block is deepslate"
        ).accepted());

        List<MemoryRecord> current = memory.search(
                playerId, "", "favorite block", Set.of(MemoryKind.PREFERENCE), 8
        );
        assertTrue(current.stream().anyMatch(record ->
                record.key().equals("favorite_block") && record.value().equalsIgnoreCase("deepslate")
        ));
        assertFalse(current.stream().anyMatch(record ->
                record.key().equals("favorite_block") && record.value().equalsIgnoreCase("netherite block")
        ));

        ExplicitPlayerMemoryService.Result forgotten = explicit.observe(
                playerId, "remymine", "Haunty vergeet mijn favoriete block"
        );
        assertTrue(forgotten.forget());
        assertTrue(memory.search(
                playerId, "", "favorite block", Set.of(MemoryKind.PREFERENCE), 8
        ).stream().noneMatch(record -> record.key().equals("favorite_block")));
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
