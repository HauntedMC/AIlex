package nl.hauntedmc.ailex.ai.movement.behaviour;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.util.math.Geometry;
import nl.hauntedmc.ailex.util.math.Probability;

import org.bukkit.Location;

import org.joml.Vector3d;

/**
 * This class represents the wander behaviour for the NPC.
 * The NPC will move in a random direction.
 */
public class WanderBehaviour extends FaceBehaviour {

    final double wanderOffset = getWanderOffset();
    final double wanderRadius = getWanderRadius();
    final double wanderRate = getWanderRate();
    final double maxAcceleration = getMaximumAcceleration();

    private float wanderOrientation;

    /**
     * Get the movement request for the NPC to move in a random direction
     * @param npc The NPC that will move
     * @param target The target location where the NPC will face to
     * @return The movement request for the NPC to move in a random direction
     */
    @Override
    public MovementRequest getMovementRequest(NPC npc, Location target) {

        wanderOrientation += (float) (Probability.getBinomial() * wanderRate);

        float targetOrientation = wanderOrientation + npc.getOrientation();
        targetOrientation = Geometry.clipAngle(targetOrientation);

        Vector3d targetPosition = npc.getPosition().add(Geometry.getOrientationAsVector(npc.getOrientation()).mul(wanderOffset));
        targetPosition.add(Geometry.getOrientationAsVector(targetOrientation).mul(wanderRadius));

        MovementRequest result = super.getMovementRequest(npc, Geometry.vector3dToLocation(npc.getEntity().getWorld(), targetPosition, targetOrientation));

        result.setLinear(Geometry.getOrientationAsVector(npc.getOrientation()).mul(maxAcceleration));

        return result;
    }

    /**
     * Get the friendly name
     * @return The friendly name
     */
    @Override
    public String getFriendlyName() {
        return "wander";
    }

    /**
     * Get the maximum acceleration of the NPC from the configuration
     * @return The maximum acceleration of the NPC
     */
    private double getMaximumAcceleration() {
        return ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.wander.maxAcceleration"), 4.0);
    }

    private double getWanderOffset() {
        return ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.wander.wanderOffset"), 2.0);
    }

    private double getWanderRadius() {
        return ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.wander.wanderRadius"), 2.0);
    }

    private double getWanderRate() {
        return ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.wander.wanderRate"), 1.0);
    }
}
