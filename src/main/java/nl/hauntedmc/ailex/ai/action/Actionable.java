package nl.hauntedmc.ailex.ai.action;

import nl.hauntedmc.ailex.npc.NPC;

import java.util.concurrent.CompletableFuture;

/**
 * This interface represents an action that the NPC can perform.
 * It contains methods to execute and cancel the action.
 * Each action must implement this interface.
 */
public interface Actionable {
    void cancel();
    CompletableFuture<Void> execute(NPC npc);
    int getPriority();
    String getFriendlyName();
    ActionContext getActionContext();
}
