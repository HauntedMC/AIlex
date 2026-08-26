package nl.hauntedmc.ailex.assistant.proactive;

import org.bukkit.entity.Player;

import java.util.Collection;

/** Deterministic policy that permits proactive answers only when a question is plausibly public. */
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
        if (source == null || settings == null || !settings.enabled()) {
            return false;
        }
        if (!GeneralQuestionDetector.isGeneralQuestion(message, source, onlinePlayers, activePlayerConversation)) {
            return false;
        }
        if (GeneralQuestionDetector.hasBroadcastCue(message)) {
            return true;
        }
        if (activePlayerConversation) {
            return false;
        }
        boolean strongPair = socialGraph != null && socialGraph.hasStrongRecentConnection(
                source.getUniqueId(), now, settings.socialGraphWindowMillis(), settings.strongPairScore()
        );
        return !strongPair || !SocialConversationGraph.looksContextualReply(message);
    }
}
