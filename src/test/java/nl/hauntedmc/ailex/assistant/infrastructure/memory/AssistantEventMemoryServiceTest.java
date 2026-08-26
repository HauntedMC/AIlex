package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.AIlexPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantEventMemoryServiceTest {

    @Test
    void customEventsShouldBeTypedAndScopedToThePlayer() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        AssistantMemoryService memory = mock(AssistantMemoryService.class);
        AssistantEventMemoryService events = new AssistantEventMemoryService(plugin, memory);
        UUID playerId = UUID.randomUUID();

        events.recordCustomEvent("chatgame.win", playerId, "42", "Player won the Regen chatgame",
                0.9D, Duration.ofDays(7), Set.of("chatgame"));

        verify(memory).rememberTrusted(
                org.mockito.ArgumentMatchers.eq(MemoryScope.EVENT),
                org.mockito.ArgumentMatchers.eq(playerId.toString()),
                org.mockito.ArgumentMatchers.eq("42"),
                org.mockito.ArgumentMatchers.eq(MemoryKind.EVENT),
                anyString(),
                org.mockito.ArgumentMatchers.eq("Player won the Regen chatgame"),
                org.mockito.ArgumentMatchers.eq(1.0D),
                org.mockito.ArgumentMatchers.eq(0.9D),
                org.mockito.ArgumentMatchers.eq("event-listener"),
                org.mockito.ArgumentMatchers.eq("chatgame.win"),
                anyLong(),
                org.mockito.ArgumentMatchers.eq(Duration.ofDays(7)),
                any()
        );
    }

    @Test
    void repeatedEventsShouldUseDistinctEpisodicKeys() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        AssistantMemoryService memory = mock(AssistantMemoryService.class);
        AssistantEventMemoryService events = new AssistantEventMemoryService(plugin, memory);
        UUID playerId = UUID.randomUUID();

        events.recordCustomEvent("world.change", playerId, "", "Player moved to survival",
                0.4D, Duration.ofHours(12), Set.of("world"));
        events.recordCustomEvent("world.change", playerId, "", "Player moved to creative",
                0.4D, Duration.ofHours(12), Set.of("world"));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(memory, times(2)).rememberTrusted(
                org.mockito.ArgumentMatchers.eq(MemoryScope.EVENT),
                org.mockito.ArgumentMatchers.eq(playerId.toString()),
                org.mockito.ArgumentMatchers.eq(""),
                org.mockito.ArgumentMatchers.eq(MemoryKind.EVENT),
                keys.capture(),
                anyString(), anyDouble(), anyDouble(), anyString(), anyString(), anyLong(), any(Duration.class), any()
        );
        assertEquals(2, keys.getAllValues().size());
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertTrue(keys.getAllValues().stream().allMatch(key -> key.startsWith("world.change.")));
    }

    @Test
    void relationshipTrackingShouldIncrementOnlyFactualInteractionCount() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        AssistantMemoryService memory = mock(AssistantMemoryService.class);
        UUID playerId = UUID.randomUUID();
        MemoryRecord old = new MemoryRecord(
                "old", MemoryScope.PLAYER_NPC, playerId.toString(), "42", MemoryKind.RELATIONSHIP,
                "interaction_count", "3", 1.0D, 0.4D, "runtime", "test", 1L, 2L, 0L, 0L, "", Set.of()
        );
        when(memory.search(playerId, "42", "interaction count", Set.of(MemoryKind.RELATIONSHIP), 8))
                .thenReturn(List.of(old));
        AssistantEventMemoryService events = new AssistantEventMemoryService(plugin, memory);

        events.recordInteraction(playerId, "42");

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(memory).rememberTrusted(
                org.mockito.ArgumentMatchers.eq(MemoryScope.PLAYER_NPC),
                org.mockito.ArgumentMatchers.eq(playerId.toString()),
                org.mockito.ArgumentMatchers.eq("42"),
                org.mockito.ArgumentMatchers.eq(MemoryKind.RELATIONSHIP),
                org.mockito.ArgumentMatchers.eq("interaction_count"),
                value.capture(),
                anyDouble(), anyDouble(), anyString(),
                org.mockito.ArgumentMatchers.eq("accepted-chat"), anyLong(), any(Duration.class), any()
        );
        assertEquals("4", value.getValue());
        assertTrue(value.getValue().matches("\\d+"));
    }
}
