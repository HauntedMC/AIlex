package nl.hauntedmc.ailex.listener.llm;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.infrastructure.openai.OpenAiResponsesClient;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.lifecycle.NpcManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LLMChatListenerTest {

    @Test
    void onChatShouldNotOverrideRendererAndSkipUnmentionedNpc() {
        HashMap<Integer, NPC> registry = new HashMap<>();
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn("BotName");
        when(npc.isChatEnabled()).thenReturn(true);
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
        verifyNoInteractions(plugin.getOpenAiResponsesClient());
    }

    @Test
    void forwardChatToAIShouldSkipUnmentionedNpc() {
        HashMap<Integer, NPC> registry = new HashMap<>();
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn("BotName");
        when(npc.isChatEnabled()).thenReturn(true);
        registry.put(1, npc);

        AIlexPlugin plugin = mockPluginWithNpcRegistry(registry);
        OpenAiResponsesClient chatClient = plugin.getOpenAiResponsesClient();
        LLMChatListener listener = new LLMChatListener(plugin);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Tester");
        Component message = Component.text("No mention here");

        listener.forwardChatToAI(player, message);
        verifyNoInteractions(chatClient);
    }

    @Test
    void forwardChatToAIShouldSkipMentionedNpcWhenChatDisabled() {
        HashMap<Integer, NPC> registry = new HashMap<>();
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn("BotName");
        when(npc.isChatEnabled()).thenReturn(false);
        registry.put(1, npc);

        AIlexPlugin plugin = mockPluginWithNpcRegistry(registry);
        OpenAiResponsesClient chatClient = plugin.getOpenAiResponsesClient();
        LLMChatListener listener = new LLMChatListener(plugin);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Tester");
        Component message = Component.text("hey BotName");

        listener.forwardChatToAI(player, message);
        verifyNoInteractions(chatClient);
    }

    @Test
    void buildUserPromptShouldReplaceKnownPlaceholders() {
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn("BotName");
        when(npc.getDisplayName()).thenReturn("<gray>[Bot] BotName");
        when(npc.getUserPromptTemplate()).thenReturn(
                "P={player_name}|PD={player_display_name}|N={npc_name}|ND={npc_display_name}|M={chat_message}"
        );

        AIlexPlugin plugin = mockPluginWithNpcRegistry(new HashMap<>());
        LLMChatListener listener = new LLMChatListener(plugin);

        String result = listener.buildUserPrompt(npc, "Tester", "hello world");
        assertEquals(
                "P=Tester|PD=Tester|N=BotName|ND=<gray>[Bot] BotName|M=hello world",
                result
        );
    }

    @Test
    void buildSystemPromptShouldFallbackToDefaultWhenBlank() {
        NPC npc = mock(NPC.class);
        when(npc.getSystemPrompt()).thenReturn(" ");

        AIlexPlugin plugin = mockPluginWithNpcRegistry(new HashMap<>());
        LLMChatListener listener = new LLMChatListener(plugin);

        assertEquals(
                nl.hauntedmc.ailex.npc.NPCProperties.DEFAULT_SYSTEM_PROMPT,
                listener.buildSystemPrompt(npc)
        );
    }

    @Test
    void buildSystemPromptShouldAppendConfiguredKnowledge() {
        NPC npc = mock(NPC.class);
        when(npc.getSystemPrompt()).thenReturn("NPC persona");
        AIlexPlugin plugin = mockPluginWithNpcRegistry(new HashMap<>());
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.knowledge.enabled", true);
        config.set("openai.knowledge.max_characters", 12);
        config.set("openai.knowledge.prompt", "HauntedMC heeft Survival.");
        config.set("openai.chat_context.memory_instruction", "");
        when(plugin.getConfig()).thenReturn(config);

        String prompt = new LLMChatListener(plugin).buildSystemPrompt(npc);

        assertEquals("NPC persona\n\n[Betrouwbare HauntedMC-kennis]\nHauntedMC he", prompt);
    }

    @Test
    void buildMetadataShouldIncludeConfiguredHeldItemOnly() {
        AIlexPlugin plugin = mockPluginWithNpcRegistry(new HashMap<>());
        YamlConfiguration config = metadataConfigWithAllValuesDisabled();
        config.set("openai.chat_context.metadata.player.include_held_item", true);
        when(plugin.getConfig()).thenReturn(config);

        Player player = mock(Player.class);
        World world = mock(World.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getInventory()).thenReturn(inventory);
        ItemStack heldItem = mock(ItemStack.class);
        when(heldItem.getType()).thenReturn(Material.DIAMOND);
        when(heldItem.getAmount()).thenReturn(2);
        when(inventory.getItemInMainHand()).thenReturn(heldItem);

        assertEquals(
                "player_main_hand=minecraft:diamondx2",
                new LLMChatListener(plugin).buildMetadata(player, mock(NPC.class))
        );
    }

    private static YamlConfiguration metadataConfigWithAllValuesDisabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.chat_context.metadata.enabled", true);
        config.set("openai.chat_context.metadata.include_player_world", false);
        config.set("openai.chat_context.metadata.include_player_coordinates", false);
        config.set("openai.chat_context.metadata.include_player_game_mode", false);
        config.set("openai.chat_context.metadata.include_player_health", false);
        config.set("openai.chat_context.metadata.include_player_food_level", false);
        config.set("openai.chat_context.metadata.include_world_time", false);
        config.set("openai.chat_context.metadata.include_weather", false);
        config.set("openai.chat_context.metadata.include_npc_world", false);
        config.set("openai.chat_context.metadata.include_npc_coordinates", false);
        config.set("openai.chat_context.metadata.player.include_biome", false);
        config.set("openai.chat_context.metadata.player.include_facing", false);
        config.set("openai.chat_context.metadata.player.include_experience", false);
        config.set("openai.chat_context.metadata.player.include_held_item", false);
        config.set("openai.chat_context.metadata.player.include_armor", false);
        config.set("openai.chat_context.metadata.player.include_ping", false);
        config.set("openai.chat_context.metadata.player.include_playtime", false);
        config.set("openai.chat_context.metadata.world.include_difficulty", false);
        config.set("openai.chat_context.metadata.world.include_environment", false);
        config.set("openai.chat_context.metadata.world.include_light_level", false);
        config.set("openai.chat_context.metadata.server.include_name", false);
        config.set("openai.chat_context.metadata.server.include_online_player_count", false);
        config.set("openai.chat_context.metadata.server.include_version", false);
        config.set("openai.chat_context.metadata.server.include_performance", false);
        config.set("openai.chat_context.metadata.server.include_uptime", false);
        config.set("openai.chat_context.metadata.nearby_players.enabled", false);
        config.set("openai.chat_context.metadata.bot.include_id", false);
        config.set("openai.chat_context.metadata.bot.include_movement_behaviour", false);
        config.set("openai.chat_context.metadata.bot.include_current_action", false);
        return config;
    }

    private static AIlexPlugin mockPluginWithNpcRegistry(HashMap<Integer, NPC> registry) {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        NpcManager npcManager = mock(NpcManager.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);

        when(plugin.getNpcManager()).thenReturn(npcManager);
        when(plugin.getOpenAiResponsesClient()).thenReturn(openAiClient);
        when(npcManager.getNPCRegistry()).thenReturn(registry);
        return plugin;
    }
}
