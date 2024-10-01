package nl.hauntedmc.ailex.ai.pathfinding;

import org.joml.Vector3d;

/**
 * This interface represents a path for the NPC to follow.
 */
public interface Path {
    double getParam(Vector3d position, double lastParam);
    Vector3d getTargetPosition(double param);
    double getPathProgress(double param);
}
