package nl.hauntedmc.ailex.assistant.adapter.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.chat.AssistantChatController;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Thin Paper adapter. All assistant chat behavior lives outside the event-listener package. */
public final class AssistantChatListener implements Listener, AutoCloseable {

    private final AIlexPlugin plugin;
    private final AssistantChatController controller;

    public AssistantChatListener(AIlexPlugin plugin) {
        this.plugin = plugin;
        this.controller = new AssistantChatController(plugin);
    }

    public void startProactiveConversationChecks() {
        controller.startProactiveConversationChecks();
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin, () -> controller.handleChat(event.getPlayer(), event.message()));
            return;
        }
        controller.handleChat(event.getPlayer(), event.message());
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        controller.handleJoin(event.getPlayer());
    }

    @Override
    public void close() {
        controller.close();
    }
}
