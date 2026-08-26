package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantRelationshipMemoryServiceTest {

    @Test
    void profileCombinesObservedRelationshipAndExplicitPlayerOwnedContinuity() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.memory.enabled", true);
        config.set("openai.assistant.memory.consolidation.enabled", false);
        when(plugin.getConfig()).thenReturn(config);
        AssistantMemoryService memory = new AssistantMemoryService(plugin, new InMemoryMemoryRepository());
        try {
            UUID player = UUID.randomUUID();
            String npc = "42";
            long now = System.currentTimeMillis();
            remember(memory, MemoryScope.PLAYER_NPC, player.toString(), npc, MemoryKind.RELATIONSHIP,
                    "first_interaction_at", String.valueOf(now - 50_000L), Set.of("relationship"));
            remember(memory, MemoryScope.PLAYER_NPC, player.toString(), npc, MemoryKind.RELATIONSHIP,
                    "last_interaction_at", String.valueOf(now), Set.of("relationship"));
            remember(memory, MemoryScope.PLAYER_NPC, player.toString(), npc, MemoryKind.RELATIONSHIP,
                    "interaction_count", "27", Set.of("relationship"));
            remember(memory, MemoryScope.PLAYER, player.toString(), "", MemoryKind.INTEREST,
                    "interest.building", "medieval castles", Set.of("building"));
            remember(memory, MemoryScope.PLAYER, player.toString(), "", MemoryKind.GOAL,
                    "project.castle", "finish the west tower", Set.of("project", "castle"));
            remember(memory, MemoryScope.PLAYER, player.toString(), "", MemoryKind.PREFERENCE,
                    "response.style", "short answers", Set.of("response"));

            RelationshipProfile profile = new AssistantRelationshipMemoryService(memory).profile(player, npc);

            assertEquals(27, profile.interactionCount());
            assertEquals("regular", profile.familiarity());
            assertTrue(profile.knownInterests().stream().anyMatch(value -> value.contains("medieval castles")));
            assertTrue(profile.currentGoals().stream().anyMatch(value -> value.contains("west tower")));
            assertTrue(profile.interactionPreferences().stream().anyMatch(value -> value.contains("short answers")));
        } finally {
            memory.close();
        }
    }

    private static void remember(
            AssistantMemoryService memory,
            MemoryScope scope,
            String subject,
            String relation,
            MemoryKind kind,
            String key,
            String value,
            Set<String> tags
    ) {
        memory.rememberTrusted(
                scope, subject, relation, kind, key, value, 1.0D, 0.8D,
                "test", key, System.currentTimeMillis(), Duration.ofDays(365), tags
        );
    }
}
