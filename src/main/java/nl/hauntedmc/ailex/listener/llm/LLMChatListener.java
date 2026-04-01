package nl.hauntedmc.ailex.listener.llm;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCHandler;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.ai.llm.ChatGPTClient;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;

/**
 * Listener for chat events.
 * This listener observes player chat for NPC mentions and never overrides server chat rendering.
 */
public class LLMChatListener implements Listener {

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

        ArrayList<String> npcNames = new ArrayList<>();

        for (NPC npc : npcHandler.getNPCRegistry().values()) {
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

                            String response = chatGPTClient.getChatResponse(prompt);

                            Component result = Component.text("[Speler] " + npcName + ": ", NamedTextColor.GRAY)
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
}
