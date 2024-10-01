package nl.hauntedmc.ailex.ai.movement.behaviour;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.npc.NPC;

import org.bukkit.Location;

import org.joml.Vector3d;

/**
 * This class represents the look velocity behaviour for the NPC.
 * The NPC will face the direction of the velocity.
 */
public class LookVelocityBehaviour extends AlignBehaviour {

    /**
     * Get the movement request for the NPC to face the direction of the velocity
     * @param npc The NPC that will move
     * @param target The target location where the NPC will move to
     * @return The movement request for the NPC to face the direction of the velocity
     */
    @Override
    public MovementRequest getMovementRequest(NPC npc, Location target) {
        MovementRequest result = new MovementRequest();

        Location newTarget = target.clone();

        Vector3d velocity = npc.getVelocity();

        if (velocity.length() == 0) {
            return result;
        }

        newTarget.setYaw((float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z)));

        return super.getMovementRequest(npc, newTarget);
    }

    /**
     * Get the friendly name
     * @return The friendly name
     */
    @Override
    public String getFriendlyName() {
        return "lookvelocity";
    }


}
