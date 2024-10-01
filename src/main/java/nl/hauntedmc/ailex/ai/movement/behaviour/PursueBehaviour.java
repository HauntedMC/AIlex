package nl.hauntedmc.ailex.ai.movement.behaviour;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.util.math.Geometry;

import org.bukkit.Location;
import org.joml.Vector3d;

/**
 * This class represents the pursue behaviour for the NPC.
 * The NPC estimates the future position of the target and moves towards that position.
 */
public class PursueBehaviour extends ArriveBehaviour {

    double maxPredictionTime = getMaxPredictionTime();

    /**
     * Get the movement request for the NPC to move towards the target location
     * @param npc The NPC that will move
     * @param target The target location where the NPC will move to
     * @return The movement request for the NPC to move towards the target location
     */
    @Override
    public MovementRequest getMovementRequest(NPC npc, Location target) {
        // Calculate the direction of the NPC towards the target
        Vector3d targetPosition = Geometry.locationToVector3d(target);
        Vector3d direction = new Vector3d(targetPosition).sub(npc.getPosition());

        // Ignore the y-axis, because we only want to move in 2D
        direction.y = 0;

        double distance = direction.length();
        double speed = npc.getVelocity().length();
        double predictionTime;

        //
        if (speed <= distance / maxPredictionTime) {
            predictionTime = maxPredictionTime;
        } else {
            predictionTime = distance / speed;
        }

        // Calculate the predicted target location
        Location predictedTarget = target.clone();
        Vector3d locationPrediction = npc.getVelocity().mul(predictionTime);
        predictedTarget.add(locationPrediction.x, locationPrediction.y, locationPrediction.z);

        // Call the seek behaviour with the predicted target location
        return super.getMovementRequest(npc, predictedTarget);
    }

    /**
     * Get the friendly name
     * @return The friendly name
     */
    @Override
    public String getFriendlyName() {
        return "pursue";
    }


    /**
     * Get the maximum prediction time of the NPC from the configuration
     * @return The maximum prediction time of the NPC
     */
    private double getMaxPredictionTime() {
        return ConfigHandler.getInstance().getConfig().getDouble(("npc.behaviour.pursue.maxPredictionTime"), 4.0);
    }

}
