package nl.hauntedmc.ailex.ai.action.move;

import nl.hauntedmc.ailex.ai.action.ActionContext;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.npc.NPC;

import org.bukkit.Location;


public class MirrorPlayerAction extends MoveAction {

    private final float targetAngle = getTargetAngle();

    public MirrorPlayerAction(ActionContext actionContext) {
        super(actionContext);
    }

    //-------------------------------------------------------------------
    // Identification methods
    //-------------------------------------------------------------------

    @Override
    public String getFriendlyName() {
        return "mirrorplayer";
    }

    //-------------------------------------------------------------------
    // Check methods for conditional behaviour
    //-------------------------------------------------------------------

    @Override
    public Location getTarget() {
        return actionContext.getTargetEntity().getLocation();
    }

    @Override
    public boolean checkStoppingConditions(NPC npc) {
        return Math.abs(npc.getOrientation() - getTarget().getYaw()) < targetAngle;
    }

    @Override
    public boolean checkWorldConditions(NPC npc) {
        return npc.getEntity().getWorld().equals(getTarget().getWorld());
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
        npc.setRotation(0);
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

    private float getTargetAngle() {
        return (float) ConfigHandler.getInstance().getConfig().getDouble(("npc.action.mirrorplayer.targetAngle"), 2.0);
    }

}
