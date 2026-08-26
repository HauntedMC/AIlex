package nl.hauntedmc.ailex.assistant.proactive;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProactiveChatServiceTest {

    @Test
    void generalQuestionsShouldRespectTheSharedCooldown() {
        AtomicLong now = new AtomicLong(1_000L);
        ProactiveChatService service = new ProactiveChatService(() -> settings(true, false), now::get);
        Player source = player("Tester");
        List<ProactiveChatTrigger> triggers = new ArrayList<>();
        service.onChat(source, "Waar vind ik diamonds?", () -> List.of(source), (player, trigger) -> {
            triggers.add(trigger);
            return true;
        });
        now.addAndGet(119_999L);
        service.onChat(source, "Hoe maak ik een beacon?", () -> List.of(source), (player, trigger) -> {
            triggers.add(trigger);
            return true;
        });
        now.incrementAndGet();
        service.onChat(source, "Hoe vind ik een fortress?", () -> List.of(source), (player, trigger) -> {
            triggers.add(trigger);
            return true;
        });
        assertEquals(2, triggers.size());
    }

    @Test
    void shouldNotInterruptAnAlternatingConversationBetweenTwoPlayers() {
        AtomicLong now = new AtomicLong(1_000L);
        ProactiveChatService service = new ProactiveChatService(() -> settings(true, false), now::get);
        Player alex = player("Alex");
        Player sam = player("Sam");
        List<Player> online = List.of(alex, sam);
        List<ProactiveChatTrigger> triggers = new ArrayList<>();

        service.onChat(alex, "ik ben bij de shop", () -> online, (player, trigger) -> {
            triggers.add(trigger);
            return true;
        });
        now.addAndGet(2_000L);
        service.onChat(sam, "ik kom eraan", () -> online, (player, trigger) -> {
            triggers.add(trigger);
            return true;
        });
        now.addAndGet(2_000L);
        service.onChat(alex, "waar ben je nu?", () -> online, (player, trigger) -> {
            triggers.add(trigger);
            return true;
        });

        assertEquals(0, triggers.size());
    }

    @Test
    void explicitBroadcastCanBreakOutOfAPlayerConversation() {
        AtomicLong now = new AtomicLong(1_000L);
        ProactiveChatService service = new ProactiveChatService(() -> settings(true, false), now::get);
        Player alex = player("Alex");
        Player sam = player("Sam");
        List<Player> online = List.of(alex, sam);
        List<ProactiveChatTrigger> triggers = new ArrayList<>();

        service.onChat(alex, "ik ben bij de shop", () -> online, (player, trigger) -> true);
        now.addAndGet(2_000L);
        service.onChat(sam, "ik kom eraan", () -> online, (player, trigger) -> true);
        now.addAndGet(2_000L);
        service.onChat(alex, "Weet iemand hoe ik /vote gebruik?", () -> online, (player, trigger) -> {
            triggers.add(trigger);
            return true;
        });

        assertEquals(1, triggers.size());
    }

    @Test
    void collectiveReactionsShouldRequireDistinctPlayers() {
        AtomicLong now = new AtomicLong(1_000L);
        ProactiveChatService service = new ProactiveChatService(() -> settings(false, true), now::get);
        Player alex = player("Alex");
        Player sam = player("Sam");
        List<ProactiveChatTrigger> triggers = new ArrayList<>();
        service.onChat(alex, "gg", () -> List.of(alex, sam), (player, trigger) -> {
            triggers.add(trigger);
            return true;
        });
        service.onChat(alex, "wp", () -> List.of(alex, sam), (player, trigger) -> {
            triggers.add(trigger);
            return true;
        });
        service.onChat(sam, "gg", () -> List.of(alex, sam), (player, trigger) -> {
            triggers.add(trigger);
            return true;
        });
        assertEquals(1, triggers.size());
    }

    private static ProactiveChatSettings settings(boolean questionsEnabled, boolean collectiveEnabled) {
        return new ProactiveChatSettings(
                true, "server", 120_000L,
                new ProactiveChatSettings.JoinSettings(false, 0.0D, 300_000L, "Hoi {player_name}"),
                new ProactiveChatSettings.QuestionSettings(questionsEnabled, 1.0D, 45_000L, 2),
                new ProactiveChatSettings.CollectiveSettings(
                        collectiveEnabled, List.of("gg", "wp"), 2, 45_000L, 1.0D
                ),
                new ProactiveChatSettings.IdleSettings(false, 1_800_000L, 60_000L, 0.0D, 1, 600_000L)
        );
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        return player;
    }
}
