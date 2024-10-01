package nl.hauntedmc.ailex.ai.movement.pipeline;

import org.joml.Vector3d;

public class Goal {

    private Vector3d position;
    private float orientation;
    private Vector3d velocity;
    private float rotation;

    private boolean hasPosition = false;
    private boolean hasOrientation = false;
    private boolean hasVelocity = false;
    private boolean hasRotation = false;

    public Goal() {
    }

    public void updateChannels(Goal other) {
        if (other.hasPosition()) {
            this.position = other.getPosition();
            this.hasPosition = true;
        }
        if (other.hasOrientation()) {
            this.orientation = other.getOrientation();
            this.hasOrientation = true;
        }
        if (other.hasVelocity()) {
            this.velocity = other.getVelocity();
            this.hasVelocity = true;
        }
        if (other.hasRotation()) {
            this.rotation = other.getRotation();
            this.hasRotation = true;
        }
    }

    public boolean hasPosition() {
        return hasPosition;
    }

    public boolean hasOrientation() {
        return hasOrientation;
    }

    public boolean hasVelocity() {
        return hasVelocity;
    }

    public boolean hasRotation() {
        return hasRotation;
    }

    public float getOrientation() {
        return orientation;
    }

    public void setOrientation(float orientation) {
        this.orientation = orientation;
        this.hasOrientation = true;
    }

    public Vector3d getPosition() {
        return new Vector3d(position);
    }

    public void setPosition(Vector3d position) {
        this.position = new Vector3d(position);
        this.hasPosition = true;
    }

    public Vector3d getVelocity() {
        return new Vector3d(velocity);
    }

    public void setVelocity(Vector3d velocity) {
        this.velocity = new Vector3d(velocity);
        this.hasVelocity = true;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
        this.hasRotation = true;
    }

}
