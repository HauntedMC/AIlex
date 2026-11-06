package nl.hauntedmc.ailex.assistant.adapter.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.ChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.chat.AssistantChatController;
import nl.hauntedmc.ailex.assistant.chat.AssistantChatObservationService;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** Paper ingress boundary. Only eligible public chat may reach assistant behavior or memory. */
public final class AssistantChatListener implements Listener, AutoCloseable {

    private final AIlexPlugin plugin;
    private final AssistantChatController controller;
    private final AssistantChatObservationService observationService;
    private final PublicChatEligibilityPolicy eligibilityPolicy = new PublicChatEligibilityPolicy();
    private final AtomicBoolean synchronousChatBlocked = new AtomicBoolean();

    public AssistantChatListener(AIlexPlugin plugin) {
        this(plugin, new AssistantChatController(plugin), new AssistantChatObservationService(plugin));
    }

    AssistantChatListener(
            AIlexPlugin plugin,
            AssistantChatController controller,
            AssistantChatObservationService observationService
    ) {
        this.plugin = plugin;
        this.controller = controller;
        this.observationService = observationService;
    }

    public void startProactiveConversationChecks() {
        controller.startProactiveConversationChecks();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled() || !synchronousChatCompatible()) {
            return;
        }

        // Freeze the modified payload and explicit recipients at MONITOR; never hand a mutable viewer set
        // or originalMessage() to assistant services. Opaque audiences cannot establish public delivery.
        Set<UUID> recipients = new HashSet<>();
        for (Audience viewer : event.viewers()) {
            if (viewer instanceof Player player) {
                recipients.add(player.getUniqueId());
            } else if (!(viewer instanceof ConsoleCommandSender)) {
                return;
            }
        }
        PublicChatSnapshot snapshot = new PublicChatSnapshot(
                event.getPlayer(), event.message(), Set.copyOf(recipients)
        );
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin, () -> processIfEligible(event, snapshot));
            return;
        }
        processIfEligible(event, snapshot);
    }

    /** Server thread only. No assistant consumer may run before this shared eligibility gate. */
    private void processIfEligible(AsyncChatEvent event, PublicChatSnapshot snapshot) {
        if (event.isCancelled() || !synchronousChatCompatible() || !snapshot.source().isOnline()) {
            return;
        }
        Set<UUID> onlinePlayers = plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toSet());
        if (!eligibilityPolicy.allows(snapshot.source().getUniqueId(), snapshot.recipients(), onlinePlayers)) {
            return;
        }
        observationService.observe(snapshot.source(), snapshot.message());
        controller.handleChat(snapshot.source(), snapshot.message());
    }

    @SuppressWarnings("deprecation") // Inspect presence only: registering this deprecated event would stall all chat.
    private boolean synchronousChatCompatible() {
        boolean blocked = ChatEvent.getHandlerList().getRegisteredListeners().length != 0;
        boolean previouslyBlocked = synchronousChatBlocked.getAndSet(blocked);
        if (blocked && !previouslyBlocked) {
            plugin.getLogger().warning("AIlex chat ingestion disabled: synchronous Paper ChatEvent handlers can "
                    + "change messages after AsyncChatEvent. Use AsyncChatEvent handlers to restore ingestion.");
        } else if (!blocked && previouslyBlocked) {
            plugin.getLogger().info("AIlex chat ingestion resumed: no synchronous Paper ChatEvent handlers remain.");
        }
        return !blocked;
    }

    private record PublicChatSnapshot(Player source, Component message, Set<UUID> recipients) {}

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        controller.handleJoin(event.getPlayer());
    }

    @Override
    public void close() {
        controller.close();
    }
}
