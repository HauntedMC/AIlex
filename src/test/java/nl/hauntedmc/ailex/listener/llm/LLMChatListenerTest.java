package nl.hauntedmc.ailex.listener.llm;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.ai.llm.ChatGPTClient;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCHandler;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LLMChatListenerTest {

    @Test
    void onChatShouldInstallViewerUnawareRenderer() {
        AIlexPlugin plugin = mockPluginWithNpcRegistry(new HashMap<>());
        LLMChatListener listener = new LLMChatListener(plugin);
        AsyncChatEvent event = mock(AsyncChatEvent.class);

        listener.onChat(event);

        verify(event).renderer(any(ChatRenderer.class));
    }

    @Test
    void renderShouldReturnOriginalMessageAndSkipUnmentionedNpc() {
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

        Component result = listener.render(player, Component.text("Tester"), message);

        assertEquals(message, result);
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
