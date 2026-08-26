package nl.hauntedmc.ailex.assistant.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.domain.AssistantReply;
import nl.hauntedmc.ailex.assistant.proactive.ProactiveChatService;
import nl.hauntedmc.ailex.assistant.proactive.ProactiveChatSettings;
import nl.hauntedmc.ailex.assistant.proactive.ProactiveChatTrigger;
import nl.hauntedmc.ailex.assistant.runtime.AssistantConversationManager;
import nl.hauntedmc.ailex.assistant.runtime.AssistantRequestCoordinator;
import nl.hauntedmc.ailex.assistant.runtime.AssistantRequestTracer;
import nl.hauntedmc.ailex.assistant.runtime.PlayerResponseRateLimiter;
import nl.hauntedmc.ailex.assistant.runtime.context.ChatContextStore;
import nl.hauntedmc.ailex.infrastructure.openai.OpenAiResponsesClient;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.lifecycle.NpcManager;
import nl.hauntedmc.ailex.util.FormatterUtils;
import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

/**
 * Application-facing chat coordinator.
 *
 * <p>This class deliberately owns no Bukkit metadata extraction. The main-thread call to
 * {@link AssistantService#prepare} is the single authority for selective live context, avoiding duplicate snapshots
 * and duplicate prompt tokens. Raw chat history is only appended when {@link WorkingContextPolicy} says the turn
 * needs historical context.</p>
 */
public final class AssistantChatController implements AutoCloseable {

    private final AIlexPlugin plugin;
    private final AssistantService assistantService;
    private final AssistantChatConfiguration configuration;
    private final PlayerResponseRateLimiter responseRateLimiter;
    private final AssistantRequestCoordinator requestCoordinator;
    private final AssistantRequestTracer requestTracer;
    private final AssistantConversationManager conversationManager;
    private final ChatContextStore chatContextStore;
    private final ProactiveChatService proactiveChatService;
    private BukkitTask idleConversationTask;

    public AssistantChatController(AIlexPlugin plugin) {
        this.plugin = plugin;
        this.assistantService = plugin.getAssistantService();
        this.configuration = new AssistantChatConfiguration(plugin::getConfig);
        this.responseRateLimiter = new PlayerResponseRateLimiter(
                configuration::responseRateLimit,
                System::currentTimeMillis
        );
        this.requestCoordinator = new AssistantRequestCoordinator(this::dispatchAsync);
        AssistantRequestTracer configuredTracer = plugin.getAssistantRequestTracer();
        this.requestTracer = configuredTracer == null ? new AssistantRequestTracer() : configuredTracer;
        this.conversationManager = new AssistantConversationManager(System::currentTimeMillis);
        ChatContextStore.ContextSettings contextSettings = configuration.contextSettings();
        this.chatContextStore = new ChatContextStore(
                plugin.getDataFolder(),
                System::currentTimeMillis,
                contextSettings.persistToDisk()
        );
        this.proactiveChatService = new ProactiveChatService(() -> ProactiveChatSettings.from(plugin.getConfig()));
    }

    /** Starts one lightweight main-thread check; actual model work is always admitted through the async coordinator. */
    public void startProactiveConversationChecks() {
        if (idleConversationTask != null) {
            return;
        }
        idleConversationTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> proactiveChatService.checkForIdleConversation(
                        new ArrayList<>(Bukkit.getOnlinePlayers()),
                        this::submitProactiveResponse
                ),
                20L,
                20L
        );
    }

    /** Must be invoked on the server thread; the Paper adapter performs the async→main handoff. */
    public void handleChat(Player source, Component message) {
        if (source == null || message == null) {
            return;
        }
        proactiveChatService.recordPlayerMessage();
        String chatMessage = PlainTextComponentSerializer.plainText().serialize(message).trim();
        if (chatMessage.isBlank()) {
            return;
        }

        ChatContextStore.ContextSettings contextSettings = configuration.contextSettings();
        AssistantChatTarget target = directlyAddressedTarget(chatMessage);
        if (target != null) {
            submitRequest(source, target, chatMessage, contextSettings);
            return;
        }

        AssistantChatTarget followUpTarget = activeFollowUpTarget(source, chatMessage);
        if (followUpTarget != null) {
            submitRequest(source, followUpTarget, chatMessage, contextSettings);
            return;
        }

        chatContextStore.recordGeneralChat(source.getName(), chatMessage, contextSettings);
        proactiveChatService.onChat(source, chatMessage, this::onlinePlayers, this::submitProactiveResponse);
    }

    public void handleJoin(Player player) {
        if (player != null) {
            proactiveChatService.onJoin(player, this::submitProactiveResponse);
        }
    }

    private AssistantChatTarget directlyAddressedTarget(String message) {
        NpcManager npcManager = plugin.getNpcManager();
        if (npcManager != null) {
            for (NPC npc : npcManager.getNPCRegistry().values()) {
                if (npc.isChatEnabled() && npc.isSpawned() && AssistantMentionMatcher.isMentioned(message, npc.getName())) {
                    return AssistantChatTarget.fromNpc(npc);
                }
            }
        }
        if (!plugin.isNpcEnabled() && configuration.standaloneEnabled()) {
            AssistantChatTarget standalone = configuration.standaloneTarget();
            if (AssistantMentionMatcher.isMentioned(message, standalone.name())) {
                return standalone;
            }
        }
        return null;
    }

    private AssistantChatTarget activeFollowUpTarget(Player source, String message) {
        AssistantConversationManager.ActiveTarget active = conversationManager.activeTarget(
                source.getUniqueId(), configuration.sessionTimeoutMillis()
        );
        if (active == null || !conversationManager.isLikelyFollowUp(message, active.snapshot())) {
            return null;
        }
        if (active.npcId() == AssistantChatTarget.STANDALONE_ID) {
            return !plugin.isNpcEnabled() && configuration.standaloneEnabled()
                    ? configuration.standaloneTarget() : null;
        }
        NpcManager npcManager = plugin.getNpcManager();
        if (npcManager == null) {
            return null;
        }
        NPC npc = npcManager.getNPCRegistry().get(active.npcId());
        return npc != null && npc.isChatEnabled() && npc.isSpawned() ? AssistantChatTarget.fromNpc(npc) : null;
    }

    private void submitRequest(
            Player source,
            AssistantChatTarget target,
            String message,
            ChatContextStore.ContextSettings contextSettings
    ) {
        if (!configuration.mayUse(source)) {
            chatContextStore.recordGeneralChat(source.getName(), message, contextSettings);
            sendFeedback(source, "access_denied", "Je kunt AIlex hier niet gebruiken.");
            return;
        }
        if (!responseRateLimiter.tryAcquire(source.getUniqueId(), configuration.bypassRateLimit(source))) {
            chatContextStore.recordGeneralChat(source.getName(), message, contextSettings);
            sendRateLimitFeedback(source);
            return;
        }
        if (assistantService == null) {
            sendFeedback(source, "failure", "Mijn AI-service is nu niet beschikbaar. Probeer het zo nog eens.");
            return;
        }

        UUID sourceId = source.getUniqueId();
        String sourceName = source.getName();
        AssistantConversationManager.Snapshot dialogue = conversationManager.snapshot(
                sourceId, target.id(), configuration.sessionTimeoutMillis()
        );
        conversationManager.recordUser(sourceId, target.id(), sourceName, message);
        UUID requestId = requestTracer.start(sourceName, target.name(), dialogue.active() ? "follow-up" : "direct");

        try {
            String userPrompt = promptWithWorkingHistory(target, source, message, dialogue, contextSettings);
            AssistantService.PreparedRequest prepared = assistantService.prepare(
                    source,
                    target.npc(),
                    message,
                    target.systemPrompt(),
                    userPrompt,
                    "",
                    dialogue.asDialogueContext()
            );
            requestTracer.transition(requestId, AssistantRequestTracer.State.PREPARED, "");
            recordUserContext(source, target, message, contextSettings);

            AssistantRequestCoordinator.Priority priority = dialogue.active()
                    ? AssistantRequestCoordinator.Priority.FOLLOW_UP
                    : AssistantRequestCoordinator.Priority.DIRECT;
            AssistantRequestCoordinator.Submission submission = requestCoordinator.submit(
                    requestId,
                    sourceId,
                    priority,
                    () -> executeRequest(requestId, source, target, prepared, contextSettings),
                    configuration.maximumConcurrentRequests(),
                    configuration.maximumQueuedRequests()
            );
            handleSubmission(source, requestId, submission);
        } catch (RuntimeException exception) {
            requestTracer.transition(requestId, AssistantRequestTracer.State.UPSTREAM_FAILED, "prepare");
            LoggerUtils.logError("Could not prepare assistant chat request: " + exception.getMessage());
            sendFeedback(source, "failure", "Mijn AI-service gaf geen bruikbaar antwoord. Probeer het nog eens.");
        }
    }

    private String promptWithWorkingHistory(
            AssistantChatTarget target,
            Player source,
            String message,
            AssistantConversationManager.Snapshot dialogue,
            ChatContextStore.ContextSettings contextSettings
    ) {
        String userPrompt = target.userPrompt(source.getName(), message);
        if (!contextSettings.enabled()
                || !WorkingContextPolicy.includeRawHistory(message, dialogue.asDialogueContext())) {
            return userPrompt;
        }
        String history = chatContextStore.buildContext(
                source.getUniqueId(), target.id(), target.name(), message, contextSettings
        );
        return history.isBlank() ? userPrompt
                : userPrompt + "\n\n[Recent untrusted chat history]\n" + history;
    }

    private void recordUserContext(
            Player source,
            AssistantChatTarget target,
            String message,
            ChatContextStore.ContextSettings contextSettings
    ) {
        chatContextStore.recordGeneralChat(source.getName(), message, contextSettings);
        chatContextStore.recordConversation(
                source.getUniqueId(), target.id(), source.getName(), message, contextSettings
        );
        chatContextStore.recordBotMemory(target.id(), source.getName(), message, contextSettings);
    }

    private void executeRequest(
            UUID requestId,
            Player source,
            AssistantChatTarget target,
            AssistantService.PreparedRequest prepared,
            ChatContextStore.ContextSettings contextSettings
    ) {
        requestTracer.transition(requestId, AssistantRequestTracer.State.STARTED, "");
        try {
            OpenAiResponsesClient client = plugin.getOpenAiResponsesClient();
            if (client == null) {
                failUpstream(requestId, source, "client-unavailable");
                return;
            }

            String response;
            if (prepared.settings().enabled()) {
                AssistantReply reply = assistantService.respond(prepared);
                response = String.join("\n", reply.lines());
            } else {
                response = client.getChatResponse(target.systemPrompt(), prepared.userPrompt());
                assistantService.recordDirectResponse(prepared, response);
            }
            if (response == null || response.isBlank()) {
                failUpstream(requestId, source, "blank-response");
                return;
            }

            chatContextStore.recordConversation(
                    source.getUniqueId(), target.id(), target.name(), response, contextSettings
            );
            chatContextStore.recordBotMemory(target.id(), target.name(), response, contextSettings);
            conversationManager.recordAssistant(
                    source.getUniqueId(), target.id(), target.name(), response, prepared.analysis().intent()
            );
            proactiveChatService.recordBotResponse();

            Component result = FormatterUtils.serializer.deserialize(target.displayName() + ": ")
                    .append(Component.text(response, NamedTextColor.WHITE));
            Bukkit.getScheduler().runTask(plugin, () -> deliverTrackedResponse(requestId, source, result));
        } catch (Exception exception) {
            LoggerUtils.logError("Could not complete assistant chat request: " + exception.getMessage());
            failUpstream(requestId, source, "exception");
        }
    }

    private boolean submitProactiveResponse(Player contextPlayer, ProactiveChatTrigger trigger) {
        AssistantChatTarget target = firstAvailableNpc();
        if (target == null || assistantService == null) {
            return false;
        }
        String prompt = "Proactieve aanleiding: \"" + trigger.context() + "\"\n"
                + trigger.instruction() + " Antwoord als " + target.name()
                + " in exact één korte, gewone chatregel, zonder speaker label.";
        AssistantService.PreparedRequest prepared;
        try {
            prepared = assistantService.prepare(
                    contextPlayer,
                    target.npc(),
                    trigger.context(),
                    target.systemPrompt(),
                    prompt
            );
        } catch (RuntimeException exception) {
            LoggerUtils.logError("Could not prepare proactive assistant chat: " + exception.getMessage());
            return false;
        }

        UUID requestId = requestTracer.start(contextPlayer.getName(), target.name(), "proactive");
        AssistantRequestCoordinator.Submission submission = requestCoordinator.submit(
                requestId,
                proactiveOwner(target.id()),
                AssistantRequestCoordinator.Priority.PROACTIVE,
                () -> executeProactiveRequest(requestId, contextPlayer, target, trigger, prepared),
                configuration.maximumConcurrentRequests(),
                0
        );
        if (!submission.accepted()) {
            requestTracer.transition(requestId, AssistantRequestTracer.State.REJECTED, "direct-capacity-reserved");
            return false;
        }
        return true;
    }

    private void executeProactiveRequest(
            UUID requestId,
            Player contextPlayer,
            AssistantChatTarget target,
            ProactiveChatTrigger trigger,
            AssistantService.PreparedRequest prepared
    ) {
        requestTracer.transition(requestId, AssistantRequestTracer.State.STARTED, "");
        try {
            OpenAiResponsesClient client = plugin.getOpenAiResponsesClient();
            if (client == null) {
                requestTracer.transition(requestId, AssistantRequestTracer.State.UPSTREAM_FAILED, "client-unavailable");
                return;
            }
            String response = prepared.settings().enabled()
                    ? String.join("\n", assistantService.respond(prepared).lines())
                    : client.getChatResponse(target.systemPrompt(), prepared.userPrompt());
            if (!prepared.settings().enabled()) {
                assistantService.recordDirectResponse(prepared, response);
            }
            if (response == null || response.isBlank() || !trigger.accepts(response)) {
                requestTracer.transition(requestId, AssistantRequestTracer.State.REJECTED, "trigger-rejected-response");
                return;
            }
            proactiveChatService.recordBotResponse();
            Component result = FormatterUtils.serializer.deserialize(target.displayName() + ": ")
                    .append(Component.text(response, NamedTextColor.WHITE));
            Bukkit.getScheduler().runTask(plugin, () -> {
                deliverResponse(contextPlayer, result, proactiveChatService.responseVisibility());
                requestTracer.transition(requestId, AssistantRequestTracer.State.COMPLETED, "delivered");
            });
        } catch (Exception exception) {
            requestTracer.transition(requestId, AssistantRequestTracer.State.UPSTREAM_FAILED, "exception");
            LoggerUtils.logError("Could not complete proactive assistant chat: " + exception.getMessage());
        }
    }

    private AssistantChatTarget firstAvailableNpc() {
        NpcManager npcManager = plugin.getNpcManager();
        if (npcManager == null) {
            return null;
        }
        return npcManager.getNPCRegistry().values().stream()
                .filter(NPC::isChatEnabled)
                .filter(NPC::isSpawned)
                .findFirst()
                .map(AssistantChatTarget::fromNpc)
                .orElse(null);
    }

    private void handleSubmission(
            Player source,
            UUID requestId,
            AssistantRequestCoordinator.Submission submission
    ) {
        if (submission.supersededRequestId() != null) {
            requestTracer.transition(
                    submission.supersededRequestId(),
                    AssistantRequestTracer.State.SUPERSEDED,
                    "newer-direct-request"
            );
        }
        switch (submission.disposition()) {
            case STARTED -> { }
            case QUEUED -> {
                requestTracer.transition(requestId, AssistantRequestTracer.State.QUEUED, "waiting-for-capacity");
                sendFeedback(source, "queued", "Ik ben nog even bezig; ik pak je bericht hierna.");
            }
            case QUEUED_REPLACED -> {
                requestTracer.transition(requestId, AssistantRequestTracer.State.QUEUED, "replaced-older-queued-request");
                sendFeedback(source, "queued_replaced", "Ik ben nog bezig; ik pak je nieuwste bericht hierna.");
            }
            case REJECTED_BUSY, REJECTED_FULL -> {
                requestTracer.transition(
                        requestId,
                        AssistantRequestTracer.State.REJECTED,
                        submission.disposition().name().toLowerCase(Locale.ROOT)
                );
                sendFeedback(
                        source,
                        "busy",
                        "Ik heb het nu te druk om dit betrouwbaar te verwerken. Probeer het zo nog eens."
                );
            }
        }
    }

    private void deliverTrackedResponse(UUID requestId, Player source, Component response) {
        if (!source.isOnline()) {
            requestTracer.transition(requestId, AssistantRequestTracer.State.DELIVERY_FAILED, "requester-offline");
            return;
        }
        try {
            deliverResponse(source, response, configuration.responseVisibility());
            requestTracer.transition(requestId, AssistantRequestTracer.State.COMPLETED, "delivered");
        } catch (RuntimeException exception) {
            requestTracer.transition(requestId, AssistantRequestTracer.State.DELIVERY_FAILED, "delivery-exception");
            LoggerUtils.logError("Could not deliver assistant response: " + exception.getMessage());
        }
    }

    private void deliverResponse(Player source, Component response, String visibility) {
        String normalized = visibility == null ? "requester" : visibility.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "server" -> plugin.getServer().broadcast(response);
            case "nearby" -> {
                if (!source.isOnline()) {
                    return;
                }
                double radiusSquared = Math.pow(configuration.nearbyResponseRadius(), 2);
                for (Player recipient : source.getWorld().getPlayers()) {
                    if (recipient.getLocation().distanceSquared(source.getLocation()) <= radiusSquared) {
                        recipient.sendMessage(response);
                    }
                }
            }
            default -> {
                if (source.isOnline()) {
                    source.sendMessage(response);
                }
            }
        }
    }

    private void failUpstream(UUID requestId, Player source, String detail) {
        requestTracer.transition(requestId, AssistantRequestTracer.State.UPSTREAM_FAILED, detail);
        Bukkit.getScheduler().runTask(
                plugin,
                () -> sendFeedback(
                        source,
                        "failure",
                        "Mijn AI-service gaf geen bruikbaar antwoord. Probeer het nog eens."
                )
        );
    }

    private void sendRateLimitFeedback(Player source) {
        if (!configuration.rateLimitFeedbackEnabled() || source == null || !source.isOnline()) {
            return;
        }
        long remainingMillis = responseRateLimiter.retryAfterMillis(source.getUniqueId());
        long remainingSeconds = Math.max(1L, (long) Math.ceil(remainingMillis / 1000.0D));
        source.sendMessage(Component.text(configuration.rateLimitFeedback().replace(
                "{remaining_seconds}", String.valueOf(remainingSeconds)
        )));
    }

    private void sendFeedback(Player source, String key, String fallback) {
        if (source == null || !source.isOnline()) {
            return;
        }
        String message = configuration.feedback(key, fallback);
        if (!message.isBlank()) {
            source.sendMessage(Component.text(message));
        }
    }

    private java.util.Collection<? extends Player> onlinePlayers() {
        return plugin.getServer().getOnlinePlayers();
    }

    private UUID proactiveOwner(int npcId) {
        return UUID.nameUUIDFromBytes(("ailex-proactive-" + npcId).getBytes(StandardCharsets.UTF_8));
    }

    private void dispatchAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void close() {
        if (idleConversationTask != null) {
            idleConversationTask.cancel();
            idleConversationTask = null;
        }
    }
}
