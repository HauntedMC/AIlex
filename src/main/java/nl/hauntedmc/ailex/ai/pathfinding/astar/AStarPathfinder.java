package nl.hauntedmc.ailex.ai.pathfinding.astar;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.*;

public class AStarPathfinder {
    public static Queue<Location> findPath(Location start, Location goal) {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        HashSet<Node> closedSet = new HashSet<>();

        Node startNode = new Node(start);
        startNode.setG(0);
        startNode.setH(heuristic(start, goal));

        openSet.add(startNode);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (current.getLocation().distance(goal) < 1.0) {
                return reconstructPath(current);
            }

            closedSet.add(current);

            for (Location neighbor : getNeighbors(current.getLocation())) {
                Node neighborNode = new Node(neighbor);
                if (closedSet.contains(neighborNode)) {
                    continue;
                }

                double tentativeG = current.getG() + current.getLocation().distance(neighbor);

                if (openSet.contains(neighborNode) && tentativeG >= neighborNode.getG()) {
                    continue;
                }

                neighborNode.setParent(current);
                neighborNode.setG(tentativeG);
                neighborNode.setH(heuristic(neighbor, goal));

                if (!openSet.contains(neighborNode)) {
                    openSet.add(neighborNode);
                }
            }
        }

        return null; // No path found
    }

    private static Queue<Location> reconstructPath(Node node) {
        LinkedList<Location> path = new LinkedList<>();
        while (node != null) {
            path.addFirst(node.getLocation());
            node = node.getParent();
        }
        return path;
    }

    private static double heuristic(Location a, Location b) {
        return a.distance(b);
    }

    private static List<Location> getNeighbors(Location location) {
        List<Location> neighbors = new ArrayList<>();
        Block blockBelow = location.clone().add(0, -1, 0).getBlock();

        if (!blockBelow.getType().isSolid()) {
            return neighbors;
        }

        Location[] potentialNeighbors = new Location[]{
                location.clone().add(1, 0, 0),
                location.clone().add(-1, 0, 0),
                location.clone().add(0, 0, 1),
                location.clone().add(0, 0, -1),
                location.clone().add(1, 1, 0),
                location.clone().add(-1, 1, 0),
                location.clone().add(0, 1, 1),
                location.clone().add(0, 1, -1)
        };

        for (Location loc : potentialNeighbors) {
            Block blockAtLoc = loc.getBlock();
            Block blockAboveLoc = loc.clone().add(0, 1, 0).getBlock();
            Block blockBelowLoc = loc.clone().add(0, -1, 0).getBlock();

            if (!blockAtLoc.getType().isSolid() && !blockAboveLoc.getType().isSolid() && blockBelowLoc.getType().isSolid()) {
                neighbors.add(loc);
            }
        }

        return neighbors;
    }
}