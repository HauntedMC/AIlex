package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TypedMemoryReconsolidationTest {

    @TempDir
    Path dataDirectory;

    @Test
    void typedEvidenceIdReactivatesTheUnderlyingRecord() {
        AssistantMemoryService memory = memoryService(dataDirectory.toFile());
        UUID playerId = UUID.randomUUID();
        MemoryRecord record = memory.rememberTrusted(
                MemoryScope.EVENT,
                playerId.toString(),
                "42",
                MemoryKind.EVENT,
                "public.chat.test",
                "Player said hello to Haunty",
                1.0D,
                0.40D,
                "runtime-observation",
                "public-chat",
                System.currentTimeMillis(),
                Duration.ofDays(1),
                Set.of("event", "public-chat")
        );
        assertNotNull(record);

        memory.reconsolidateVerifiedEvidence(Set.of(MemoryEvidenceId.forRecord(record)));

        MemoryRecord updated = memory.activeSnapshot().stream()
                .filter(candidate -> candidate.id().equals(record.id()))
                .findFirst()
                .orElseThrow();
        assertTrue(updated.salience() > record.salience());
        assertTrue(updated.lastConfirmed() >= record.lastConfirmed());
        memory.close();
    }

    private AssistantMemoryService memoryService(File dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.memory.enabled", true);
        config.set("openai.assistant.memory.max_shared_facts", 1024);
        config.set("openai.assistant.memory.max_player_memories", 256);
        config.set("openai.assistant.memory.max_context_characters", 8000);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        return new AssistantMemoryService(plugin);
    }
}
