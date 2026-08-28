package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryTopicViewTest {

    @Test
    void topicViewKeepsExactTypedEvidenceIdentityWhileCompactingContext() {
        MemoryRecord project = record("a", MemoryKind.GOAL, "project.castle", "finish castle", Set.of("project"));
        MemoryRecord interest = record("b", MemoryKind.INTEREST, "favorite_build", "medieval", Set.of("build"));

        String rendered = new MemoryTopicView().render(List.of(project, interest), 1_000);

        assertTrue(rendered.contains("topic=project"));
        assertTrue(rendered.contains("evidence_id=memory.player.a"));
        assertTrue(rendered.contains("evidence_id=memory.player.b"));
        assertTrue(rendered.contains("project.castle=finish castle"));
    }

    private static MemoryRecord record(String id, MemoryKind kind, String key, String value, Set<String> tags) {
        return new MemoryRecord(
                id, MemoryScope.PLAYER, "player", "", kind, key, value,
                0.95D, 0.85D, "player-explicit", "player", 1L, 2L, 0L, 0L, "", tags
        );
    }
}
