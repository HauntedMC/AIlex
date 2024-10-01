package nl.hauntedmc.ailex.ai.movement.pipeline;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.ai.pathfinding.Path;

public interface Actuator {
    Path getPath(NPC npc, Goal goal);
    MovementRequest outputMovementRequest(NPC npc, Path path, Goal goal);
}
