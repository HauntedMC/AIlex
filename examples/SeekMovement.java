package nl.hauntedmc.ailex.ai.movement.legacy;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.util.math.Geometry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Objects;

public class SeekMovement {
    private final double speed = 0.3;
    private Entity entity;


    public void move(NPC npc, Location target) {
        this.entity = npc.getEntity();

        BukkitRunnable movementTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead() || Geometry.distance2D(npc.getPosition(), Geometry.locationToVector3d(target)) < 1.0) {
                    this.cancel();
                    return;
                }

                if (!entity.isOnGround()) {
                    return;
                }

                Location npcLocation = entity.getLocation();
                Vector direction = target.toVector().subtract(npcLocation.toVector());

                // Only adjust the x and z for the direction vector
                direction.setY(0).normalize().multiply(speed);
                Location nextLocation = npcLocation.clone().add(direction);

                // Check if the next location is solid
                if (nextLocation.getBlock().getType().isSolid()) {
                    Block blockAbove = nextLocation.clone().add(0, 1, 0).getBlock();
                    Block blockTwoAbove = nextLocation.clone().add(0, 2, 0).getBlock();

                    // Check if the blocks above the target are air
                    if (!blockAbove.getType().isSolid() && !blockTwoAbove.getType().isSolid()) {
                        nextLocation.add(0, 1, 0); // Jump
                    } else {
                        this.cancel();
                        return;
                    }
                } else {
                    Block blockBelow = nextLocation.clone().add(0, -1, 0).getBlock();
                    Block blockTwoBelow = nextLocation.clone().add(0, -2, 0).getBlock();

                    // Check if the block below the target is solid
                    if (!blockBelow.getType().isSolid()) {
                        if (blockTwoBelow.getType().isSolid()) {
                            nextLocation.add(0, -1, 0); // Step down
                        } else {
                            // Check if there are up to 5 air blocks below the target
                            boolean canFall = false;
                            for (int i = 3; i <= 6; i++) {
                                Block fallBlock = nextLocation.clone().add(0, -i, 0).getBlock();
                                if (fallBlock.getType().isSolid()) {
                                    canFall = true;
                                    break;
                                }
                            }
                            if (!canFall) {
                                this.cancel();
                                return;
                            }
                        }
                    }
                }
                entity.teleport(nextLocation);
            }
        };
        movementTask.runTaskTimer(AIlexPlugin.getPlugin(), 0L, 1L);
    }
}