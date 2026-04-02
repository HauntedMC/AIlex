package nl.hauntedmc.ailex.listener.llm;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCHandler;
import nl.hauntedmc.ailex.npc.NPCProperties;
import nl.hauntedmc.ailex.util.FormatterUtils;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.ai.llm.ChatGPTClient;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listener for chat events.
 * This listener observes player chat for NPC mentions and never overrides server chat rendering.
 */
public class LLMChatListener implements Listener {

    private final ChatGPTClient chatGPTClient;
    private final AIlexPlugin plugin;
    static final String PLACEHOLDER_PLAYER_NAME = "{player_name}";
    static final String PLACEHOLDER_PLAYER_DISPLAY_NAME = "{player_display_name}";
    static final String PLACEHOLDER_NPC_NAME = "{npc_name}";
    static final String PLACEHOLDER_NPC_DISPLAY_NAME = "{npc_display_name}";
    static final String PLACEHOLDER_CHAT_MESSAGE = "{chat_message}";

    /**
     * Constructor for the ChatListener
     * @param plugin the AIlex plugin
     */
    public LLMChatListener(AIlexPlugin plugin) {
        this.chatGPTClient = plugin.getChatGPTClient();
        this.plugin = plugin;
    }

    /**
     * Handle the chat event and forward player messages to AI when an NPC is mentioned.
     * @param event the chat event
     */
    @EventHandler
    public void onChat(AsyncChatEvent event) {
        forwardChatToAI(event.getPlayer(), event.message());
    }

    /**
     * Forward the chat message to the AI if an NPC is mentioned
     * @param source the chat message
     * @param message the chat message
     */
    void forwardChatToAI(Player source, Component message) {
        NPCHandler npcHandler = plugin.getNPCHandler();
        if (npcHandler == null) {
            return;
        }

        // Get the chat message from the component
        String chatMessage = LegacyComponentSerializer.legacySection().serialize(message);

        // If an NPC is mentioned in the message forward chat to AI
        for (NPC npc : npcHandler.getNPCRegistry().values()) {
            if (!npc.isChatEnabled()) {
                continue;
            }

            String npcName = npc.getName();
            if (chatMessage.toLowerCase().contains(npcName.toLowerCase())) {
                String npcDisplayName = npc.getDisplayName();
                String sourceName = source.getName();
                String systemPrompt = buildSystemPrompt(npc);
                String userPrompt = buildUserPrompt(npc, sourceName, chatMessage);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            String response = chatGPTClient.getChatResponse(systemPrompt, userPrompt);

                            Component result = FormatterUtils.serializer.deserialize(npcDisplayName + ": ")
                                    .append(Component.text(response, NamedTextColor.WHITE));

                            if (!response.isEmpty()) {
                                Bukkit.getScheduler().runTask(plugin, () -> plugin.getServer().broadcast(result));
                            }
                        } catch (Exception e) {
                            LoggerUtils.logError(e.getMessage());
                        }
                    }
                }.runTaskAsynchronously(plugin);
                return;
            }
        }
    }

    String buildSystemPrompt(NPC npc) {
        String configuredSystemPrompt = npc.getSystemPrompt();
        if (configuredSystemPrompt == null || configuredSystemPrompt.isBlank()) {
            return NPCProperties.DEFAULT_SYSTEM_PROMPT;
        }
        return configuredSystemPrompt;
    }

    String buildUserPrompt(NPC npc, String sourceName, String chatMessage) {
        String template = npc.getUserPromptTemplate();
        if (template == null || template.isBlank()) {
            template = NPCProperties.DEFAULT_USER_PROMPT_TEMPLATE;
        }

        return template
                .replace(PLACEHOLDER_PLAYER_NAME, sourceName)
                .replace(PLACEHOLDER_PLAYER_DISPLAY_NAME, sourceName)
                .replace(PLACEHOLDER_NPC_NAME, npc.getName())
                .replace(PLACEHOLDER_NPC_DISPLAY_NAME, npc.getDisplayName())
                .replace(PLACEHOLDER_CHAT_MESSAGE, chatMessage);
    }
}
