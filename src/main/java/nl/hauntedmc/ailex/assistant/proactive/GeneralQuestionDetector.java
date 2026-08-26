package nl.hauntedmc.ailex.assistant.proactive;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/** Identifies questions that are plausibly addressed to the public server chat rather than another player. */
public final class GeneralQuestionDetector {

    private GeneralQuestionDetector() {
    }

    public static boolean isGeneralQuestion(String message, Player source, Collection<? extends Player> onlinePlayers) {
        return isGeneralQuestion(message, source, onlinePlayers, false);
    }

    public static boolean isGeneralQuestion(
            String message,
            Player source,
            Collection<? extends Player> onlinePlayers,
            boolean activePlayerConversation
    ) {
        if (message == null || !message.contains("?")) {
            return false;
        }
        String normalizedMessage = message.trim().toLowerCase(Locale.ROOT);
        if (normalizedMessage.isBlank() || normalizedMessage.startsWith("@")) {
            return false;
        }
        if (onlinePlayers != null) {
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
        }
        if (activePlayerConversation && !hasBroadcastCue(normalizedMessage)) {
            return false;
        }
        return hasBroadcastCue(normalizedMessage) || looksSelfContained(normalizedMessage);
    }

    static boolean hasBroadcastCue(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.matches(".*\\b(weet iemand|kan iemand|wil iemand|iemand enig idee|wie weet|voor iedereen|algemene vraag)\\b.*")
                || normalized.matches(".*\\b(anyone know|does anyone|can anyone|can someone|does somebody|anybody know|general question)\\b.*");
    }

    private static boolean looksSelfContained(String message) {
        if (message.length() < 4) {
            return false;
        }
        if (message.matches("^(waarom|hoezo|wat dan|welke dan|waar dan|en waarom|maar waarom|why|how come|what then|which one|where then)\\??$")) {
            return false;
        }
        return message.matches("^(hoe|waar|wat|welk|welke|wanneer|waarom|wie|kan|kun|is|zijn|heeft|heb|mag|moet)\\b.*")
                || message.matches("^(how|where|what|which|when|why|who|can|could|is|are|does|do|has|have|should)\\b.*")
                || message.matches(".*\\b(minecraft|hauntedmc|server|survival|creative|minigames|command|commando|rank|claim|vote|stem)\\b.*");
    }

    private static boolean startsWithAddress(String message, String playerName) {
        return message.matches("^" + Pattern.quote(playerName) + "(?:\\s*[:,!?].*|\\s+.*)");
    }
}
