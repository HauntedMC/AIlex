package nl.hauntedmc.ailex.npc.impl;

import io.papermc.paper.entity.TeleportFlag;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCData;
import nl.hauntedmc.ailex.util.SkinUtils;
import nl.hauntedmc.ailex.util.math.Geometry;

import org.bukkit.event.player.PlayerTeleportEvent;

import org.joml.Vector3d;

/**
 * This class represents the AIlex NPC in the game.
 * It extends the NPC class and implements the methods to update the kinematics of the NPC.
 */
public class AilexNPC extends NPC {

    /**
     * Constructor for the AIlex NPC class
     * @param npcData The data of the NPC
     */
    public AilexNPC(NPCData npcData) {
        super(npcData);
        setSkin(SkinUtils.AIlex_textureValue, SkinUtils.AIlex_signatureValue);
    }

    /**
     * Update the kinematics of the NPC
     * This method updates the position, orientation, velocity and rotation of the NPC
     * @param movementRequest The movement request for this NPC
     * @param time The time for this update step
     */
    @Override
    public void updateKinematics(MovementRequest movementRequest, float time){
        // Update the position of the NPC after teleporting
        position = Geometry.locationToVector3d(fakePlayer.getLocation());

        // Update the target position and orientation for this update step
        position.add(getVelocity().mul(time));
        orientation += getRotation() * time;

        // Limit the orientation to the range [-180, 180]
        // To match the output of entity.getYaw()
        orientation = Geometry.clipAngle(orientation);

        // Teleport the fake player
        fakePlayer.teleportAsync(Geometry.vector3dToLocation(fakePlayer.getWorld(), getPosition(), getOrientation()), PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.Relative.X, TeleportFlag.Relative.Y, TeleportFlag.Relative.Z);

        // Update the velocity and rotation of the NPC
        velocity.add(movementRequest.getLinear().mul(time));
        rotation += movementRequest.getAngular() * time;

        // Limit the velocity
        if (velocity.length() > getMaxVelocity()) {
            velocity.normalize();
            velocity.mul(getMaxVelocity());
        }

        // Limit the rotation
        if (Math.abs(rotation) > getMaxRotation()) {
            rotation = Math.signum(rotation) * getMaxRotation();
        }

    }

    /**
     * Get the friendly name of the AIlex NPC
     * @return The friendly name of the AIlex NPC
     */
    @Override
    public String getFriendlyName() {
        return "ailex_npc";
    }

    /**
     * Calculate the new orientation of the NPC
     * Naive implementation: The orientation is calculated based on the velocity of the NPC
     *
     * @param currentOrientation The current orientation of the NPC
     * @param velocity The velocity of the NPC
     * @return The new orientation of the NPC
     */
    @Override
    public float newOrientation(float currentOrientation, Vector3d velocity) {
        if (velocity.lengthSquared() > 0) {
            // Note: All internal minecraft angles are in degrees
            return (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        } else {
            return currentOrientation;
        }
    }
}
