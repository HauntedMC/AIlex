package nl.hauntedmc.ailex.ai.movement;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class MovementRequestTest {

    @Test
    void shouldUseDefensiveCopiesForLinearVector() {
        MovementRequest request = new MovementRequest();
        Vector3d input = new Vector3d(1.0, 2.0, 3.0);

        request.setLinear(input);
        Vector3d returned = request.getLinear();

        assertEquals(new Vector3d(1.0, 2.0, 3.0), returned);
        assertNotSame(input, returned);
    }

    @Test
    void shouldStoreAngularValue() {
        MovementRequest request = new MovementRequest();
        request.setAngular(42.5f);

        assertEquals(42.5f, request.getAngular());
    }

    @Test
    void toStringShouldContainChannelNames() {
        MovementRequest request = new MovementRequest();
        String value = request.toString();
        assertEquals(true, value.contains("Linear"));
        assertEquals(true, value.contains("Angular"));
    }
}
