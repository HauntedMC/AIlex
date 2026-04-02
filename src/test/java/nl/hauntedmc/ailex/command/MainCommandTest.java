package nl.hauntedmc.ailex.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPCHandler;
import nl.hauntedmc.ailex.testutil.ConfigTestSupport;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
        NPCHandler npcHandler = mock(NPCHandler.class);
        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(npcHandler.getNPCRegistry()).thenReturn(new HashMap<>());

        MainCommand command = new MainCommand(plugin);
        CommandSourceStack source = mock(CommandSourceStack.class);

        Collection<String> suggestions = command.suggest(source, new String[]{});

        assertTrue(suggestions.contains("create"));
        assertTrue(suggestions.contains("action"));
        assertTrue(suggestions.contains("reload"));
    }

    @Test
    void suggestShouldReturnNpcIdsForIdBasedSubcommands() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler npcHandler = mock(NPCHandler.class);
        HashMap<Integer, nl.hauntedmc.ailex.npc.NPC> registry = new HashMap<>();
        registry.put(3, mock(nl.hauntedmc.ailex.npc.NPC.class));
        registry.put(7, mock(nl.hauntedmc.ailex.npc.NPC.class));
        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(npcHandler.getNPCRegistry()).thenReturn(registry);

        MainCommand command = new MainCommand(plugin);
        CommandSourceStack source = mock(CommandSourceStack.class);

        Collection<String> suggestions = command.suggest(source, new String[]{"action"});

        assertTrue(suggestions.contains("3"));
        assertTrue(suggestions.contains("7"));
    }

    @Test
    void suggestShouldReturnActionSuggestionsForActionSubcommand() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler npcHandler = mock(NPCHandler.class);
        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(npcHandler.getNPCRegistry()).thenReturn(new HashMap<>());

        MainCommand command = new MainCommand(plugin);
        CommandSourceStack source = mock(CommandSourceStack.class);

        Collection<String> suggestions = command.suggest(source, new String[]{"action", "1", ""});

        assertTrue(suggestions.contains("movehere"));
        assertTrue(suggestions.contains("followplayer"));
        assertTrue(suggestions.contains("fleeplayer"));
        assertTrue(suggestions.contains("mirrorplayer"));
    }

    @Test
    void suggestShouldReturnBehaviourOptionsForSetMoveBehaviour() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler npcHandler = mock(NPCHandler.class);
        HashMap<Integer, nl.hauntedmc.ailex.npc.NPC> registry = new HashMap<>();
        registry.put(1, mock(nl.hauntedmc.ailex.npc.NPC.class));
        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(npcHandler.getNPCRegistry()).thenReturn(registry);

        MainCommand command = new MainCommand(plugin);
        CommandSourceStack source = mock(CommandSourceStack.class);

        Collection<String> settingSuggestions = command.suggest(source, new String[]{"set", "1", ""});
        Collection<String> behaviourSuggestions = command.suggest(source, new String[]{"set", "1", "movebehaviour", ""});

        assertTrue(settingSuggestions.contains("movebehaviour"));
        assertTrue(behaviourSuggestions.contains("seek"));
        assertTrue(behaviourSuggestions.contains("arrive"));
    }

    @Test
    void suggestShouldReturnNpcTypesForCreateSubcommand() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler npcHandler = mock(NPCHandler.class);
        HashMap<Integer, nl.hauntedmc.ailex.npc.NPC> registry = new HashMap<>();
        registry.put(1, mock(nl.hauntedmc.ailex.npc.NPC.class));
        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(npcHandler.getNPCRegistry()).thenReturn(registry);

        MainCommand command = new MainCommand(plugin);
        CommandSourceStack source = mock(CommandSourceStack.class);

        Collection<String> suggestions = command.suggest(source, new String[]{"create", "1", ""});
        assertTrue(suggestions.contains("ailex_npc"));
    }

    @Test
    void suggestShouldKeepIdSuggestionsWhenIdArgumentIsCurrentToken() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler npcHandler = mock(NPCHandler.class);
        HashMap<Integer, nl.hauntedmc.ailex.npc.NPC> registry = new HashMap<>();
        registry.put(3, mock(nl.hauntedmc.ailex.npc.NPC.class));
        registry.put(7, mock(nl.hauntedmc.ailex.npc.NPC.class));
        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(npcHandler.getNPCRegistry()).thenReturn(registry);

        MainCommand command = new MainCommand(plugin);
        CommandSourceStack source = mock(CommandSourceStack.class);

        Collection<String> suggestions = command.suggest(source, new String[]{"action", ""});

        assertTrue(suggestions.contains("3"));
        assertTrue(suggestions.contains("7"));
        assertFalse(suggestions.contains("movehere"));
    }

    @Test
    void suggestShouldReturnEmptyListForUnknownPattern() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler npcHandler = mock(NPCHandler.class);
        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(npcHandler.getNPCRegistry()).thenReturn(new HashMap<>());

        MainCommand command = new MainCommand(plugin);
        CommandSourceStack source = mock(CommandSourceStack.class);

        Collection<String> suggestions = command.suggest(source, new String[]{"unknown", "x", "y"});
        assertFalse(suggestions.iterator().hasNext());
    }

    @Test
    void executeShouldNoOpForNonPlayerSender() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler npcHandler = mock(NPCHandler.class);
        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(npcHandler.getNPCRegistry()).thenReturn(new HashMap<>());

        MainCommand command = new MainCommand(plugin);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(mock(CommandSender.class));

        command.execute(source, new String[]{"reload"});
    }
}
