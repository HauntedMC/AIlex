package nl.hauntedmc.ailex.ai.pathfinding;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A path that consists of line segments.
 */
public class LinePath implements Path {

    private final List<Double> segmentOffsets;
    private final List<Vector3d> pathSegments;
    private final double totalPathLength;

    /**
     * Create a new line path.
     * @param pathSegments The segments of the path.
     */
    public LinePath(List<Vector3d> pathSegments) {
        this.pathSegments = pathSegments;
        this.segmentOffsets = calculateSegmentOffsets(pathSegments);
        this.totalPathLength = segmentOffsets.getLast();
    }

    /**
     * Get the parameter of the path given a position and the last parameter.
     * @param position The position to get the parameter of.
     * @param lastParam The last parameter of the path.
     * @return The parameter of the path.
     */
    @Override
    public double getParam(Vector3d position, double lastParam) {
        if (pathSegments.isEmpty()) {
            return 0.0f;
        }

        double closestDistance = Double.MAX_VALUE;
        double closestParam = lastParam;
        double closestParamDistance = Double.MAX_VALUE;

        for (int i = 0; i < pathSegments.size() - 1; i++) {
            // Get the start and end of the segment
            Vector3d start = pathSegments.get(i);
            Vector3d end = pathSegments.get(i + 1);

            // Project the position onto the segment to get the closest point
            Vector3d projectionPosition = projectOntoSegment(position, start, end);

            // Calculate the parameter of the projection point position
            double segmentLength = start.distance(end);
            double segmentStartOffset = segmentOffsets.get(i);
            double t = start.distance(projectionPosition) / segmentLength;
            double param = segmentStartOffset + t * segmentLength;

            // Calculate the distance between the position and the projection point
            double distance = position.distance(projectionPosition);

            // Calculate the distance between the parameter and the last parameter
            double paramDistance = Math.abs(param - lastParam);

            // Prioritize the segment closest to lastParam if distances are similar
            if (distance < closestDistance || (distance == closestDistance && paramDistance < closestParamDistance)) {
                closestDistance = distance;
                closestParam = param;
                closestParamDistance = paramDistance;
            }
        }

        return closestParam;
    }

    /**
     * Project a position onto a segment.
     * @param position The position to project.
     * @param start The start of the segment.
     * @param end The end of the segment.
     * @return The projected position.
     */
    private Vector3d projectOntoSegment(Vector3d position, Vector3d start, Vector3d end) {
        Vector3d segment = new Vector3d(end).sub(start);
        Vector3d pointToStart = new Vector3d(position).sub(start);
        double t = pointToStart.dot(segment) / segment.dot(segment);

        if (t <= 0.0) {
            return new Vector3d(start);
        } else if (t >= 1.0) {
            return new Vector3d(end);
        } else {
            return new Vector3d(start).lerp(end, t);
        }
    }

    /**
     * Get the target position of the path given a parameter.
     * @param param The parameter of the path.
     * @return The target position of the path.
     */
    @Override
    public Vector3d getTargetPosition(double param) {
        // If the path is empty, return the origin
        if (pathSegments.isEmpty()) {
            return new Vector3d();
        }

        // If the parameter is less than 0, return the first segment
        if (param <= 0) {
            return pathSegments.getFirst();
        }

        // If the parameter is greater than the total path length, return the last segment
        if (param >= totalPathLength) {
            return pathSegments.getLast();
        }

        // Find the segment that the parameter is in
        int segmentIndex = findSegmentIndex(param);
        Vector3d start = pathSegments.get(segmentIndex);
        Vector3d end = pathSegments.get(segmentIndex + 1);

        // Calculate the position on the segment
        double segmentLength = start.distance(end);
        double segmentStartOffset = segmentOffsets.get(segmentIndex);
        double t = (param - segmentStartOffset) / segmentLength;

        // Interpolate between the start and end of the segment
        return new Vector3d(start).lerp(end, t);
    }

    /**
     * Find the segment that the parameter is in.
     * @param param The parameter of the path.
     * @return The index of the segment.
     */
    private int findSegmentIndex(double param) {
        // Find the segment that the parameter is in using binary search
        int index = Collections.binarySearch(segmentOffsets, param);

        if (index < 0) {
            // Insertion point is returned as (-(insertion point) - 1)
            // Convert it to the index of the segment before it
            index = -index - 2;
        }
        return index;
    }

    /**
     * Calculate the offset of each segment in the path.
     * @param pathSegments The segments of the path.
     * @return The offset of each segment.
     */
    private List<Double> calculateSegmentOffsets(List<Vector3d> pathSegments) {
        List<Double> offsets = new ArrayList<>();
        double accumulatedLength = 0;
        offsets.add(accumulatedLength);
        for (int i = 0; i < pathSegments.size() - 1; i++) {
            accumulatedLength += pathSegments.get(i).distance(pathSegments.get(i + 1));
            offsets.add(accumulatedLength);
        }
        return offsets;

    }

    /**
     * Get the progress of the path.
     * @param param The parameter of the path.
     * @return The progress of the path.
     */
    public double getPathProgress(double param) {
        return (param / totalPathLength) * 100.0;
    }
}
