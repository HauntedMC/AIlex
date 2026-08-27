package nl.hauntedmc.ailex.assistant.proactive;

import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Coordinates low-priority proactive behavior independently from model execution and scheduling. */
public final class ProactiveChatService {

    private final Supplier<ProactiveChatSettings> settingsSupplier;
    private final LongSupplier currentTimeMillis;
    private final CollectiveReactionTracker collectiveTracker = new CollectiveReactionTracker();
    private final SocialConversationGraph socialGraph = new SocialConversationGraph();
    private final ProactiveGoalService goalService;
    private final AtomicLong lastBotMessageMillis;
    private final AtomicLong lastPlayerMessageMillis;
    private final AtomicLong lastProactiveRequestMillis = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong lastJoinResponseMillis = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong lastIdleCheckMillis = new AtomicLong(Long.MIN_VALUE);

    public ProactiveChatService(Supplier<ProactiveChatSettings> settingsSupplier) {
        this(settingsSupplier, System::currentTimeMillis, null);
    }

    public ProactiveChatService(
            Supplier<ProactiveChatSettings> settingsSupplier,
            AssistantMemoryService memoryService
    ) {
        this(settingsSupplier, System::currentTimeMillis, memoryService);
    }

    ProactiveChatService(Supplier<ProactiveChatSettings> settingsSupplier, LongSupplier currentTimeMillis) {
        this(settingsSupplier, currentTimeMillis, null);
    }

    ProactiveChatService(
            Supplier<ProactiveChatSettings> settingsSupplier,
            LongSupplier currentTimeMillis,
            AssistantMemoryService memoryService
    ) {
        this.settingsSupplier = settingsSupplier;
        this.currentTimeMillis = currentTimeMillis;
        this.goalService = new ProactiveGoalService(memoryService);
        long now = currentTimeMillis.getAsLong();
        this.lastBotMessageMillis = new AtomicLong(now);
        this.lastPlayerMessageMillis = new AtomicLong(now);
    }

    /**
     * Returns whether the current unaddressed message is likely part of a player-to-player conversation.
     * This does not record the current message; callers that route it as ambient chat should subsequently call
     * {@link #onChat(Player, String, Supplier, TriggerConsumer)}, which records it exactly once.
     */
    public boolean isLikelyPlayerConversation(
            Player source,
            String message,
            Collection<? extends Player> onlinePlayers
    ) {
        ProactiveChatSettings.QuestionSettings questions = settingsSupplier.get().questions();
        long now = currentTimeMillis.getAsLong();
        boolean trackedConversation = socialGraph.isLikelyConversation(
                source,
                message,
                onlinePlayers,
                now,
                questions.conversationWindowMillis(),
                questions.minimumSpeakerAlternations()
        );
        if (trackedConversation || GeneralQuestionDetector.hasBroadcastCue(message)) {
            return trackedConversation;
        }
        return source != null
                && SocialConversationGraph.looksContextualReply(message)
                && socialGraph.hasStrongRecentConnection(
                        source.getUniqueId(), now, questions.socialGraphWindowMillis(), questions.strongPairScore()
                );
    }

    public void onChat(
            Player source,
            String message,
            Supplier<? extends Collection<? extends Player>> onlinePlayers,
            TriggerConsumer consumer
    ) {
        long now = currentTimeMillis.getAsLong();
        lastPlayerMessageMillis.set(now);
        ProactiveChatSettings settings = settingsSupplier.get();
        ProactiveChatSettings.QuestionSettings questions = settings.questions();
        Collection<? extends Player> players = onlinePlayers.get();
        boolean activeConversation = isLikelyPlayerConversation(source, message, players);
        InterventionDecision decision = ProactiveInterventionPolicy.evaluateQuestion(
                source, message, players, activeConversation, socialGraph, now, questions
        );
        SocialConversationGraph.ThreadView thread = socialGraph.threadView(
                source.getUniqueId(), now, questions.socialGraphWindowMillis()
        );
        java.util.Optional<ProactiveChatTrigger> goalTrigger = goalService.chatTrigger(
                source, message, thread, activeConversation
        );
        InterventionDecision goalDecision = goalTrigger
                .map(trigger -> ProactiveInterventionPolicy.evaluateGoal(
                        trigger.goal(), source, activeConversation, trigger.privateOnly(), socialGraph, now, questions
                ))
                .orElse(new InterventionDecision(CommunityGoal.SILENCE, 0, 1, 0, 0, -1, false));
        socialGraph.observe(source, message, players, now, questions.socialGraphWindowMillis());

        if (!settings.enabled()) {
            return;
        }
        if (goalTrigger.isPresent() && settings.goals().enabled(goalTrigger.get().goal()) && goalDecision.speak()
                && passesProbability(settings.goals().probability())
                && submitIfOffCooldown(source, goalTrigger.get(), goalDecision.goal(), now, settings, consumer)) {
            return;
        }
        if (decision.speak()
                && passesProbability(questions.probability())
                && submitIfOffCooldown(
                        source, ProactiveChatTrigger.question(message), decision.goal(), now, settings, consumer
                )) {
            return;
        }

        ProactiveChatSettings.CollectiveSettings collective = settings.collective();
        if (collective.enabled()
                && collectiveTracker.recordAndHasEnoughPlayers(source.getUniqueId(), message, now, collective)
                && passesProbability(collective.probability())
                && submitIfOffCooldown(
                        source,
                        ProactiveChatTrigger.collectiveReaction(collective.minimumDistinctPlayers(), message),
                        CommunityGoal.CELEBRATE,
                        now,
                        settings,
                        consumer
                )) {
            collectiveTracker.reset();
        }
    }

    public void recordPlayerMessage() {
        lastPlayerMessageMillis.set(currentTimeMillis.getAsLong());
    }

    public void onJoin(Player player, TriggerConsumer consumer) {
        long now = currentTimeMillis.getAsLong();
        ProactiveChatSettings settings = settingsSupplier.get();
        ProactiveChatSettings.JoinSettings join = settings.join();
        if (!settings.enabled() || !join.enabled() || !passesProbability(join.probability())
                || isWithin(lastJoinResponseMillis.get(), now, join.cooldownMillis())) {
            return;
        }
        if (submitIfOffCooldown(
                player, ProactiveChatTrigger.join(player.getName(), join.prompt()), CommunityGoal.WELCOME,
                now, settings, consumer
        )) {
            lastJoinResponseMillis.set(now);
        }
    }

    public void checkForIdleConversation(Collection<? extends Player> onlinePlayers, TriggerConsumer consumer) {
        long now = currentTimeMillis.getAsLong();
        ProactiveChatSettings settings = settingsSupplier.get();
        ProactiveChatSettings.IdleSettings idle = settings.idle();
        if (!settings.enabled() || !idle.enabled()
                || isWithin(lastIdleCheckMillis.get(), now, idle.checkIntervalMillis())) {
            return;
        }
        lastIdleCheckMillis.set(now);
        if (onlinePlayers.size() < idle.minimumOnlinePlayers()
                || now - lastBotMessageMillis.get() < idle.silenceMillis()
                || !passesProbability(idle.probability())) {
            return;
        }
        Player contextPlayer = onlinePlayers.stream().findFirst().orElse(null);
        if (contextPlayer == null) {
            return;
        }
        boolean hasRecentChat = now - lastPlayerMessageMillis.get() <= idle.recentChatWindowMillis();
        submitIfOffCooldown(
                contextPlayer,
                ProactiveChatTrigger.idleConversation(hasRecentChat),
                hasRecentChat ? CommunityGoal.SUPPORT_CONVERSATION : CommunityGoal.INFORM,
                now,
                settings,
                consumer
        );
    }

    public void checkForScheduledGoals(
            Collection<? extends Player> onlinePlayers,
            TriggerConsumer consumer
    ) {
        long now = currentTimeMillis.getAsLong();
        ProactiveChatSettings settings = settingsSupplier.get();
        if (!settings.enabled() || !settings.goals().enabled(CommunityGoal.FOLLOW_UP)
                || onlinePlayers == null || onlinePlayers.isEmpty()) {
            return;
        }
        for (Player player : onlinePlayers) {
            java.util.Optional<ProactiveChatTrigger> candidate = goalService.followUp(player, now);
            if (candidate.isEmpty()) {
                continue;
            }
            ProactiveChatTrigger trigger = candidate.get();
            InterventionDecision decision = ProactiveInterventionPolicy.evaluateGoal(
                    CommunityGoal.FOLLOW_UP, player, false, true, socialGraph, now, settings.questions()
            );
            if (decision.speak() && passesProbability(settings.goals().followUpProbability())
                    && submitIfOffCooldown(player, trigger, CommunityGoal.FOLLOW_UP, now, settings, consumer)) {
                return;
            }
        }
    }

    public void recordDeliveredGoal(Player player, ProactiveChatTrigger trigger) {
        if (trigger != null && trigger.goal() == CommunityGoal.FOLLOW_UP) {
            goalService.recordFollowUpDelivered(player, currentTimeMillis.getAsLong());
        }
    }

    public void recordBotResponse() {
        lastBotMessageMillis.set(currentTimeMillis.getAsLong());
    }

    public String responseVisibility() {
        return settingsSupplier.get().responseVisibility();
    }

    public SocialConversationGraph.ThreadView threadView(Player player) {
        if (player == null) {
            return SocialConversationGraph.ThreadView.empty();
        }
        ProactiveChatSettings.QuestionSettings questions = settingsSupplier.get().questions();
        return socialGraph.threadView(
                player.getUniqueId(), currentTimeMillis.getAsLong(), questions.socialGraphWindowMillis()
        );
    }

    private boolean submitIfOffCooldown(
            Player player,
            ProactiveChatTrigger trigger,
            CommunityGoal goal,
            long now,
            ProactiveChatSettings settings,
            TriggerConsumer consumer
    ) {
        if (isWithin(lastProactiveRequestMillis.get(), now, settings.cooldownMillis())
                || !consumer.submit(player, trigger)) {
            return false;
        }
        lastProactiveRequestMillis.set(now);
        if (player != null) {
            socialGraph.recordAilexIntervention(
                    player.getUniqueId(), goal, now, settings.questions().socialGraphWindowMillis()
            );
        }
        return true;
    }

    private boolean passesProbability(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    private boolean isWithin(long timestamp, long now, long durationMillis) {
        return timestamp != Long.MIN_VALUE && now - timestamp < durationMillis;
    }

    @FunctionalInterface
    public interface TriggerConsumer {
        boolean submit(Player contextPlayer, ProactiveChatTrigger trigger);
    }
}
