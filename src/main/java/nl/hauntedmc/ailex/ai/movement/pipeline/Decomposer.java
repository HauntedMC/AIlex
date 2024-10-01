package nl.hauntedmc.ailex.ai.movement.pipeline;

import nl.hauntedmc.ailex.npc.NPC;

public interface Decomposer {
    Goal decompose(NPC npc, Goal goal);
}
