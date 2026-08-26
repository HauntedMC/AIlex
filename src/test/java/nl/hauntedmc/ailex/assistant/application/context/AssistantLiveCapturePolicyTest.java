package nl.hauntedmc.ailex.assistant.application.context;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantLiveCapturePolicyTest {

    @Test
    void groundedLiveAgentFreezesAuthorizedSupersetButDirectPlanStaysSmall() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.tools.allowed", List.of(
                "requester", "world", "nearby", "server", "npc", "session", "knowledge"
        ));
        AssistantSettings settings = AssistantSettings.from(config);
        RequiredContextPlanner.Plan plan = new RequiredContextPlanner.Plan(
                false, false, false, Set.of(RequiredContextPlanner.LiveSource.REQUESTER)
        );

        Set<RequiredContextPlanner.LiveSource> captured = AssistantLiveCapturePolicy.captureSources(
                plan, AssistantIntent.LIVE_STATE, AssistantMode.GROUNDED, settings, true
        );

        assertEquals(Set.of(RequiredContextPlanner.LiveSource.REQUESTER), plan.liveSources());
        assertTrue(captured.containsAll(Set.of(
                RequiredContextPlanner.LiveSource.REQUESTER,
                RequiredContextPlanner.LiveSource.INVENTORY,
                RequiredContextPlanner.LiveSource.WORLD,
                RequiredContextPlanner.LiveSource.TARGET,
                RequiredContextPlanner.LiveSource.NEARBY,
                RequiredContextPlanner.LiveSource.SERVER,
                RequiredContextPlanner.LiveSource.NPC
        )));
    }

    @Test
    void fastOrAgentDisabledRoutesCaptureOnlyTheDeterministicPlan() {
        AssistantSettings settings = AssistantSettings.defaults();
        RequiredContextPlanner.Plan plan = new RequiredContextPlanner.Plan(
                false, false, false, Set.of(RequiredContextPlanner.LiveSource.WORLD)
        );
        assertEquals(plan.liveSources(), AssistantLiveCapturePolicy.captureSources(
                plan, AssistantIntent.LIVE_STATE, AssistantMode.FAST, settings, true
        ));
        assertEquals(plan.liveSources(), AssistantLiveCapturePolicy.captureSources(
                plan, AssistantIntent.LIVE_STATE, AssistantMode.GROUNDED, settings, false
        ));
    }
}
