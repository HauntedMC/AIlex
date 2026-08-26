package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantExperienceMemoryServiceTest {

    @Test
    void verifiedFailureShouldBecomeNpcScopedProceduralEpisode() {
        AssistantMemoryService memory = mock(AssistantMemoryService.class);
        AssistantExperienceMemoryService experience = new AssistantExperienceMemoryService(memory);

        experience.recordVerifiedOutcome(
                "12", AssistantIntent.SERVER_FACT, "grounding-server_fact",
                "Retrieve more evidence or abstain.", "unverified", Set.of("knowledge.rules")
        );

        ArgumentCaptor<Set<String>> tags = ArgumentCaptor.forClass(Set.class);
        verify(memory).rememberTrusted(
                eq(MemoryScope.NPC), eq("12"), eq(""), eq(MemoryKind.EPISODE),
                eq("experience.grounding-server_fact"),
                eq("lesson=Retrieve more evidence or abstain. | outcome=unverified"),
                eq(0.98D), eq(0.92D), eq("runtime-verified-experience"), eq("unverified"),
                anyLong(), any(Duration.class), tags.capture()
        );
        assertTrue(tags.getValue().contains("experience"));
        assertTrue(tags.getValue().contains("procedural"));
        assertTrue(tags.getValue().contains("verified"));
        assertTrue(tags.getValue().contains("failure"));
        assertTrue(tags.getValue().contains("intent-server_fact"));
        assertTrue(tags.getValue().contains("knowledge.rules"));
    }

    @Test
    void recallShouldKeepOnlyNpcExperienceEpisodes() {
        AssistantMemoryService memory = mock(AssistantMemoryService.class);
        AssistantExperienceMemoryService experience = new AssistantExperienceMemoryService(memory);
        UUID player = UUID.randomUUID();
        MemoryRecord useful = record("useful", MemoryScope.NPC, Set.of("experience", "procedural"));
        MemoryRecord ordinaryNpcEpisode = record("ordinary", MemoryScope.NPC, Set.of("event"));
        MemoryRecord playerEpisode = record("player", MemoryScope.PLAYER, Set.of("experience"));
        when(memory.search(eq(player), eq("12"), eq("grounding"), eq(Set.of(MemoryKind.EPISODE)), any(Integer.class)))
                .thenReturn(List.of(useful, ordinaryNpcEpisode, playerEpisode));

        List<MemoryRecord> recalled = experience.recall(player, "12", "grounding", 4);

        assertEquals(List.of(useful), recalled);
    }

    @Test
    void incompleteExperienceIdentityShouldNotWrite() {
        AssistantMemoryService memory = mock(AssistantMemoryService.class);
        AssistantExperienceMemoryService experience = new AssistantExperienceMemoryService(memory);
        assertNull(experience.recordVerifiedOutcome(
                "", AssistantIntent.SERVER_FACT, "key", "lesson", "accepted", Set.of("evidence")
        ));
    }

    private static MemoryRecord record(String id, MemoryScope scope, Set<String> tags) {
        return new MemoryRecord(
                id, scope, "12", "", MemoryKind.EPISODE, "experience.test", "lesson=test",
                0.9D, 0.8D, "runtime-verified-experience", "accepted",
                1L, 1L, 1L, 0L, "", tags
        );
    }
}
