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
        double utility = utility(helpful, privateConversation, error, repetition, settings);
        boolean speak = broadcast || utility > settings.utilityThreshold();
        CommunityGoal goal = speak
                ? broadcast || serverSpecific(message) ? CommunityGoal.INFORM : CommunityGoal.SUPPORT_CONVERSATION
                : CommunityGoal.SILENCE;
        return new InterventionDecision(goal, helpful, privateConversation, error, repetition, utility, speak);
    }

    /** Utility evaluation for non-question community goals. The trigger has already been produced by deterministic cues. */
    public static InterventionDecision evaluateGoal(
            CommunityGoal requestedGoal,
            Player source,
            boolean activePlayerConversation,
            boolean privateOnly,
            SocialConversationGraph socialGraph,
            long now,
            ProactiveChatSettings.QuestionSettings settings
    ) {
        if (requestedGoal == null || requestedGoal == CommunityGoal.SILENCE || source == null || settings == null) {
            return silence();
        }
        double helpful = switch (requestedGoal) {
            case HELP_NEW_PLAYER -> 0.94D;
            case WELCOME -> 0.72D;
            case CELEBRATE -> 0.82D;
            case CONNECT -> 0.78D;
            case DEFUSE -> 0.70D;
            case FOLLOW_UP -> 0.76D;
            case INFORM -> 0.82D;
            case SUPPORT_CONVERSATION -> 0.68D;
            case SILENCE -> 0.0D;
        };
        double privacy = privateOnly ? 0.02D : activePlayerConversation ? 0.88D : switch (requestedGoal) {
            case CONNECT, CELEBRATE, WELCOME, HELP_NEW_PLAYER, INFORM -> 0.08D;
            case DEFUSE -> 0.18D;
            case FOLLOW_UP -> 0.95D;
            case SUPPORT_CONVERSATION -> 0.30D;
            case SILENCE -> 1.0D;
        };
        double error = switch (requestedGoal) {
            case DEFUSE -> 0.22D;
            case FOLLOW_UP -> 0.14D;
            case INFORM, HELP_NEW_PLAYER -> 0.18D;
            default -> 0.08D;
        };
        double repetition = socialGraph == null ? 0.0D : socialGraph.repetitionPenalty(
                source.getUniqueId(), now, settings.socialGraphWindowMillis()
        );
        double score = utility(helpful, privacy, error, repetition, settings);
        boolean speak = privateOnly ? score > settings.utilityThreshold() - 0.10D : score > settings.utilityThreshold();
        return new InterventionDecision(
                speak ? requestedGoal : CommunityGoal.SILENCE,
                helpful, privacy, error, repetition, score, speak
        );
    }

    private static double utility(
            double helpful,
            double privacy,
            double error,
            double repetition,
            ProactiveChatSettings.QuestionSettings settings
    ) {
        return helpful * settings.helpfulWeight()
                - privacy * settings.privacyCost()
                - error * settings.errorCost()
                - repetition * settings.repetitionCost();
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
