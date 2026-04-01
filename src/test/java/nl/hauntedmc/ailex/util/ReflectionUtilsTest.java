package nl.hauntedmc.ailex.util;

import nl.hauntedmc.ailex.testutil.ConfigTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionUtilsTest {

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
                Map.entry("npc.behaviour.wander.wanderRate", 1.0),
                Map.entry("npc.action.movehere.targetDistance", 0.5),
                Map.entry("npc.action.followplayer.targetDistance", 2.5),
                Map.entry("npc.action.fleeplayer.targetDistance", 10.0),
                Map.entry("npc.action.mirrorplayer.targetAngle", 3.0)
        ));
    }

    @AfterEach
    void tearDownConfig() {
        ConfigTestSupport.reset();
    }

    @Test
    void shouldRegisterCoreMovementBehaviourImplementations() {
        Map<String, Class<? extends nl.hauntedmc.ailex.ai.movement.behaviour.MovementBehaviour>> behaviourMap = ReflectionUtils.getBehaviourMap();

        assertTrue(behaviourMap.containsKey("seek"));
        assertTrue(behaviourMap.containsKey("arrive"));
        assertTrue(behaviourMap.containsKey("flee"));
        assertTrue(behaviourMap.containsKey("align"));
        assertTrue(behaviourMap.containsKey("face"));
        assertTrue(behaviourMap.containsKey("lookvelocity"));
        assertTrue(behaviourMap.containsKey("pursue"));
        assertTrue(behaviourMap.containsKey("evade"));
        assertTrue(behaviourMap.containsKey("wander"));
    }

    @Test
    void shouldRegisterCoreActionImplementations() {
        Map<String, Class<? extends nl.hauntedmc.ailex.ai.action.Actionable>> actionMap = ReflectionUtils.getActionMap();

        assertTrue(actionMap.containsKey("movehere"));
        assertTrue(actionMap.containsKey("followplayer"));
        assertTrue(actionMap.containsKey("fleeplayer"));
        assertTrue(actionMap.containsKey("mirrorplayer"));
        assertTrue(actionMap.containsKey("wander"));
    }

    @Test
    void shouldRegisterAvailableNpcTypes() {
        Map<String, Class<? extends nl.hauntedmc.ailex.npc.NPC>> npcTypeMap = ReflectionUtils.getNPCTypeMap();
        assertTrue(npcTypeMap.containsKey("ailex_npc"));
    }
}
