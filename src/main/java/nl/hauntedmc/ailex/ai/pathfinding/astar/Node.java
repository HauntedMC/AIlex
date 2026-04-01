package nl.hauntedmc.ailex.ai.pathfinding.astar;

import org.bukkit.Location;

public class Node implements Comparable<Node> {
    private final Location location;
    private Node parent;
    private double g; // Cost from start to this node
    private double h; // Heuristic cost to the goal

    public Node(Location location) {
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    public Node getParent() {
        return parent;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public double getG() {
        return g;
    }

    public void setG(double g) {
        this.g = g;
    }

    public double getH() {
        return h;
    }

    public void setH(double h) {
        this.h = h;
    }

    public double getF() {
        return g + h;
    }

    @Override
    public int compareTo(Node other) {
        return Double.compare(this.getF(), other.getF());
    }
}
