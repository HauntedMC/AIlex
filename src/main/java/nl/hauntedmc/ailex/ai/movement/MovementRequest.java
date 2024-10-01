package nl.hauntedmc.ailex.ai.movement;

import org.joml.Vector3d;

/**
 * This class represents the movement request for the NPC.
 * It contains the velocity and rotation for the NPC.
 */
public class MovementRequest {

    private Vector3d linear;
    private float angular;

    /**
     * Constructor for the MovementRequest class
     */
    public MovementRequest() {
        linear = new Vector3d();
        angular = 0;
    }

    /**
     * Get the velocity of the NPC
     * @return The velocity of the NPC
     */
    public Vector3d getLinear() {
        return new Vector3d(linear);
    }

    /**
     * Get the rotation of the NPC
     * @return The rotation of the NPC
     */
    public float getAngular() {
        return angular;
    }

    /**
     * Set the velocity of the NPC
     * @param linear The new velocity of the NPC
     */
    public void setLinear(Vector3d linear) {
        this.linear = new Vector3d(linear);
    }

    /**
     * Set the rotation of the NPC
     * @param angular The new rotation of the NPC
     */
    public void setAngular(float angular) {
        this.angular = angular;
    }

    /**
     * Convert the movement request to a string for debugging purposes
     * @return The string representation of the movement request
     */
    @Override
    public String toString() {
        return "MovementRequest{" +
                "Linear=" + linear +
                ", Angular=" + angular +
                '}';
    }
}
