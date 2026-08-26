package nl.hauntedmc.ailex.assistant.proactive;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Tracks distinct recent players sharing a configured positive-chat term. */
final class CollectiveReactionTracker {

    private final Map<UUID, Long> recentPlayers = new HashMap<>();

    boolean recordAndHasEnoughPlayers(UUID playerId, String message, long now, ProactiveChatSettings.CollectiveSettings settings) {
        if (!containsConfiguredTerm(message, settings)) {
            return false;
        }
        recentPlayers.entrySet().removeIf(entry -> now - entry.getValue() > settings.windowMillis());
        recentPlayers.put(playerId, now);
        return recentPlayers.size() >= settings.minimumDistinctPlayers();
    }

    void reset() {
        recentPlayers.clear();
    }

    private boolean containsConfiguredTerm(String message, ProactiveChatSettings.CollectiveSettings settings) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        return settings.terms().stream()
                .filter(term -> term != null && !term.isBlank())
                .map(term -> term.trim().toLowerCase(Locale.ROOT))
                .anyMatch(term -> normalizedMessage.matches(".*(?<![\\p{L}\\p{N}_])"
                        + Pattern.quote(term) + "(?![\\p{L}\\p{N}_]).*"));
    }
}
