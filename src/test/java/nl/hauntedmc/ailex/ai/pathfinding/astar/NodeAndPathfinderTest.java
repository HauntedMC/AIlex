package nl.hauntedmc.ailex.ai.pathfinding.astar;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeAndPathfinderTest {

    @Test
    void shouldOrderNodesByFCost() {
        Location lowCostLocation = mock(Location.class);
        Location highCostLocation = mock(Location.class);

        Node low = new Node(lowCostLocation);
        low.setG(1);
        low.setH(1);

        Node high = new Node(highCostLocation);
        high.setG(5);
        high.setH(5);

        assertEquals(-1, low.compareTo(high));
    }

    @Test
    void shouldReturnTrivialPathWhenStartIsGoal() {
        Location start = mock(Location.class);
        when(start.distance(start)).thenReturn(0.0);

        Queue<Location> result = AStarPathfinder.findPath(start, start);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(start, result.peek());
    }
}
