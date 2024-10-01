package nl.hauntedmc.ailex.ai.movement.behaviour;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.npc.NPC;

import nl.hauntedmc.ailex.util.math.Geometry;
import org.bukkit.Location;
import org.joml.Vector3d;

/**
 * This class represents the align behaviour for the NPC.
 * The NPC will align itself to the target orientation.
 */
public class AlignBehaviour implements MovementBehaviour {

    final float maxAngularAcceleration = getMaximumAngularAcceleration();
    final float slowRadius = getSlowRadius();
    final float timeToTarget = getTimeToTarget();

    /**
     * Align the NPC to the target orientation
     * @param npc The NPC that will move
     * @param target The target location where the NPC will move to
     * @return The movement request for the NPC to move towards the target location
     */
    @Override
    public MovementRequest getMovementRequest(NPC npc, Location target) {
        MovementRequest result = new MovementRequest();

        // Calculate the rotation of the NPC towards the target in range [-180, 180]
        float rotation = (target.getYaw() - npc.getOrientation());

        // Normalize the rotation to the range [-180, 180]
        rotation = Geometry.clipAngle(rotation);

        // Get the size of the rotation
        float rotationSize = Math.abs(rotation);

        // Clip the rotation to the maximum rotation speed if it is outside the slow radius
        // else scale the rotation to the maximum rotation speed
        float targetRotation;
        if (rotationSize > slowRadius) {
            targetRotation = npc.getMaxRotation();
        } else {
            targetRotation = npc.getMaxRotation() * (rotationSize / slowRadius);
        }

        // Correct the rotation direction by flipping the sign
        targetRotation *= (rotation / rotationSize);

        // Correction for the target rotation by taking into account the current rotation and time to target
        targetRotation -= npc.getRotation();
        targetRotation /= timeToTarget;

        // Calculate the angular acceleration
        float angularAcceleration = Math.abs(targetRotation);

        // Clip the acceleration to the maximum acceleration
        if (angularAcceleration > maxAngularAcceleration) {
            targetRotation /= angularAcceleration;
            targetRotation *= maxAngularAcceleration;
        }

        result.setAngular(targetRotation);
        result.setLinear(new Vector3d(0, 0, 0));

        return result;
    }

    /**
     * Get the friendly
     * @return The friendly name
     */
    @Override
    public String getFriendlyName() {
        return "align";
    }

    /**
     * Get the maximum acceleration of the NPC from the configuration
     * @return The maximum acceleration of the NPC
     */
    private float getMaximumAngularAcceleration() {
        return (float) ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.align.maxAngularAcceleration"), 20.0);
    }

    /**
     * Get the slow radius of the NPC from the configuration
     * @return The slow radius of the NPC
     */
    private float getSlowRadius() {
        return (float) ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.align.slowRadius"), 10.0);
    }


    /**
     * Get the time to target of the NPC from the configuration
     * @return The time to target of the NPC
     */
    private float getTimeToTarget() {
        return (float) ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.align.timeToTarget"), 0.1);
    }

}
