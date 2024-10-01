package nl.hauntedmc.ailex.ai.action.move;

import nl.hauntedmc.ailex.ai.action.ActionContext;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.util.math.Geometry;

import org.bukkit.Location;

public class MoveHereAction extends MoveAction {

    private final double targetDistance = getTargetDistance();

    public MoveHereAction(ActionContext actionContext) {
        super(actionContext);
    }

    //-------------------------------------------------------------------
    // Identification methods
    //-------------------------------------------------------------------

    @Override
    public String getFriendlyName() {
        return "movehere";
    }


    //-------------------------------------------------------------------
    // Check methods for conditional behaviour
    //-------------------------------------------------------------------


    @Override
    public Location getTarget() {
        return actionContext.getTargetLocation();
    }

    @Override
    public boolean checkStoppingConditions(NPC npc) {
        return Geometry.distance2D(npc.getPosition(), Geometry.locationToVector3d(getTarget())) < targetDistance;
    }

    @Override
    public boolean checkWorldConditions(NPC npc) {
        return npc.getEntity().getWorld().equals(actionContext.getTargetLocation().getWorld());
    }

    @Override
    public boolean checkEntityConditions(NPC npc) {
        return npc.getEntity().isOnGround();
    }

    //-------------------------------------------------------------------
    // Process methods for conditional behaviour
    //-------------------------------------------------------------------

    @Override
    public void processStoppingConditions(NPC npc) {
        movementTask.cancel();
        future.complete(null);
    }

    @Override
    public void processWorldConditions(NPC npc) {
        movementTask.cancel();
        future.complete(null);
    }

    @Override
    public void processEntityConditions(NPC npc) {
    }

    //-------------------------------------------------------------------
    // Config getters
    //-------------------------------------------------------------------

    private double getTargetDistance() {
        return ConfigHandler.getInstance().getConfig().getDouble(("npc.action.movehere.targetDistance"), 0.5);
    }
}
