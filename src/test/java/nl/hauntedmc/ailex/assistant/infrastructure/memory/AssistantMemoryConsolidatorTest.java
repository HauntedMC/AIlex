package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantMemoryConsolidatorTest {

    @Test
    void repeatedTrustedEventsBecomeOneDeterministicEpisodeSummary() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.memory.enabled", true);
        config.set("openai.assistant.memory.consolidation.enabled", false);
        when(plugin.getConfig()).thenReturn(config);
        AssistantMemoryService memory = new AssistantMemoryService(plugin, new InMemoryMemoryRepository());
        try {
            long now = System.currentTimeMillis();
            for (int index = 0; index < 3; index++) {
                memory.rememberTrusted(
                        MemoryScope.EVENT, "player", "npc", MemoryKind.EVENT,
                        "build.castle." + index, "Worked on castle section " + index,
                        1.0D, 0.8D, "test-event", "event-" + index,
                        now - Duration.ofHours(3L - index).toMillis(), Duration.ofDays(30),
                        Set.of("project", "castle")
                );
            }

            AssistantMemoryConsolidator.ConsolidationReport report = memory.consolidateNow();

            assertEquals(1, report.episodesCreated());
            assertTrue(memory.activeSnapshot().stream()
                    .anyMatch(record -> record.kind() == MemoryKind.EPISODE
                            && record.tags().contains("consolidated")
                            && record.value().contains("castle")));
        } finally {
            memory.close();
        }
    }
}
