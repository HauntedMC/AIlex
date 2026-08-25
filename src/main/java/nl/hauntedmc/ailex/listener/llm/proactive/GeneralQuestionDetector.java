package nl.hauntedmc.ailex.listener.llm.proactive;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/** Identifies questions addressed to the server rather than an individual online player. */
public final class GeneralQuestionDetector {

    private GeneralQuestionDetector() {
    }

    /**
     * Determines whether a question is general enough for a public bot response.
     *
     * @param message player chat message
     * @param source message sender
     * @param onlinePlayers currently online players
     * @return true when the question is not directed at another player
     */
    public static boolean isGeneralQuestion(String message, Player source, Collection<? extends Player> onlinePlayers) {
        if (message == null || !message.contains("?")) {
            return false;
        }
        String normalizedMessage = message.trim().toLowerCase(Locale.ROOT);
        if (normalizedMessage.isBlank() || normalizedMessage.startsWith("@")) {
            return false;
        }
        for (Player player : onlinePlayers) {
            if (player == null || player.equals(source) || player.getName() == null || player.getName().isBlank()) {
                continue;
            }
            String playerName = player.getName().toLowerCase(Locale.ROOT);
            if (startsWithAddress(normalizedMessage, playerName)
                    || normalizedMessage.contains("@" + playerName)
                    || normalizedMessage.matches(".*\\b(?:to|aan|voor)\\s+" + Pattern.quote(playerName) + "\\b.*")) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAddress(String message, String playerName) {
        return message.matches("^" + Pattern.quote(playerName) + "(?:\\s*[:,!?].*|\\s+.*)");
    }
}
