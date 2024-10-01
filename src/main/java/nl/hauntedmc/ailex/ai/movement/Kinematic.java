package nl.hauntedmc.ailex.ai.movement;

import nl.hauntedmc.ailex.ai.movement.behaviour.MovementBehaviour;
import org.joml.Vector3d;

/**
 * This interface represents the kinematics of the NPC.
 * It contains the position, orientation, velocity and rotation of the NPC.
 * It also contains methods to update the kinematics of the NPC.
 * Each NPC must implement this interface.
 */
public interface Kinematic {
    Vector3d getPosition();
    float getOrientation();
    Vector3d getVelocity();
    double getMaxVelocity();
    float getRotation();
    void setPosition(Vector3d position);
    void setOrientation(float orientation);
    void setVelocity(Vector3d velocity);
    void setRotation(float rotation);
    float newOrientation(float currentOrientation, Vector3d velocity);
    void updateKinematics(MovementRequest movementRequest, float time);
    void resetKinematics();

    // TODO: These might not be part of this interface, refactor if needed
    MovementBehaviour getMovementBehaviour();
    void setMovementBehaviour(MovementBehaviour movementBehaviour);
}
