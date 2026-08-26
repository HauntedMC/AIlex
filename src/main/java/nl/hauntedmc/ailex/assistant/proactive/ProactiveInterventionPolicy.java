package nl.hauntedmc.ailex.assistant.proactive;

import org.bukkit.entity.Player;

import java.util.Collection;

/** Deterministic utility policy for deciding whether proactive participation creates net community value. */
public final class ProactiveInterventionPolicy {

    private ProactiveInterventionPolicy() {
    }

    public static boolean shouldAnswerQuestion(
            Player source,
            String message,
            Collection<? extends Player> onlinePlayers,
            boolean activePlayerConversation,
            SocialConversationGraph socialGraph,
            long now,
            ProactiveChatSettings.QuestionSettings settings
    ) {
        return evaluateQuestion(
                source, message, onlinePlayers, activePlayerConversation, socialGraph, now, settings
        ).speak();
    }

    public static InterventionDecision evaluateQuestion(
            Player source,
            String message,
            Collection<? extends Player> onlinePlayers,
            boolean activePlayerConversation,
            SocialConversationGraph socialGraph,
            long now,
            ProactiveChatSettings.QuestionSettings settings
    ) {
        if (source == null || settings == null || !settings.enabled()) {
            return silence();
        }
        boolean generalQuestion = GeneralQuestionDetector.isGeneralQuestion(
                message, source, onlinePlayers, activePlayerConversation
        );
        boolean broadcast = GeneralQuestionDetector.hasBroadcastCue(message);
        if (!generalQuestion && !broadcast) {
            return silence();
        }

        double strongestPair = socialGraph == null ? 0.0D : socialGraph.strongestRecentConnection(
                source.getUniqueId(), now, settings.socialGraphWindowMillis()
        );
        boolean contextual = SocialConversationGraph.looksContextualReply(message);
        double helpful = broadcast ? 0.97D : contextual ? 0.66D : 0.80D;
        double privateConversation = broadcast ? 0.04D
                : activePlayerConversation ? 0.95D
                : contextual && strongestPair >= settings.strongPairScore() ? 0.78D
                : Math.clamp(strongestPair / Math.max(1.0D, settings.strongPairScore()) * 0.42D, 0.0D, 0.65D);
        double error = serverSpecific(message) ? 0.18D : 0.10D;
        double repetition = socialGraph == null ? 0.0D : socialGraph.repetitionPenalty(
                source.getUniqueId(), now, settings.socialGraphWindowMillis()
        );
        double utility = helpful * settings.helpfulWeight()
                - privateConversation * settings.privacyCost()
                - error * settings.errorCost()
                - repetition * settings.repetitionCost();
        boolean speak = broadcast || utility > settings.utilityThreshold();
        CommunityGoal goal = speak
                ? broadcast || serverSpecific(message) ? CommunityGoal.INFORM : CommunityGoal.SUPPORT_CONVERSATION
                : CommunityGoal.SILENCE;
        return new InterventionDecision(
                goal, helpful, privateConversation, error, repetition, utility, speak
        );
    }

    private static boolean serverSpecific(String message) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return text.contains("/") || text.contains("haunted") || text.contains("server") || text.contains("rank")
                || text.contains("claim") || text.contains("vote") || text.contains("currency") || text.contains("event");
    }

    private static InterventionDecision silence() {
        return new InterventionDecision(CommunityGoal.SILENCE, 0.0D, 1.0D, 0.0D, 0.0D, -1.0D, false);
    }
}
