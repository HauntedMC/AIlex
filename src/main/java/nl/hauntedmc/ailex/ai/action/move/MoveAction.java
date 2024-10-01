package nl.hauntedmc.ailex.ai.action.move;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.ai.action.ActionContext;
import nl.hauntedmc.ailex.ai.action.Actionable;
import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;

public abstract class MoveAction implements Actionable {

    protected final ActionContext actionContext;
    protected CompletableFuture<Void> future;
    protected BukkitRunnable movementTask;

    public abstract String getFriendlyName();
    public abstract Location getTarget();
    public abstract boolean checkStoppingConditions(NPC npc);
    public abstract boolean checkWorldConditions(NPC npc);
    public abstract boolean checkEntityConditions(NPC npc);
    public abstract void processStoppingConditions(NPC npc);
    public abstract void processWorldConditions(NPC npc);
    public abstract void processEntityConditions(NPC npc);

    /**
     * Constructor for the MoveAction class
     * @param actionContext The context of the action
     */
    public MoveAction(ActionContext actionContext) {
        this.actionContext = actionContext;
    }

    /**
     * Cancel the action
     */
    @Override
    public void cancel() {
        if (movementTask != null) {
            movementTask.cancel();
        }
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new Exception("Action was cancelled"));
        }
    }

    /**
     * Execute the action
     * @param npc The NPC that will perform the action
     * @return A CompletableFuture that will be completed when the action is done
     */
    @Override
    public CompletableFuture<Void> execute(NPC npc) {
        future = new CompletableFuture<>();

        Entity entity = npc.getEntity();

        // Reset the kinematics of the NPC
        npc.resetKinematics();

        movementTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (entity != null) {
                    if (checkStoppingConditions(npc)) {
                        processStoppingConditions(npc);
                        return;
                    }

                    if (!checkWorldConditions(npc)) {
                        processWorldConditions(npc);
                        return;
                    }

                    // TODO: Find more edge cases and perform this check in the movement behaviour
                    if (!checkEntityConditions(npc)) {
                        processEntityConditions(npc);
                        return;
                    }

                    MovementRequest movementRequest = npc.getMovementBehaviour().getMovementRequest(npc, getTarget());
                    npc.updateKinematics(movementRequest, 0.1f);
                }
            }
        };
        movementTask.runTaskTimer(AIlexPlugin.getPlugin(), 0L, 2L);

        return future;
    }

    /**
     * Get the priority of the action
     * @return The priority of the action
     */
    @Override
    public int getPriority() {
        return actionContext.getPriority();
    }

    /**
     * Get the action context
     * @return The action context
     */
    @Override
    public ActionContext getActionContext() {
        return actionContext;
    }

}
