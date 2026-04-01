package nl.hauntedmc.ailex.ai.pathfinding;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplinePathTest {

    @Test
    void shouldReturnFirstAndLastPointsForBoundaryParameters() {
        List<Vector3d> points = List.of(
                new Vector3d(0, 0, 0),
                new Vector3d(5, 0, 0),
                new Vector3d(10, 0, 5),
                new Vector3d(15, 0, 5)
        );
        SplinePath path = new SplinePath(points);

        assertEquals(points.getFirst(), path.getTargetPosition(-10));
        assertEquals(points.getLast(), path.getTargetPosition(999));
    }

    @Test
    void shouldFindNearbyParameterForPosition() {
        List<Vector3d> points = List.of(
                new Vector3d(0, 0, 0),
                new Vector3d(5, 0, 0),
                new Vector3d(10, 0, 5),
                new Vector3d(15, 0, 5)
        );
        SplinePath path = new SplinePath(points);

        double param = path.getParam(new Vector3d(10, 0, 5), 1.0);

        assertTrue(param >= 1.0);
        assertTrue(param <= 3.0);
    }

    @Test
    void shouldReportPathProgressPercentage() {
        List<Vector3d> points = List.of(
                new Vector3d(0, 0, 0),
                new Vector3d(10, 0, 0),
                new Vector3d(20, 0, 0)
        );
        SplinePath path = new SplinePath(points);

        assertEquals(50.0, path.getPathProgress(10.0), 0.001);
    }
}
