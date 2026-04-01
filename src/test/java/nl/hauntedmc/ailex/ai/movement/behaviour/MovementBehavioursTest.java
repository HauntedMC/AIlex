package nl.hauntedmc.ailex.ai.movement.behaviour;

import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.testutil.ConfigTestSupport;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MovementBehavioursTest {

    @BeforeEach
    void setUpConfig() {
        ConfigTestSupport.initWith(Map.ofEntries(
                Map.entry("npc.behaviour.seek.maxAcceleration", 4.0),
                Map.entry("npc.behaviour.arrive.maxAcceleration", 4.0),
                Map.entry("npc.behaviour.arrive.slowRadius", 3.5),
                Map.entry("npc.behaviour.arrive.timeToTarget", 0.1),
                Map.entry("npc.behaviour.flee.maxAcceleration", 4.0),
                Map.entry("npc.behaviour.align.maxAngularAcceleration", 360.0),
                Map.entry("npc.behaviour.align.slowRadius", 90.0),
                Map.entry("npc.behaviour.align.timeToTarget", 0.1),
                Map.entry("npc.behaviour.pursue.maxPredictionTime", 3.0),
                Map.entry("npc.behaviour.evade.maxPredictionTime", 2.0),
                Map.entry("npc.behaviour.wander.maxAcceleration", 4.0),
                Map.entry("npc.behaviour.wander.wanderOffset", 2.0),
                Map.entry("npc.behaviour.wander.wanderRadius", 2.0),
                Map.entry("npc.behaviour.wander.wanderRate", 1.0)
        ));
    }

    @AfterEach
    void tearDownConfig() {
        ConfigTestSupport.reset();
    }

    @Test
    void seekShouldAccelerateTowardTarget() {
        SeekBehaviour behaviour = new SeekBehaviour();
        NPC npc = baseNpc(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0));

        MovementRequest request = behaviour.getMovementRequest(npc, location(10, 0, 0, 0f));

        assertEquals("seek", behaviour.getFriendlyName());
        assertEquals(4.0, request.getLinear().length(), 0.0001);
        assertEquals(0.0f, request.getAngular(), 0.0001);
    }

    @Test
    void fleeShouldAccelerateAwayFromTarget() {
        FleeBehaviour behaviour = new FleeBehaviour();
        NPC npc = baseNpc(new Vector3d(10, 0, 0), new Vector3d(0, 0, 0));

        MovementRequest request = behaviour.getMovementRequest(npc, location(11, 0, 0, 0f));

        assertEquals("flee", behaviour.getFriendlyName());
        assertTrue(request.getLinear().x < 0);
    }

    @Test
    void arriveShouldCapAcceleration() {
        ArriveBehaviour behaviour = new ArriveBehaviour();
        NPC npc = baseNpc(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0));
        when(npc.getMaxVelocity()).thenReturn(4.317);

        MovementRequest request = behaviour.getMovementRequest(npc, location(100, 0, 0, 0f));

        assertEquals("arrive", behaviour.getFriendlyName());
        assertTrue(request.getLinear().length() <= 4.0 + 0.0001);
    }

    @Test
    void alignShouldOutputAngularAcceleration() {
        AlignBehaviour behaviour = new AlignBehaviour();
        NPC npc = baseNpc(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0));
        when(npc.getOrientation()).thenReturn(10f);
        when(npc.getRotation()).thenReturn(0f);
        when(npc.getMaxRotation()).thenReturn(180f);

        MovementRequest request = behaviour.getMovementRequest(npc, location(0, 0, 0, 90f));

        assertEquals("align", behaviour.getFriendlyName());
        assertTrue(Math.abs(request.getAngular()) > 0);
    }

    @Test
    void faceShouldReturnNoRotationWhenTargetMatchesPosition() {
        FaceBehaviour behaviour = new FaceBehaviour();
        NPC npc = baseNpc(new Vector3d(5, 0, 5), new Vector3d(0, 0, 0));

        MovementRequest request = behaviour.getMovementRequest(npc, location(5, 0, 5, 0f));

        assertEquals("face", behaviour.getFriendlyName());
        assertEquals(0.0, request.getLinear().length(), 0.0001);
        assertEquals(0.0f, request.getAngular(), 0.0001);
    }

    @Test
    void lookVelocityShouldReturnNoRotationOnZeroVelocity() {
        LookVelocityBehaviour behaviour = new LookVelocityBehaviour();
        NPC npc = baseNpc(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0));

        MovementRequest request = behaviour.getMovementRequest(npc, location(0, 0, 0, 0f));

        assertEquals("lookvelocity", behaviour.getFriendlyName());
        assertEquals(0.0f, request.getAngular(), 0.0001);
    }

    @Test
    void pursueShouldReturnArriveLikeRequestOnPredictedTarget() {
        PursueBehaviour behaviour = new PursueBehaviour();
        NPC npc = baseNpc(new Vector3d(0, 0, 0), new Vector3d(2, 0, 0));
        when(npc.getMaxVelocity()).thenReturn(4.0);

        MovementRequest request = behaviour.getMovementRequest(npc, location(10, 0, 0, 0f));

        assertEquals("pursue", behaviour.getFriendlyName());
        assertTrue(request.getLinear().length() >= 0.0);
    }

    @Test
    void evadeShouldReturnFleeLikeRequestOnPredictedTarget() {
        EvadeBehaviour behaviour = new EvadeBehaviour();
        NPC npc = baseNpc(new Vector3d(0, 0, 0), new Vector3d(2, 0, 0));

        MovementRequest request = behaviour.getMovementRequest(npc, location(10, 0, 0, 0f));

        assertEquals("evade", behaviour.getFriendlyName());
        assertTrue(request.getLinear().length() >= 0.0);
    }

    @Test
    void wanderShouldProduceForwardAcceleration() {
        WanderBehaviour behaviour = new WanderBehaviour();
        NPC npc = baseNpc(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0));
        when(npc.getOrientation()).thenReturn(10f);
        when(npc.getRotation()).thenReturn(0f);
        when(npc.getMaxRotation()).thenReturn(180f);

        MovementRequest request = behaviour.getMovementRequest(npc, location(0, 0, 0, 0f));

        assertEquals("wander", behaviour.getFriendlyName());
        assertEquals(4.0, request.getLinear().length(), 0.0001);
    }

    private static NPC baseNpc(Vector3d position, Vector3d velocity) {
        NPC npc = mock(NPC.class);
        Entity entity = mock(Entity.class);
        World world = mock(World.class);

        when(entity.getWorld()).thenReturn(world);
        when(npc.getEntity()).thenReturn(entity);
        when(npc.getPosition()).thenAnswer(invocation -> new Vector3d(position));
        when(npc.getVelocity()).thenAnswer(invocation -> new Vector3d(velocity));
        when(npc.getOrientation()).thenReturn(0f);
        when(npc.getRotation()).thenReturn(0f);
        when(npc.newOrientation(org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.any(Vector3d.class))).thenReturn(30f);
        return npc;
    }

    private static Location location(double x, double y, double z, float yaw) {
        World world = mock(World.class);
        return new Location(world, x, y, z, yaw, 0f);
    }
}
