package nl.hauntedmc.ailex.ai.action.move;

import nl.hauntedmc.ailex.ai.action.ActionContext;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.testutil.ConfigTestSupport;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoveActionsTest {

    @BeforeEach
    void setUpConfig() {
        ConfigTestSupport.initWith(Map.of(
                "npc.action.movehere.targetDistance", 0.5,
                "npc.action.followplayer.targetDistance", 2.5,
                "npc.action.fleeplayer.targetDistance", 10.0,
                "npc.action.mirrorplayer.targetAngle", 3.0
        ));
    }

    @AfterEach
    void tearDownConfig() {
        ConfigTestSupport.reset();
    }

    @Test
    void moveActionShouldExposePriorityAndContext() {
        ActionContext context = new ActionContext.Builder().setPriority(9).build();
        TestMoveAction action = new TestMoveAction(context);

        assertEquals(9, action.getPriority());
        assertEquals(context, action.getActionContext());
    }

    @Test
    void cancelShouldStopTaskAndCompleteFutureExceptionally() {
        TestMoveAction action = new TestMoveAction(new ActionContext.Builder().setPriority(1).build());
        BukkitRunnable runnable = mock(BukkitRunnable.class);
        action.movementTask = runnable;
        action.future = new CompletableFuture<>();

        action.cancel();

        verify(runnable).cancel();
        assertTrue(action.future.isCompletedExceptionally());
    }

    @Test
    void moveHereActionShouldStopWhenCloseToTarget() {
        World world = mock(World.class);
        Location target = new Location(world, 0.2, 0, 0.2);
        Entity entity = mock(Entity.class);
        when(entity.getWorld()).thenReturn(world);
        when(entity.isOnGround()).thenReturn(true);

        NPC npc = mock(NPC.class);
        when(npc.getPosition()).thenReturn(new Vector3d(0, 0, 0));
        when(npc.getEntity()).thenReturn(entity);

        MoveHereAction action = new MoveHereAction(new ActionContext.Builder().setTargetLocation(target).build());

        assertTrue(action.checkStoppingConditions(npc));
        assertTrue(action.checkWorldConditions(npc));
        assertTrue(action.checkEntityConditions(npc));
        assertEquals("movehere", action.getFriendlyName());
    }

    @Test
    void followPlayerActionShouldNotStopWhenFarAway() {
        World world = mock(World.class);
        Entity followed = mock(Entity.class);
        when(followed.getLocation()).thenReturn(new Location(world, 100, 0, 100));

        Entity npcEntity = mock(Entity.class);
        when(npcEntity.getWorld()).thenReturn(world);
        when(npcEntity.isOnGround()).thenReturn(true);

        NPC npc = mock(NPC.class);
        when(npc.getPosition()).thenReturn(new Vector3d(0, 0, 0));
        when(npc.getEntity()).thenReturn(npcEntity);

        FollowPlayerAction action = new FollowPlayerAction(new ActionContext.Builder().setTargetEntity(followed).build());

        assertFalse(action.checkStoppingConditions(npc));
        assertTrue(action.checkWorldConditions(npc));
        assertTrue(action.checkEntityConditions(npc));
        assertEquals("followplayer", action.getFriendlyName());
    }

    @Test
    void fleePlayerActionShouldStopWhenFarEnough() {
        World world = mock(World.class);
        Entity targetEntity = mock(Entity.class);
        when(targetEntity.getLocation()).thenReturn(new Location(world, 100, 0, 100));

        Entity npcEntity = mock(Entity.class);
        when(npcEntity.getWorld()).thenReturn(world);
        when(npcEntity.isOnGround()).thenReturn(true);

        NPC npc = mock(NPC.class);
        when(npc.getPosition()).thenReturn(new Vector3d(0, 0, 0));
        when(npc.getEntity()).thenReturn(npcEntity);

        FleePlayerAction action = new FleePlayerAction(new ActionContext.Builder().setTargetEntity(targetEntity).build());

        assertTrue(action.checkStoppingConditions(npc));
        assertTrue(action.checkWorldConditions(npc));
        assertTrue(action.checkEntityConditions(npc));
        assertEquals("fleeplayer", action.getFriendlyName());
    }

    @Test
    void mirrorPlayerActionShouldStopWhenAngleIsAligned() {
        World world = mock(World.class);
        Location target = new Location(world, 0, 0, 0, 12f, 0f);
        Entity targetEntity = mock(Entity.class);
        when(targetEntity.getLocation()).thenReturn(target);

        Entity npcEntity = mock(Entity.class);
        when(npcEntity.getWorld()).thenReturn(world);
        when(npcEntity.isOnGround()).thenReturn(true);

        NPC npc = mock(NPC.class);
        when(npc.getOrientation()).thenReturn(10f);
        when(npc.getEntity()).thenReturn(npcEntity);

        MirrorPlayerAction action = new MirrorPlayerAction(new ActionContext.Builder().setTargetEntity(targetEntity).build());

        assertTrue(action.checkStoppingConditions(npc));
        assertTrue(action.checkWorldConditions(npc));
        assertTrue(action.checkEntityConditions(npc));
        assertEquals("mirrorplayer", action.getFriendlyName());
    }

    @Test
    void wanderActionShouldExposeExpectedDefaults() {
        World world = mock(World.class);
        Entity npcEntity = mock(Entity.class);
        when(npcEntity.isOnGround()).thenReturn(true);
        when(npcEntity.getWorld()).thenReturn(world);

        NPC npc = mock(NPC.class);
        when(npc.getEntity()).thenReturn(npcEntity);

        WanderAction action = new WanderAction(new ActionContext.Builder().build());

        assertEquals("wander", action.getFriendlyName());
        assertFalse(action.checkStoppingConditions(npc));
        assertTrue(action.checkWorldConditions(npc));
        assertTrue(action.checkEntityConditions(npc));
    }

    @Test
    void moveHereProcessWorldConditionsShouldCancelAndCompleteFuture() {
        MoveHereAction action = new MoveHereAction(new ActionContext.Builder().setTargetLocation(new Location(mock(World.class), 0, 0, 0)).build());
        action.future = new CompletableFuture<>();
        action.movementTask = mock(BukkitRunnable.class);

        action.processWorldConditions(mock(NPC.class));

        verify(action.movementTask).cancel();
        assertTrue(action.future.isDone());
        assertNull(action.future.getNow(null));
    }

    @Test
    void mirrorPlayerProcessStoppingShouldStopRotation() {
        MirrorPlayerAction action = new MirrorPlayerAction(new ActionContext.Builder().setTargetEntity(mock(Entity.class)).build());
        NPC npc = mock(NPC.class);

        action.processStoppingConditions(npc);

        verify(npc).setRotation(0);
    }

    private static final class TestMoveAction extends MoveAction {

        private TestMoveAction(ActionContext actionContext) {
            super(actionContext);
        }

        @Override
        public String getFriendlyName() {
            return "test";
        }

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
            return true;
        }

        @Override
        public void processStoppingConditions(NPC npc) {
        }

        @Override
        public void processWorldConditions(NPC npc) {
        }

        @Override
        public void processEntityConditions(NPC npc) {
        }
    }
}
