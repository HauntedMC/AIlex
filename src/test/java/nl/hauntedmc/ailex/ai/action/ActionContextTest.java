package nl.hauntedmc.ailex.ai.action;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class ActionContextTest {

    @Test
    void builderShouldPopulateKnownFields() {
        Location location = new Location(null, 1, 2, 3);
        Entity entity = mock(Entity.class);

        ActionContext context = new ActionContext.Builder()
                .setTargetLocation(location)
                .setTargetEntity(entity)
                .setPriority(7)
                .build();

        assertEquals(location, context.getTargetLocation());
        assertEquals(entity, context.getTargetEntity());
        assertEquals(7, context.getPriority());
    }

    @Test
    void missingFieldsShouldReturnSafeDefaults() {
        ActionContext context = new ActionContext.Builder().build();

        assertNull(context.getTargetLocation());
        assertNull(context.getTargetEntity());
        assertEquals(0, context.getPriority());
    }

    @Test
    void toStringShouldIncludeAssignedFields() {
        ActionContext context = new ActionContext.Builder()
                .setPriority(3)
                .build();

        String value = context.toString();
        assertEquals(true, value.contains("priority"));
        assertEquals(true, value.contains("3"));
    }
}
