package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryGraphRetrieverTest {

    @Test
    void explicitEntityBridgePropagatesRetrievalActivationAcrossMemories() {
        MemoryRecord project = record(
                "project", MemoryScope.PLAYER, MemoryKind.GOAL, "player", "castle", "project.castle",
                "Building a castle", Set.of("project", "castle")
        );
        MemoryRecord episode = record(
                "episode", MemoryScope.EVENT, MemoryKind.EVENT, "player", "castle", "build.roof",
                "Discussed a dark prismarine roof", Set.of("project", "castle", "roof")
        );
        MemoryRecord unrelated = record(
                "unrelated", MemoryScope.PLAYER, MemoryKind.PREFERENCE, "player", "", "food.favorite",
                "Pizza", Set.of("food")
        );

        Map<String, Double> scores = new MemoryGraphRetriever().graphScores(
                List.of(project, episode, unrelated), Map.of("project", 1.0D)
        );

        assertTrue(scores.getOrDefault("episode", 0.0D) > scores.getOrDefault("unrelated", 0.0D));
        assertTrue(scores.getOrDefault("episode", 0.0D) > 0.0D);
    }

    private static MemoryRecord record(
            String id,
            MemoryScope scope,
            MemoryKind kind,
            String subject,
            String relation,
            String key,
            String value,
            Set<String> tags
    ) {
        long now = System.currentTimeMillis();
        return new MemoryRecord(
                id, scope, subject, relation, kind, key, value, 0.95D, 0.8D,
                "test", id, now, now, now, 0L, "", tags
        );
    }
}
