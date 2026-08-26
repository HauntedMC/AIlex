package nl.hauntedmc.ailex.assistant.action;

import nl.hauntedmc.ailex.ai.action.move.FollowPlayerAction;
import nl.hauntedmc.ailex.npc.NPC;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantActionServiceTest {

    @Test
    void explicitFollowRequestCanQueueOnlyWhitelistedFollowAction() {
        JavaPlugin plugin = plugin();
        AssistantActionService service = new AssistantActionService(plugin);
        Player requester = mock(Player.class);
        Player npcEntity = mock(Player.class);
        World world = mock(World.class);
        NPC npc = mock(NPC.class);
        when(requester.getWorld()).thenReturn(world);
        when(npcEntity.getWorld()).thenReturn(world);
        when(npc.getEntity()).thenReturn(npcEntity);
        when(npc.isSpawned()).thenReturn(true);

        AssistantActionService.ActionResult result = service.validateAndExecute(
                requester, npc, "AIlex, volg mij even",
                List.of(new AssistantActionProposal(AssistantActionType.FOLLOW_REQUESTER, "player explicitly asked"))
        );

        assertEquals(List.of(AssistantActionType.FOLLOW_REQUESTER), result.executed());
        verify(npc).queueAction(any(FollowPlayerAction.class));
    }

    @Test
    void modelCannotCauseMovementWithoutMatchingExplicitPlayerIntent() {
        JavaPlugin plugin = plugin();
        AssistantActionService service = new AssistantActionService(plugin);
        Player requester = mock(Player.class);
        NPC npc = mock(NPC.class);
        when(npc.isSpawned()).thenReturn(true);

        AssistantActionService.ActionResult result = service.validateAndExecute(
                requester, npc, "Vertel me iets over Survival",
                List.of(new AssistantActionProposal(AssistantActionType.FOLLOW_REQUESTER, "model suggestion"))
        );

        assertTrue(result.executed().isEmpty());
        assertEquals(List.of(AssistantActionType.FOLLOW_REQUESTER), result.rejected());
        verify(npc, never()).queueAction(any());
    }

    @Test
    void stopRequiresExplicitStopRequestAndCancelsCurrentMovement() {
        AssistantActionService service = new AssistantActionService(plugin());
        Player requester = mock(Player.class);
        NPC npc = mock(NPC.class);
        when(npc.isSpawned()).thenReturn(true);

        AssistantActionService.ActionResult result = service.validateAndExecute(
                requester, npc, "stop met lopen",
                List.of(new AssistantActionProposal(AssistantActionType.STOP_MOVING, "explicit stop"))
        );

        assertEquals(List.of(AssistantActionType.STOP_MOVING), result.executed());
        verify(npc).clearActionQueue();
        verify(npc).cancelCurrentAction();
    }

    private static JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.actions.enabled", true);
        config.set("openai.assistant.actions.allowed", List.of(
                "FOLLOW_REQUESTER", "COME_HERE", "STOP_MOVING"
        ));
        when(plugin.getConfig()).thenReturn(config);
        return plugin;
    }
}
