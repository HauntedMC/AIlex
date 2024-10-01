package nl.hauntedmc.ailex.ai.movement.behaviour;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.util.math.Geometry;

import org.bukkit.Location;
import org.joml.Vector3d;

/**
 * This class represents the arrive behaviour for the NPC.
 * The NPC will move towards the target location until it reaches the satisfaction radius.
 */
public class ArriveBehaviour implements MovementBehaviour {

    final double maxAcceleration = getMaximumAcceleration();
    final double slowRadius = getSlowRadius();
    final double timeToTarget = getTimeToTarget();

    /**
     * Get the movement request for the NPC to move towards the target location
     * @param npc The NPC that will move
     * @param target The target location where the NPC will move to
     * @return The movement request for the NPC to move towards the target location
     */
    @Override
    public MovementRequest getMovementRequest(NPC npc, Location target) {
        MovementRequest result = new MovementRequest();

        // Calculate the direction of the NPC towards the target
        Vector3d targetPosition = Geometry.locationToVector3d(target);
        Vector3d direction = new Vector3d(targetPosition).sub(npc.getPosition());

        // Ignore the y-axis, because we only want to move in 2D
        direction.y = 0;

        // Calculate the target speed based on the distance to the target
        double targetSpeed;
        if (direction.length() > slowRadius) {
            targetSpeed = npc.getMaxVelocity();
        } else {
            targetSpeed = npc.getMaxVelocity() * (direction.length() / slowRadius);
        }

        // Calculate the target velocity based on the direction and target speed
        Vector3d targetVelocity = new Vector3d(direction).normalize().mul(targetSpeed);

        // Calculate the linear and angular direction for the movement request
        result.setLinear(targetVelocity.sub(npc.getVelocity()).div(timeToTarget));

        // Check if acceleration is too high
        if (result.getLinear().length() > maxAcceleration) {
            result.setLinear(result.getLinear().normalize().mul(maxAcceleration));
        }

        npc.setOrientation(npc.newOrientation(npc.getOrientation(), npc.getVelocity()));
        result.setAngular(0);

        return result;
    }

    /**
     * Get the friendly name
     * @return The friendly name
     */
    @Override
    public String getFriendlyName() {
        return "arrive";
    }

    /**
     * Get the maximum acceleration of the NPC from the configuration
     * @return The maximum acceleration of the NPC
     */
    private double getMaximumAcceleration() {
        return ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.arrive.maxAcceleration"), 4.0);
    }

    /**
     * Get the slow radius of the NPC from the configuration
     * @return The slow radius of the NPC
     */
    private double getSlowRadius() {
        return ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.arrive.slowRadius"), 3.0);
    }


    /**
     * Get the time to target of the NPC from the configuration
     * @return The time to target of the NPC
     */
    private double getTimeToTarget() {
        return ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.arrive.timeToTarget"), 0.1);
    }
}
