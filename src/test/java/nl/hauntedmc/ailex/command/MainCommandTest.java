package nl.hauntedmc.ailex.command;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.lifecycle.NpcManager;
import nl.hauntedmc.ailex.testutil.ConfigTestSupport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MainCommandTest {

    @BeforeEach
    void setUpConfig() {
        ConfigTestSupport.initWith(Map.of(
                "npc.behaviour.seek.maxAcceleration", 4.0,
                "npc.action.movehere.targetDistance", 0.5
        ));
    }

    @AfterEach
    void tearDownConfig() {
        ConfigTestSupport.reset();
    }

    @Test
    void suggestShouldIncludeKnownSubcommandsWithoutArguments() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager npcManager = mock(NpcManager.class);
        when(plugin.getNpcManager()).thenReturn(npcManager);
        when(npcManager.getNPCRegistry()).thenReturn(new HashMap<>());

        MainCommand command = new MainCommand(plugin);
        CommandSender source = mock(CommandSender.class);

        Collection<String> suggestions = tabComplete(command, source, new String[]{});

        assertTrue(suggestions.contains("create"));
        assertTrue(suggestions.contains("action"));
        assertTrue(suggestions.contains("reload"));
    }

    @Test
    void suggestShouldReturnNpcIdsForIdBasedSubcommands() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager npcManager = mock(NpcManager.class);
        HashMap<Integer, nl.hauntedmc.ailex.npc.NPC> registry = new HashMap<>();
        registry.put(3, mock(nl.hauntedmc.ailex.npc.NPC.class));
        registry.put(7, mock(nl.hauntedmc.ailex.npc.NPC.class));
        when(plugin.getNpcManager()).thenReturn(npcManager);
        when(npcManager.getNPCRegistry()).thenReturn(registry);

        MainCommand command = new MainCommand(plugin);
        CommandSender source = mock(CommandSender.class);

        Collection<String> suggestions = tabComplete(command, source, new String[]{"action"});

        assertTrue(suggestions.contains("3"));
        assertTrue(suggestions.contains("7"));
    }

    @Test
    void suggestShouldReturnActionSuggestionsForActionSubcommand() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager npcManager = mock(NpcManager.class);
        when(plugin.getNpcManager()).thenReturn(npcManager);
        when(npcManager.getNPCRegistry()).thenReturn(new HashMap<>());

        MainCommand command = new MainCommand(plugin);
        CommandSender source = mock(CommandSender.class);

        Collection<String> suggestions = tabComplete(command, source, new String[]{"action", "1", ""});

        assertTrue(suggestions.contains("movehere"));
        assertTrue(suggestions.contains("followplayer"));
        assertTrue(suggestions.contains("fleeplayer"));
        assertTrue(suggestions.contains("mirrorplayer"));
    }

    @Test
    void suggestShouldReturnBehaviourOptionsForSetMoveBehaviour() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager npcManager = mock(NpcManager.class);
        HashMap<Integer, nl.hauntedmc.ailex.npc.NPC> registry = new HashMap<>();
        registry.put(1, mock(nl.hauntedmc.ailex.npc.NPC.class));
        when(plugin.getNpcManager()).thenReturn(npcManager);
        when(npcManager.getNPCRegistry()).thenReturn(registry);

        MainCommand command = new MainCommand(plugin);
        CommandSender source = mock(CommandSender.class);

        Collection<String> settingSuggestions = tabComplete(command, source, new String[]{"set", "1", ""});
        Collection<String> behaviourSuggestions = tabComplete(command, source, new String[]{"set", "1", "movebehaviour", ""});

        assertTrue(settingSuggestions.contains("movebehaviour"));
        assertTrue(behaviourSuggestions.contains("seek"));
        assertTrue(behaviourSuggestions.contains("arrive"));
    }

    @Test
    void suggestShouldReturnNpcTypesForCreateSubcommand() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager npcManager = mock(NpcManager.class);
        HashMap<Integer, nl.hauntedmc.ailex.npc.NPC> registry = new HashMap<>();
        registry.put(1, mock(nl.hauntedmc.ailex.npc.NPC.class));
        when(plugin.getNpcManager()).thenReturn(npcManager);
        when(npcManager.getNPCRegistry()).thenReturn(registry);

        MainCommand command = new MainCommand(plugin);
        CommandSender source = mock(CommandSender.class);

        Collection<String> suggestions = tabComplete(command, source, new String[]{"create", "1", ""});
        assertTrue(suggestions.contains("ailex_npc"));
    }

    @Test
    void suggestShouldKeepIdSuggestionsWhenIdArgumentIsCurrentToken() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager npcManager = mock(NpcManager.class);
        HashMap<Integer, nl.hauntedmc.ailex.npc.NPC> registry = new HashMap<>();
        registry.put(3, mock(nl.hauntedmc.ailex.npc.NPC.class));
        registry.put(7, mock(nl.hauntedmc.ailex.npc.NPC.class));
        when(plugin.getNpcManager()).thenReturn(npcManager);
        when(npcManager.getNPCRegistry()).thenReturn(registry);

        MainCommand command = new MainCommand(plugin);
        CommandSender source = mock(CommandSender.class);

        Collection<String> suggestions = tabComplete(command, source, new String[]{"action", ""});

        assertTrue(suggestions.contains("3"));
        assertTrue(suggestions.contains("7"));
        assertFalse(suggestions.contains("movehere"));
    }

    @Test
    void suggestShouldReturnEmptyListForUnknownPattern() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager npcManager = mock(NpcManager.class);
        when(plugin.getNpcManager()).thenReturn(npcManager);
        when(npcManager.getNPCRegistry()).thenReturn(new HashMap<>());

        MainCommand command = new MainCommand(plugin);
        CommandSender source = mock(CommandSender.class);

        Collection<String> suggestions = tabComplete(command, source, new String[]{"unknown", "x", "y"});
        assertFalse(suggestions.iterator().hasNext());
    }

    @Test
    void executeShouldExplainWhenSenderIsNotAPlayer() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager npcManager = mock(NpcManager.class);
        when(plugin.getNpcManager()).thenReturn(npcManager);
        when(npcManager.getNPCRegistry()).thenReturn(new HashMap<>());

        MainCommand command = new MainCommand(plugin);
        CommandSender source = mock(CommandSender.class);

        command.onCommand(source, mock(Command.class), "ailex", new String[]{"reload"});

        verify(source).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void executeShouldSendUsageDirectlyToThePlayer() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager npcManager = mock(NpcManager.class);
        Player player = mock(Player.class);
        CommandSender source = player;
        when(plugin.getNpcManager()).thenReturn(npcManager);
        when(npcManager.getNPCRegistry()).thenReturn(new HashMap<>());

        new MainCommand(plugin).onCommand(source, mock(Command.class), "ailex", new String[]{});

        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void executeShouldRejectPlayerWithoutAdminPermission() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        Player player = mock(Player.class);
        when(player.hasPermission("ailex.admin")).thenReturn(false);

        new MainCommand(plugin).onCommand(player, mock(Command.class), "ailex", new String[]{"reload"});

        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(plugin, never()).reloadOpenAiResponsesClient();
    }

    private Collection<String> tabComplete(MainCommand command, CommandSender sender, String[] args) {
        return command.onTabComplete(sender, mock(Command.class), "ailex", args);
    }
}
