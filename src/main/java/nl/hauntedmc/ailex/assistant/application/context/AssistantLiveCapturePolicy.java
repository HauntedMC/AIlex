package nl.hauntedmc.ailex.assistant.application.context;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import java.util.EnumSet;
import java.util.Set;

/**
 * Chooses the main-thread snapshot ceiling. Direct prompt exposure still uses the smaller deterministic/semantic plan;
 * the ceiling only lets asynchronous reasoning refine an ambiguous information need without touching Bukkit off-thread.
 */
public final class AssistantLiveCapturePolicy {

    private AssistantLiveCapturePolicy() {
    }

    public static Set<RequiredContextPlanner.LiveSource> captureSources(
            RequiredContextPlanner.Plan plan,
            AssistantIntent intent,
            AssistantMode mode,
            AssistantSettings settings,
            boolean agentEnabled
    ) {
        EnumSet<RequiredContextPlanner.LiveSource> sources = EnumSet.noneOf(RequiredContextPlanner.LiveSource.class);
        if (plan != null) {
            sources.addAll(plan.liveSources());
        }
        if (!agentEnabled || mode == AssistantMode.FAST || mode == AssistantMode.HANDOFF || settings == null) {
            return Set.copyOf(sources);
        }
        if (settings.toolAllowed("requester")) {
            sources.add(RequiredContextPlanner.LiveSource.REQUESTER);
            sources.add(RequiredContextPlanner.LiveSource.INVENTORY);
        }
        if (settings.toolAllowed("world")) {
            sources.add(RequiredContextPlanner.LiveSource.WORLD);
            sources.add(RequiredContextPlanner.LiveSource.TARGET);
        }
        if (settings.toolAllowed("nearby")) {
            sources.add(RequiredContextPlanner.LiveSource.NEARBY);
        }
        if (settings.toolAllowed("server")) {
            sources.add(RequiredContextPlanner.LiveSource.SERVER);
        }
        if (settings.toolAllowed("npc")) {
            sources.add(RequiredContextPlanner.LiveSource.NPC);
        }
        return Set.copyOf(sources);
    }
}
