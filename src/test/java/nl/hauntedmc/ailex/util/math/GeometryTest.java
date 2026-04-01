package nl.hauntedmc.ailex.util.math;

import org.bukkit.Location;
import org.bukkit.World;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GeometryTest {

    @Test
    void shouldCalculateTwoDimensionalDistance() {
        double distance = Geometry.distance2D(new Vector3d(0, 0, 0), new Vector3d(3, 5, 4));
        assertEquals(5.0, distance, 0.0001);
    }

    @Test
    void shouldConvertLocationToVectorAndBack() {
        World world = mock(World.class);
        Location location = new Location(world, 1.5, 2.5, 3.5, 45f, 0f);

        Vector3d vector = Geometry.locationToVector3d(location);
        Location reconstructed = Geometry.vector3dToLocation(world, vector, 45f);

        assertEquals(1.5, vector.x, 0.0001);
        assertEquals(2.5, vector.y, 0.0001);
        assertEquals(3.5, vector.z, 0.0001);
        assertEquals(world, reconstructed.getWorld());
        assertEquals(45f, reconstructed.getYaw(), 0.0001);
    }

    @Test
    void shouldClipAnglesIntoMinecraftRange() {
        assertEquals(-170f, Geometry.clipAngle(190f));
        assertEquals(170f, Geometry.clipAngle(-190f));
        assertEquals(90f, Geometry.clipAngle(90f));
    }

    @Test
    void orientationVectorShouldBeUnitLengthOnXZPlane() {
        Vector3d vector = Geometry.getOrientationAsVector(45f);
        assertEquals(0.0, vector.y, 0.0001);
        assertTrue(vector.length() > 0.999);
        assertTrue(vector.length() < 1.001);
    }
}
