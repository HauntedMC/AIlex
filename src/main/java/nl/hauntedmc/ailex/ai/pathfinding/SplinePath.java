package nl.hauntedmc.ailex.ai.pathfinding;

import org.joml.Vector3d;

import java.util.List;

/**
 * A path that consists of spline segments.
 * Using Catmull-Rom splines.
 */
public class SplinePath implements Path {

    private final List<Vector3d> pathPoints;
    private final double totalPathLength;

    /**
     * Create a new spline path.
     * @param pathPoints The points of the path.
     */
    public SplinePath(List<Vector3d> pathPoints) {
        this.pathPoints = pathPoints;
        this.totalPathLength = calculateTotalLength(pathPoints);
    }

    /**
     * Get the parameter of the path given a position and the last parameter.
     * @param position The position to get the parameter of.
     * @param lastParam The last parameter of the path.
     * @return The parameter of the path.
     */
    @Override
    public double getParam(Vector3d position, double lastParam) {
        if (pathPoints.isEmpty()) {
            return 0.0;
        }

        double closestDistance = Double.MAX_VALUE;
        double closestParam = lastParam;
        double closestParamDistance = Double.MAX_VALUE;

        for (int i = 1; i < pathPoints.size() - 2; i++) {
            Vector3d p0 = pathPoints.get(i - 1);
            Vector3d p1 = pathPoints.get(i);
            Vector3d p2 = pathPoints.get(i + 1);
            Vector3d p3 = pathPoints.get(i + 2);

            for (double t = 0; t <= 1; t += 0.01) {
                Vector3d splinePoint = getCatmullRomPoint(p0, p1, p2, p3, t);
                double distance = position.distance(splinePoint);
                double param = i + t;

                double paramDistance = Math.abs(param - lastParam);

                if (distance < closestDistance || (distance == closestDistance && paramDistance < closestParamDistance)) {
                    closestDistance = distance;
                    closestParam = param;
                    closestParamDistance = paramDistance;
                }
            }
        }

        return closestParam;
    }

    /**
     * Get the target position of the path at a given parameter.
     * @param param The parameter of the path.
     * @return The target position of the path.
     */
    @Override
    public Vector3d getTargetPosition(double param) {
        if (pathPoints.isEmpty()) {
            return new Vector3d();
        }

        if (param <= 0) {
            return pathPoints.getFirst();
        }

        if (param >= pathPoints.size() - 1) {
            return pathPoints.getLast();
        }

        int segmentIndex = (int) param;
        double t = param - segmentIndex;

        Vector3d p0 = pathPoints.get(Math.max(segmentIndex - 1, 0));
        Vector3d p1 = pathPoints.get(segmentIndex);
        Vector3d p2 = pathPoints.get(Math.min(segmentIndex + 1, pathPoints.size() - 1));
        Vector3d p3 = pathPoints.get(Math.min(segmentIndex + 2, pathPoints.size() - 1));

        return getCatmullRomPoint(p0, p1, p2, p3, t);
    }

    /**
     * Get the Catmull-Rom point at a given parameter.
     * @param p0 The first point.
     * @param p1 The second point.
     * @param p2 The third point.
     * @param p3 The fourth point.
     * @param t The parameter.
     * @return The Catmull-Rom point.
     */
    private Vector3d getCatmullRomPoint(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double t) {
        // Catmull-Rom spline formula
        double t2 = t * t;
        double t3 = t2 * t;
        double a0 = -0.5 * t3 + t2 - 0.5 * t;
        double a1 = 1.5 * t3 - 2.5 * t2 + 1.0;
        double a2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t;
        double a3 = 0.5 * t3 - 0.5 * t2;

        return new Vector3d(
                a0 * p0.x + a1 * p1.x + a2 * p2.x + a3 * p3.x,
                a0 * p0.y + a1 * p1.y + a2 * p2.y + a3 * p3.y,
                a0 * p0.z + a1 * p1.z + a2 * p2.z + a3 * p3.z
        );
    }

    /**
     * Calculate the total length of the path.
     * @param points The points of the path.
     * @return The total length of the path.
     */
    private double calculateTotalLength(List<Vector3d> points) {
        double length = 0.0;
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i - 1).distance(points.get(i));
        }
        return length;
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
