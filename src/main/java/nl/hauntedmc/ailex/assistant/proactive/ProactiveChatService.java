package nl.hauntedmc.ailex.assistant.proactive;

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
    private final ConversationParticipationTracker conversationTracker = new ConversationParticipationTracker();
    private final AtomicLong lastBotMessageMillis;
    private final AtomicLong lastPlayerMessageMillis;
    private final AtomicLong lastProactiveRequestMillis = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong lastJoinResponseMillis = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong lastIdleCheckMillis = new AtomicLong(Long.MIN_VALUE);

    public ProactiveChatService(Supplier<ProactiveChatSettings> settingsSupplier) {
        this(settingsSupplier, System::currentTimeMillis);
    }

    ProactiveChatService(Supplier<ProactiveChatSettings> settingsSupplier, LongSupplier currentTimeMillis) {
        this.settingsSupplier = settingsSupplier;
        this.currentTimeMillis = currentTimeMillis;
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
        return conversationTracker.isLikelyConversation(
                source,
                message,
                onlinePlayers,
                currentTimeMillis.getAsLong(),
                questions.conversationWindowMillis(),
                questions.minimumSpeakerAlternations()
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
        boolean activeConversation = conversationTracker.isLikelyConversation(
                source,
                message,
                players,
                now,
                questions.conversationWindowMillis(),
                questions.minimumSpeakerAlternations()
        );
        conversationTracker.record(source, message, now, questions.conversationWindowMillis());

        if (!settings.enabled()) {
            return;
        }
        if (questions.enabled()
                && GeneralQuestionDetector.isGeneralQuestion(message, source, players, activeConversation)
                && passesProbability(questions.probability())
                && submitIfOffCooldown(source, ProactiveChatTrigger.question(message), now, settings, consumer)) {
            return;
        }

        ProactiveChatSettings.CollectiveSettings collective = settings.collective();
        if (collective.enabled()
                && collectiveTracker.recordAndHasEnoughPlayers(source.getUniqueId(), message, now, collective)
                && passesProbability(collective.probability())
                && submitIfOffCooldown(
                        source,
                        ProactiveChatTrigger.collectiveReaction(collective.minimumDistinctPlayers(), message),
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
        if (submitIfOffCooldown(player, ProactiveChatTrigger.join(player.getName(), join.prompt()), now, settings, consumer)) {
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
        submitIfOffCooldown(contextPlayer, ProactiveChatTrigger.idleConversation(hasRecentChat), now, settings, consumer);
    }

    public void recordBotResponse() {
        lastBotMessageMillis.set(currentTimeMillis.getAsLong());
    }

    public String responseVisibility() {
        return settingsSupplier.get().responseVisibility();
    }

    private boolean submitIfOffCooldown(
            Player player,
            ProactiveChatTrigger trigger,
            long now,
            ProactiveChatSettings settings,
            TriggerConsumer consumer
    ) {
        if (isWithin(lastProactiveRequestMillis.get(), now, settings.cooldownMillis())
                || !consumer.submit(player, trigger)) {
            return false;
        }
        lastProactiveRequestMillis.set(now);
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
