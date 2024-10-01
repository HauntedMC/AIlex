package nl.hauntedmc.ailex.ai.movement.behaviour;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.util.math.Geometry;

import org.bukkit.Location;
import org.joml.Vector3d;

/**
 * This class represents the flee behaviour for the NPC.
 * The NPC will move away from the target location until it reaches the stop radius.
 */
public class FleeBehaviour implements MovementBehaviour {

    final double maxAcceleration = getMaximumAcceleration();

    /**
     * Get the movement request for the NPC to move away from the target locations
     * @param npc The NPC that will move
     * @param target The target location where the NPC will move away from
     * @return The movement request for the NPC to move away from the target location
     */
    @Override
    public MovementRequest getMovementRequest(NPC npc, Location target) {
        MovementRequest result = new MovementRequest();

        // Calculate the direction of the NPC towards the target
        Vector3d targetPosition = Geometry.locationToVector3d(target);
        Vector3d direction = npc.getPosition().sub(targetPosition);

        // Ignore the y-axis, because we only want to move in 2D
        direction.y = 0;

        // Normalize the direction and multiply it with the max acceleration
        direction.normalize().mul(maxAcceleration);

        // Calculate the linear and angular direction for the movement request
        result.setLinear(direction);
        npc.setOrientation(npc.newOrientation(npc.getOrientation(), result.getLinear()));
        result.setAngular(0);

        return result;
    }

    /**
     * Get the friendly name
     * @return The friendly name
     */
    @Override
    public String getFriendlyName() {
        return "flee";
    }

    /**
     * Get the maximum acceleration of the NPC from the configuration
     * @return The maximum acceleration of the NPC
     */
    private double getMaximumAcceleration() {
        return ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.flee.maxAcceleration"), 4.0);
    }

}
