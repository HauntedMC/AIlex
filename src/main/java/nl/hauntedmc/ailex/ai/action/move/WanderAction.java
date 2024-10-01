package nl.hauntedmc.ailex.ai.action.move;

import nl.hauntedmc.ailex.ai.action.ActionContext;
import nl.hauntedmc.ailex.npc.NPC;
import org.bukkit.Location;

public class WanderAction extends MoveAction {

    public WanderAction(ActionContext actionContext) {
        super(actionContext);
    }

    //-------------------------------------------------------------------
    // Identification methods
    //-------------------------------------------------------------------

    @Override
    public String getFriendlyName() {
        return "wander";
    }


    //-------------------------------------------------------------------
    // Check methods for conditional behaviour
    //-------------------------------------------------------------------


    @Override
    public Location getTarget() {
        return null;
    }


    @Override
    public boolean checkStoppingConditions(NPC npc) {
        return false;
    }

    @Override
    public boolean checkWorldConditions(NPC npc) {
        return true;
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
    }

    @Override
    public void processWorldConditions(NPC npc) {
    }

    @Override
    public void processEntityConditions(NPC npc) {
    }

    //-------------------------------------------------------------------
    // Config getters
    //-------------------------------------------------------------------

}
