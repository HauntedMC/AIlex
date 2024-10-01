package nl.hauntedmc.ailex.ai.movement.behaviour;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.npc.NPC;

import org.bukkit.Location;

/**
 * This interface represents the movement behaviour for the NPC.
 * It contains methods to get the movement and check the stopping conditions.
 * Each movement behaviour must implement this interface.
 */
public interface MovementBehaviour {
    MovementRequest getMovementRequest(NPC npc, Location target);
    String getFriendlyName();
}
