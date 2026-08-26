package nl.hauntedmc.ailex.assistant.proactive;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialConversationGraphTest {

    @Test
    void directAddressShouldCreateStrongTransientConnection() {
        SocialConversationGraph graph = new SocialConversationGraph();
        Player alex = player("Alex");
        Player sam = player("Sam");

        graph.observe(alex, "Sam, waar ben je?", List.of(alex, sam), 1_000L, 180_000L);

        assertTrue(graph.hasStrongRecentConnection(alex.getUniqueId(), 1_000L, 180_000L, 2.5D));
        assertEquals(1, graph.edgeCount());
    }

    @Test
    void connectionShouldDecayAndBePruned() {
        SocialConversationGraph graph = new SocialConversationGraph();
        Player alex = player("Alex");
        Player sam = player("Sam");
        graph.observe(alex, "@Sam kom je?", List.of(alex, sam), 1_000L, 60_000L);

        assertFalse(graph.hasStrongRecentConnection(alex.getUniqueId(), 121_001L, 60_000L, 0.1D));
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void contextualAlternationShouldAccumulateWithoutDurableSocialState() {
        SocialConversationGraph graph = new SocialConversationGraph();
        Player alex = player("Alex");
        Player sam = player("Sam");
        List<Player> online = List.of(alex, sam);

        graph.observe(alex, "ik ben bij spawn", online, 1_000L, 180_000L);
        graph.observe(sam, "ja ik kom eraan", online, 3_000L, 180_000L);
        graph.observe(alex, "waar ben je dan?", online, 5_000L, 180_000L);

        assertTrue(graph.strongestRecentConnection(alex.getUniqueId(), 5_000L, 180_000L) > 2.0D);
        assertTrue(SocialConversationGraph.looksContextualReply("waar ben je dan?"));
        assertEquals(3, graph.recentMessageCount());
    }

    @Test
    void threadModelDetectsUnaddressedFollowupWithoutSecondTracker() {
        SocialConversationGraph graph = new SocialConversationGraph();
        Player alex = player("Alex");
        Player sam = player("Sam");
        List<Player> online = List.of(alex, sam);

        graph.observe(alex, "Sam, kom naar spawn", online, 1_000L, 180_000L);
        graph.observe(sam, "ok ik kom", online, 3_000L, 180_000L);

        assertTrue(graph.isLikelyConversation(
                alex, "waar ben je dan?", online, 5_000L, 45_000L, 2
        ));
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        return player;
    }
}
