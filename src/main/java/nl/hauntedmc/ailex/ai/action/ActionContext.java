package nl.hauntedmc.ailex.ai.action;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.*;

public class ActionContext {
    private final Map<String, Object> context;

    /**
     * Constructor for the ActionContext class
     * Do not call this constructor directly, use the Builder instead.
     * @param builder The builder to create the ActionContext with
     */
    public ActionContext(Builder builder) {
        context = builder.context;
    }

    /**
     * Gets the target location of the action
     * @return The target location of the action
     */
    public Location getTargetLocation() {
        return context.get("targetLocation") instanceof Location ? (Location) context.get("targetLocation") : null;
    }

    /**
     * Gets the priority of the action
     * @return The priority of the action
     */
    public int getPriority() {
        return context.get("priority") instanceof Integer ? (int) context.get("priority") : 0;
    }

    /**
     * Gets the target entity of the action
     * @return The target entity of the action
     */
    public Entity getTargetEntity() {
        return context.get("targetEntity") instanceof Entity ? (Entity) context.get("targetEntity") : null;
    }

    /**
     * Get a string representation of the ActionContext
     * @return A string representation of the ActionContext
     */
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", ActionContext.class.getSimpleName() + "[", "]");

        for (String fieldName : context.keySet()) {
            Object field = context.get(fieldName);
            joiner.add(fieldName + "=" + field.toString());
        }

        return joiner.toString();
    }

    /**
     * Builder class for the ActionContext
     * This class is used to create an ActionContext object with the desired fields.
     */
    public static class Builder {
        private final Map<String, Object> context = new HashMap<>();

        /**
         * Set the target location of the action
         * @param targetLocation The target location of the action
         * @return The builder with the target location set
         */
        public Builder setTargetLocation(Location targetLocation) {
            context.put("targetLocation", targetLocation);
            return this;
        }

        /**
         * Set the priority of the action
         * @param priority The priority of the action
         * @return The builder with the priority set
         */
        public Builder setPriority(int priority) {
            context.put("priority", priority);
            return this;
        }

        /**
         * Set the target entity of the action
         * @param targetEntity The target entity of the action
         * @return The builder with the target entity set
         */
        public Builder setTargetEntity(Entity targetEntity) {
            context.put("targetEntity", targetEntity);
            return this;
        }

        /**
         * Build the ActionContext object
         * This method creates an ActionContext object with the desired fields.
         * @return The ActionContext object with the desired fields
         */
        public ActionContext build() {
            return new ActionContext(this);
        }
    }
}