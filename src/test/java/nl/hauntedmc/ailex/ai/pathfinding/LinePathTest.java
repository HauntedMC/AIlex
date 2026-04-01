package nl.hauntedmc.ailex.ai.pathfinding;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinePathTest {

    @Test
    void shouldInterpolateTargetPositionByParameter() {
        LinePath path = new LinePath(List.of(
                new Vector3d(0, 0, 0),
                new Vector3d(10, 0, 0),
                new Vector3d(10, 0, 10)
        ));

        Vector3d firstSegmentMid = path.getTargetPosition(5.0);
        Vector3d secondSegmentMid = path.getTargetPosition(15.0);

        assertEquals(new Vector3d(5, 0, 0), firstSegmentMid);
        assertEquals(new Vector3d(10, 0, 5), secondSegmentMid);
    }

    @Test
    void shouldReturnClosestPathParameter() {
        LinePath path = new LinePath(List.of(
                new Vector3d(0, 0, 0),
                new Vector3d(10, 0, 0)
        ));

        double param = path.getParam(new Vector3d(7, 0, 3), 0);
        assertEquals(7.0, param, 0.001);
    }

    @Test
    void shouldReportPathProgressPercentage() {
        LinePath path = new LinePath(List.of(
                new Vector3d(0, 0, 0),
                new Vector3d(0, 0, 10)
        ));

        assertEquals(50.0, path.getPathProgress(5.0), 0.001);
    }
}
