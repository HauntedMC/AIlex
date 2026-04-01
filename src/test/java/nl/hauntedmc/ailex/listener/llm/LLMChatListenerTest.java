package nl.hauntedmc.ailex.listener.llm;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.ai.llm.ChatGPTClient;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCHandler;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LLMChatListenerTest {

    @Test
    void onChatShouldNotOverrideRendererAndSkipUnmentionedNpc() {
        HashMap<Integer, NPC> registry = new HashMap<>();
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn("BotName");
        registry.put(1, npc);

        AIlexPlugin plugin = mockPluginWithNpcRegistry(registry);
        LLMChatListener listener = new LLMChatListener(plugin);
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        Player player = mock(Player.class);

        when(player.getName()).thenReturn("Tester");
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text("No mention here"));

        listener.onChat(event);

        verify(event, never()).renderer(any());
        verifyNoInteractions(plugin.getChatGPTClient());
    }

    @Test
    void forwardChatToAIShouldSkipUnmentionedNpc() {
        HashMap<Integer, NPC> registry = new HashMap<>();
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn("BotName");
        registry.put(1, npc);

        AIlexPlugin plugin = mockPluginWithNpcRegistry(registry);
        ChatGPTClient chatClient = plugin.getChatGPTClient();
        LLMChatListener listener = new LLMChatListener(plugin);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Tester");
        Component message = Component.text("No mention here");

        listener.forwardChatToAI(player, message);
        verifyNoInteractions(chatClient);
    }

    private static AIlexPlugin mockPluginWithNpcRegistry(HashMap<Integer, NPC> registry) {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NPCHandler npcHandler = mock(NPCHandler.class);
        ChatGPTClient chatGPTClient = mock(ChatGPTClient.class);

        when(plugin.getNPCHandler()).thenReturn(npcHandler);
        when(plugin.getChatGPTClient()).thenReturn(chatGPTClient);
        when(npcHandler.getNPCRegistry()).thenReturn(registry);
        return plugin;
    }
}
