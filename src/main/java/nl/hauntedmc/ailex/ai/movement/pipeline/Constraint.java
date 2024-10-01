package nl.hauntedmc.ailex.ai.movement.pipeline;

import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.ai.pathfinding.Path;

public interface Constraint {
    boolean willViolate(Path path);
    Goal suggestNewGoal(NPC npc, Path path, Goal goal);
}
