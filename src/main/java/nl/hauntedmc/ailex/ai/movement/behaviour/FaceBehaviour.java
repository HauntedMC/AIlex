package nl.hauntedmc.ailex.ai.movement.behaviour;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.npc.NPC;

import nl.hauntedmc.ailex.util.math.Geometry;
import org.bukkit.Location;
import org.joml.Vector3d;

/**
 * This class represents the face behaviour for the NPC.
 * The NPC will face the target location.
 */
public class FaceBehaviour extends AlignBehaviour {

    /**
     * Get the movement request for the NPC to face the target location
     * @param npc The NPC that will move
     * @param target The target location where the NPC will face to
     * @return The movement request for the NPC to face the target location
     */
    @Override
    public MovementRequest getMovementRequest(NPC npc, Location target) {
        MovementRequest result = new MovementRequest();

        Location newTarget = target.clone();
        Vector3d targetPosition = Geometry.locationToVector3d(newTarget);

        Vector3d direction = new Vector3d(targetPosition).sub(npc.getPosition());
        direction.y = 0;

        if (direction.length() == 0) {
            return result;
        }

        newTarget.setYaw((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));

        return super.getMovementRequest(npc, newTarget);
    }

    /**
     * Get the friendly name
     * @return The friendly name
     */
    @Override
    public String getFriendlyName() {
        return "face";
    }


}
