package nl.hauntedmc.ailex.ai.movement.pipeline;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalTest {

    @Test
    void shouldTrackSetChannels() {
        Goal goal = new Goal();
        goal.setPosition(new Vector3d(1, 2, 3));
        goal.setOrientation(90f);
        goal.setVelocity(new Vector3d(4, 5, 6));
        goal.setRotation(2.5f);

        assertTrue(goal.hasPosition());
        assertTrue(goal.hasOrientation());
        assertTrue(goal.hasVelocity());
        assertTrue(goal.hasRotation());
        assertEquals(new Vector3d(1, 2, 3), goal.getPosition());
        assertEquals(90f, goal.getOrientation());
        assertEquals(new Vector3d(4, 5, 6), goal.getVelocity());
        assertEquals(2.5f, goal.getRotation());
    }

    @Test
    void updateChannelsShouldCopyOnlyAvailableChannels() {
        Goal destination = new Goal();
        destination.setOrientation(5f);

        Goal source = new Goal();
        source.setPosition(new Vector3d(9, 8, 7));

        destination.updateChannels(source);

        assertTrue(destination.hasPosition());
        assertTrue(destination.hasOrientation());
        assertFalse(destination.hasVelocity());
        assertEquals(new Vector3d(9, 8, 7), destination.getPosition());
        assertEquals(5f, destination.getOrientation());
    }
}
