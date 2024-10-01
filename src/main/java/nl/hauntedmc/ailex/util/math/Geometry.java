package nl.hauntedmc.ailex.util.math;

import org.bukkit.Location;
import org.bukkit.World;

import org.joml.Vector3d;

/**
 * Utility class for geometry calculations
 * JOML is used for vector calculations
 */
public class Geometry {

    /**
     * Calculates the 2D distance between two locations
     * @param loc1 the first location
     * @param loc2 the second location
     * @return the 2D distance between the two locations
     */
    public static double distance2D(Vector3d loc1, Vector3d loc2) {
        return Math.sqrt(Math.pow(loc1.x - loc2.x, 2) + Math.pow(loc1.z - loc2.z, 2));
    }

    /**
     * Get the Vector3d representation of a location
     * @param target the location to convert
     * @return the Vector3d representation of the location
     */
    public static Vector3d locationToVector3d(Location target) {
        return new Vector3d(target.getX(), target.getY(), target.getZ());
    }

    /**
     * Converts a Vector3d and orientation to a Location
     * @param world the world of the location
     * @param position the position of the location
     * @param orientation the orientation of the location
     * @return the location
     */
    public static Location vector3dToLocation(World world, Vector3d position, float orientation) {
        return new Location(world, position.x, position.y, position.z, orientation, 0);
    }

    /**
     * Get the orientation as a vector.
     * We assume a left-handed coordinate system
     * @param orientation the orientation
     * @return the orientation as a vector
     */
    public static Vector3d getOrientationAsVector(float orientation) {
        double x = Math.sin(orientation);
        double z = Math.cos(orientation);
        return new Vector3d(x, 0, z);
    }

    /**
     * Clip the angle to the range [-180, 180]
     * @param targetOrientation the target orientation
     * @return the clipped orientation
     */
    public static float clipAngle(float targetOrientation) {
        float clippedOrientation = targetOrientation;

        if (targetOrientation > 180) {
            clippedOrientation -= 360;
        } else if (targetOrientation < -180) {
            clippedOrientation += 360;
        }

        return clippedOrientation;
    }
}
