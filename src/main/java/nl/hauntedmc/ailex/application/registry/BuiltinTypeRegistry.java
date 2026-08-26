package nl.hauntedmc.ailex.application.registry;

import nl.hauntedmc.ailex.ai.action.Actionable;
import nl.hauntedmc.ailex.ai.action.move.FleePlayerAction;
import nl.hauntedmc.ailex.ai.action.move.FollowPlayerAction;
import nl.hauntedmc.ailex.ai.action.move.MirrorPlayerAction;
import nl.hauntedmc.ailex.ai.action.move.MoveHereAction;
import nl.hauntedmc.ailex.ai.action.move.WanderAction;
import nl.hauntedmc.ailex.ai.movement.behaviour.AlignBehaviour;
import nl.hauntedmc.ailex.ai.movement.behaviour.ArriveBehaviour;
import nl.hauntedmc.ailex.ai.movement.behaviour.EvadeBehaviour;
import nl.hauntedmc.ailex.ai.movement.behaviour.FaceBehaviour;
import nl.hauntedmc.ailex.ai.movement.behaviour.FleeBehaviour;
import nl.hauntedmc.ailex.ai.movement.behaviour.LookVelocityBehaviour;
import nl.hauntedmc.ailex.ai.movement.behaviour.MovementBehaviour;
import nl.hauntedmc.ailex.ai.movement.behaviour.PursueBehaviour;
import nl.hauntedmc.ailex.ai.movement.behaviour.SeekBehaviour;
import nl.hauntedmc.ailex.ai.movement.behaviour.WanderBehaviour;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.impl.AilexNPC;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of the built-in AIlex types used by commands and persisted NPC data.
 *
 * <p>The registry intentionally uses direct class references rather than classpath scanning.
 * Scanning plugin classes during a hot reload can discover classes from an old plugin
 * classloader, which makes otherwise identical classes fail casts at runtime.</p>
 */
public final class BuiltinTypeRegistry {

    private static final Map<String, Class<? extends MovementBehaviour>> BEHAVIOUR_MAP = new LinkedHashMap<>();
    private static final Map<String, Class<? extends Actionable>> ACTION_MAP = new LinkedHashMap<>();
    private static final Map<String, Class<? extends NPC>> NPC_TYPE_MAP = new LinkedHashMap<>();

    static {
        BEHAVIOUR_MAP.put("align", AlignBehaviour.class);
        BEHAVIOUR_MAP.put("arrive", ArriveBehaviour.class);
        BEHAVIOUR_MAP.put("evade", EvadeBehaviour.class);
        BEHAVIOUR_MAP.put("face", FaceBehaviour.class);
        BEHAVIOUR_MAP.put("flee", FleeBehaviour.class);
        BEHAVIOUR_MAP.put("lookvelocity", LookVelocityBehaviour.class);
        BEHAVIOUR_MAP.put("pursue", PursueBehaviour.class);
        BEHAVIOUR_MAP.put("seek", SeekBehaviour.class);
        BEHAVIOUR_MAP.put("wander", WanderBehaviour.class);

        ACTION_MAP.put("fleeplayer", FleePlayerAction.class);
        ACTION_MAP.put("followplayer", FollowPlayerAction.class);
        ACTION_MAP.put("mirrorplayer", MirrorPlayerAction.class);
        ACTION_MAP.put("movehere", MoveHereAction.class);
        ACTION_MAP.put("wander", WanderAction.class);

        NPC_TYPE_MAP.put("ailex_npc", AilexNPC.class);
    }

    private BuiltinTypeRegistry() {
    }

    /**
     * Gets the registered movement behaviours keyed by their command name.
     *
     * @return the movement behaviour registry
     */
    public static Map<String, Class<? extends MovementBehaviour>> getBehaviourMap() {
        return BEHAVIOUR_MAP;
    }

    /**
     * Gets the registered actions keyed by their command name.
     *
     * @return the action registry
     */
    public static Map<String, Class<? extends Actionable>> getActionMap() {
        return ACTION_MAP;
    }

    /**
     * Gets the registered NPC types keyed by their command name.
     *
     * @return the NPC type registry
     */
    public static Map<String, Class<? extends NPC>> getNPCTypeMap() {
        return NPC_TYPE_MAP;
    }
}
