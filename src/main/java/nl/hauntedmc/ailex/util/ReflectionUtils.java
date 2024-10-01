package nl.hauntedmc.ailex.util;

import nl.hauntedmc.ailex.ai.action.ActionContext;
import nl.hauntedmc.ailex.ai.action.Actionable;
import nl.hauntedmc.ailex.ai.movement.behaviour.MovementBehaviour;
import nl.hauntedmc.ailex.npc.NPC;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import nl.hauntedmc.ailex.npc.NPCData;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for reflection operations
 */
public class ReflectionUtils {

    /**
     * Map of all registered movement behaviours
     * Key: friendly name of the behaviour
     * Value: class of the behaviour
     */
    private static final Map<String, Class<? extends MovementBehaviour>> behaviourMap = new HashMap<>();
    private static final Map<String, Class<? extends Actionable>> actionMap = new HashMap<>();
    private static final Map<String, Class<? extends NPC>> npcTypeMap = new HashMap<>();

    /*
     * Static initializer blocks to scan for all subclasses and implementations register them
     * This block is executed when the class is loaded by the JVM
     * This is done to ensure that all behaviours are registered before they are used
     * This is a form of lazy initialization. This block is executed only once
     */
    static {
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages("nl.hauntedmc.ailex.ai.movement.behaviour")
                .scan()) {
            scanResult.getClassesImplementing(MovementBehaviour.class.getName())
                    .loadClasses(MovementBehaviour.class)
                    .forEach(cls -> {
                        try {
                            MovementBehaviour behaviourInstance = cls.getDeclaredConstructor().newInstance();
                            registerBehaviour(behaviourInstance);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    static {
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages("nl.hauntedmc.ailex.ai.action")
                .scan()) {
            scanResult.getClassesImplementing(Actionable.class.getName())
                    .loadClasses(Actionable.class)
                    .forEach(cls -> {
                        try {
                            if (!Modifier.isAbstract(cls.getModifiers())) {
                                Actionable actionInstance = cls.getDeclaredConstructor(ActionContext.class).newInstance(new ActionContext.Builder().build());
                                registerAction(actionInstance);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    static {
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages("nl.hauntedmc.ailex.npc.impl", "nl.hauntedmc.ailex.npc")
                .scan()) {
            scanResult.getSubclasses(NPC.class.getName())
                    .loadClasses(NPC.class)
                    .forEach(cls -> {
                        try {
                            NPC npcInstance = cls.getDeclaredConstructor(NPCData.class).newInstance(new NPCData(0,null, null, cls.getName()));
                            registerNPCType(npcInstance);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    /**
     * Registers a movement behaviour in the map
     * @param behaviour the behaviour to register
     */
    private static void registerBehaviour(MovementBehaviour behaviour) {
        behaviourMap.put(behaviour.getFriendlyName(), behaviour.getClass());
    }

    /**
     * Gets the map of all registered movement behaviours
     * @return the map of all registered movement behaviours
     */
    public static Map<String, Class<? extends MovementBehaviour>> getBehaviourMap() {
        return behaviourMap;
    }

    /**
     * Registers a movement behaviour in the map
     * @param actionable the behaviour to register
     */
    private static void registerAction(Actionable actionable) {
        actionMap.put(actionable.getFriendlyName(), actionable.getClass());
    }

    /**
     * Gets the map of all registered movement behaviours
     * @return the map of all registered movement behaviours
     */
    public static Map<String, Class<? extends Actionable>> getActionMap() {
        return actionMap;
    }

    /**
     * Registers a NPC type in the map
     * @param npc the NPC to register
     */
    private static void registerNPCType(NPC npc) {
        npcTypeMap.put(npc.getFriendlyName(), npc.getClass());
    }

    /**
     * Gets the map of all registered NPC types
     * @return the map of all registered NPC types
     */
    public static Map<String, Class<? extends NPC>> getNPCTypeMap() {
        return npcTypeMap;
    }
}
