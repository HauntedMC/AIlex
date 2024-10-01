package nl.hauntedmc.ailex.listener.llm;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.ai.llm.ChatGPTClient;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * Listener for chat events
 * Use ViewerUnaware to render chat messages without knowing the audience
 */
public class LLMChatListener implements Listener, ChatRenderer.ViewerUnaware {

    private final ChatGPTClient chatGPTClient;
    private final AIlexPlugin plugin;

    /**
     * Constructor for the ChatListener
     * @param plugin the AIlex plugin
     */
    public LLMChatListener(AIlexPlugin plugin) {
        this.chatGPTClient = plugin.getChatGPTClient();
        this.plugin = plugin;
    }

    /**
     * Handle the chat event, use the renderer for further processing
     * @param event the chat event
     */
    @EventHandler
    public void onChat(AsyncChatEvent event) {
        event.renderer(ChatRenderer.viewerUnaware(this)); // Tell the event to use our renderer
    }

    /**
     * Render the chat message
     * Forward the chat message to the AI if an NPC is mentioned
     * @param source the message source
     * @param sourceDisplayName the display name of the source player
     * @param message the chat message
     * @return the rendered chat message
     */
    @Override
    public @NotNull Component render(@NotNull Player source, @NotNull Component sourceDisplayName, @NotNull Component message) {
        forwardChatToAI(source, message);
        return message;
    }

    /**
     * Forward the chat message to the AI if an NPC is mentioned
     * @param source the chat message
     * @param message the chat message
     */
    void forwardChatToAI(Player source, Component message) {
        ArrayList<String> npcNames = new ArrayList<>();

        for (NPC npc : plugin.getNPCHandler().getNPCRegistry().values()) {
            npcNames.add(npc.getName());
        }

        // Get the chat message from the component
        String chatMessage = LegacyComponentSerializer.legacySection().serialize(message);

        // If an NPC is mentioned in the message forward chat to AI
        for (String npcName : npcNames) {
            if (chatMessage.toLowerCase().contains(npcName.toLowerCase())) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            String prompt = String.format(
                                    "Je speelt op dit moment op een Nederlandse Minecraft server. Een online speler genaamd %s zei: \"%s\"." +
                                            " Antwoord als een gemiddelde minecraft speler. Geef korte antwoorden en wees gematigd positief." +
                                            " Je antwoorden hoeven niet heel formeel, mag best casual en in spreektaal met soms een spelfoutje." +
                                            " Groet de speler niet in je antwoord, je antwoord is een reactie mogelijk midden in een gesprek.",
                                    source.getName(), chatMessage
                            );

                            String prefix = String.format("<grey>[Speler] %s: <white>", npcName);
                            String response = chatGPTClient.getChatResponse(prompt);

                            Component result =  MiniMessage.miniMessage().deserialize(prefix+response);

                            if (!response.isEmpty()) {
                                plugin.getServer().broadcast(result);
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
}
