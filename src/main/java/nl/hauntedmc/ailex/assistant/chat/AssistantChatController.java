package nl.hauntedmc.ailex.assistant.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.action.AssistantActionService;
import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.action.AssistantActionOutcomeRecorder;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;
import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantReply;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantEventMemoryService;
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
 * <p>This class owns routing and delivery, but not Bukkit metadata extraction. {@link AssistantService#prepare}
 * captures planner-selected live state synchronously before model work is admitted. Raw chat history is appended only
 * when {@link WorkingContextPolicy} says the turn needs historical context.</p>
 */
public final class AssistantChatController implements AutoCloseable {

    private final AIlexPlugin plugin;
    private final AssistantService assistantService;
    private final AssistantActionService assistantActionService;
    private final AssistantChatConfiguration configuration;
    private final PlayerResponseRateLimiter responseRateLimiter;
    private final AssistantRequestCoordinator requestCoordinator;
    private final AssistantRequestTracer requestTracer;
    private final AssistantConversationManager conversationManager;
    private final ChatContextStore chatContextStore;
    private final ProactiveChatService proactiveChatService;
    private final AssistantActionOutcomeRecorder actionOutcomeRecorder;
    private BukkitTask idleConversationTask;

    public AssistantChatController(AIlexPlugin plugin) {
        this.plugin = plugin;
        this.assistantService = plugin.getAssistantService();
        this.assistantActionService = new AssistantActionService(plugin);
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
        this.proactiveChatService = new ProactiveChatService(
                () -> ProactiveChatSettings.from(plugin.getConfig()), plugin.getAssistantMemoryService()
        );
        this.actionOutcomeRecorder = new AssistantActionOutcomeRecorder(
                plugin.getAssistantEventMemoryService(),
                new AssistantExperienceMemoryService(plugin.getAssistantMemoryService())
        );
    }

    /** Starts one lightweight main-thread check; actual model work is always admitted through the async coordinator. */
    public void startProactiveConversationChecks() {
        if (idleConversationTask != null) {
            return;
        }
        idleConversationTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    ArrayList<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                    proactiveChatService.checkForIdleConversation(online, this::submitProactiveResponse);
                    proactiveChatService.checkForScheduledGoals(online, this::submitProactiveResponse);
                },
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

        boolean ambientPlayerConversation = proactiveChatService.isLikelyPlayerConversation(
                source,
                chatMessage,
                onlinePlayers()
        );
        if (!ambientPlayerConversation) {
            AssistantChatTarget followUpTarget = activeFollowUpTarget(source, chatMessage);
            if (followUpTarget != null) {
                submitRequest(source, followUpTarget, chatMessage, contextSettings);
                return;
            }
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
                if (chatAvailable(npc) && AssistantMentionMatcher.isMentioned(message, npc.getName())) {
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
        return chatAvailable(npc) ? AssistantChatTarget.fromNpc(npc) : null;
    }

    /**
     * Conversational availability is intentionally independent from Citizens entity spawn state. A chunk unload or
     * failed physical respawn may remove embodiment, but must never make an otherwise registered chat assistant silent.
     */
    static boolean chatAvailable(NPC npc) {
        return npc != null && npc.isChatEnabled();
    }

    private void submitRequest(
            Player source,
            AssistantChatTarget target,
            String message,
            ChatContextStore.ContextSettings contextSettings
    ) {
        UUID sourceId = source.getUniqueId();
        String sourceName = source.getName();
        AssistantConversationManager.Snapshot dialogue = conversationManager.snapshot(
                sourceId, target.id(), configuration.sessionTimeoutMillis()
        );
        UUID requestId = requestTracer.start(sourceName, target.name(), dialogue.active() ? "follow-up" : "direct");

        if (!configuration.mayUse(source)) {
            chatContextStore.recordGeneralChat(sourceName, message, contextSettings);
            requestTracer.transition(requestId, AssistantRequestTracer.State.REJECTED, "access-denied");
            sendFeedback(source, "access_denied", "Je kunt AIlex hier niet gebruiken.");
            return;
        }
        if (assistantService == null) {
            requestTracer.transition(requestId, AssistantRequestTracer.State.UPSTREAM_FAILED, "assistant-service-unavailable");
            sendFeedback(source, "failure", "Mijn AI-service is nu niet beschikbaar. Probeer het zo nog eens.");
            return;
        }
        if (!responseRateLimiter.tryAcquire(sourceId, configuration.bypassRateLimit(source))) {
            chatContextStore.recordGeneralChat(sourceName, message, contextSettings);
            requestTracer.transition(requestId, AssistantRequestTracer.State.REJECTED, "rate-limited");
            sendRateLimitFeedback(source);
            return;
        }

        conversationManager.recordUser(sourceId, target.id(), sourceName, message);
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
                    () -> executeRequest(requestId, sourceId, source, target, prepared, contextSettings),
                    configuration.maximumConcurrentRequests(),
                    configuration.maximumQueuedRequests()
            );
            handleSubmission(source, requestId, submission);
        } catch (RuntimeException exception) {
            requestTracer.transition(requestId, AssistantRequestTracer.State.UPSTREAM_FAILED, "prepare-or-dispatch");
            LoggerUtils.logError("Could not prepare or dispatch assistant chat request: " + exception.getMessage());
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

    private void recordCompletedInteraction(UUID sourceId, AssistantChatTarget target) {
        AssistantEventMemoryService eventMemory = plugin.getAssistantEventMemoryService();
        if (eventMemory != null) {
            eventMemory.recordInteraction(sourceId, String.valueOf(target.id()));
        }
    }

    private void executeRequest(
            UUID requestId,
            UUID sourceId,
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
            AssistantReply reply = null;
            if (prepared.settings().enabled()) {
                reply = assistantService.respond(prepared);
                response = String.join("\n", reply.lines());
            } else {
                response = client.getChatResponse(target.systemPrompt(), prepared.userPrompt());
                assistantService.recordDirectResponse(prepared, response);
            }
            if (response == null || response.isBlank()) {
                failUpstream(requestId, source, "blank-response");
                return;
            }

            recordCompletedInteraction(sourceId, target);
            chatContextStore.recordConversation(sourceId, target.id(), target.name(), response, contextSettings);
            chatContextStore.recordBotMemory(target.id(), target.name(), response, contextSettings);
            conversationManager.recordAssistant(
                    sourceId, target.id(), target.name(), response, prepared.analysis().intent()
            );
            proactiveChatService.recordBotResponse();

            Component result = FormatterUtils.serializer.deserialize(target.displayName() + ": ")
                    .append(Component.text(response, NamedTextColor.WHITE));
            AssistantReply completedReply = reply;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (completedReply != null && target.npc() != null && !completedReply.actionProposals().isEmpty()) {
                    var actionResult = assistantActionService.validateAndExecute(
                            source, target.npc(), prepared.message(), completedReply.actionProposals()
                    );
                    actionOutcomeRecorder.record(
                            source, String.valueOf(target.id()), prepared.message(), actionResult
                    );
                }
                deliverTrackedResponse(requestId, source, result);
            });
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
        String prompt = "Proactief doel=" + trigger.goal().name().toLowerCase(Locale.ROOT)
                + " aanleiding: \"" + trigger.context() + "\"\n"
                + trigger.instruction() + " Antwoord als " + target.name()
                + " in exact één korte, gewone chatregel, zonder speaker label.";
        AssistantService.PreparedRequest prepared;
        try {
            prepared = publicProactiveRequest(assistantService.prepare(
                    contextPlayer,
                    target.npc(),
                    trigger.context(),
                    target.systemPrompt(),
                    prompt
            ));
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

    /**
     * Proactive responses can be broadcast publicly, so they must never receive one player's private memory, live
     * requester state or direct-dialogue history. Reviewed knowledge retrieval remains available through the prepared
     * request, while the model-facing player-specific context is removed before asynchronous generation.
     */
    private AssistantService.PreparedRequest publicProactiveRequest(AssistantService.PreparedRequest prepared) {
        return new AssistantService.PreparedRequest(
                prepared.playerId(),
                prepared.playerName(),
                prepared.npcName(),
                prepared.npcMemoryId(),
                prepared.message(),
                prepared.systemPrompt(),
                prepared.userPrompt(),
                prepared.analysis(),
                prepared.settings(),
                prepared.contextPlan(),
                prepared.retrieveKnowledge(),
                new AssistantService.LiveSnapshot(java.util.List.of(), java.util.Set.of()),
                "",
                AssistantDialogueContext.empty(),
                false,
                prepared.preparedAtNanos()
        );
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
                deliverResponse(
                        contextPlayer, result, trigger.privateOnly() ? "requester" : proactiveChatService.responseVisibility()
                );
                proactiveChatService.recordDeliveredGoal(contextPlayer, trigger);
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
                .filter(AssistantChatController::chatAvailable)
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
