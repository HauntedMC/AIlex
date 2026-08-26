package nl.hauntedmc.ailex.assistant.proactive;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tiny temporal model used only to keep unaddressed AIlex replies out of player-to-player conversations.
 * It stores a short in-memory speaker window; no chat transcript is persisted by this component.
 */
final class ConversationParticipationTracker {

    private static final int MAX_MESSAGES = 48;
    private static final long CONTEXTUAL_REPLY_MAX_GAP_MILLIS = 20_000L;
    private static final int DIRECT_ADDRESS_HISTORY_MESSAGES = 8;
    private final Deque<Message> messages = new ArrayDeque<>();

    synchronized boolean isLikelyConversation(
            Player source,
            String currentMessage,
            Collection<? extends Player> onlinePlayers,
            long now,
            long windowMillis,
            int minimumAlternations
    ) {
        if (source == null || currentMessage == null) {
            return false;
        }
        prune(now, windowMillis);
        if (messages.isEmpty()) {
            return false;
        }
        UUID sourceId = source.getUniqueId();
        List<Message> recent = new ArrayList<>(messages);
        Message latestOther = null;
        for (int index = recent.size() - 1; index >= 0; index--) {
            Message candidate = recent.get(index);
            if (!candidate.playerId().equals(sourceId)) {
                latestOther = candidate;
                break;
            }
        }
        if (latestOther == null) {
            return false;
        }

        if (hasDirectAddressHistory(source, latestOther, recent, onlinePlayers)) {
            return true;
        }

        int transitions = 0;
        UUID previousSpeaker = null;
        for (Message message : recent) {
            if (!message.playerId().equals(sourceId) && !message.playerId().equals(latestOther.playerId())) {
                continue;
            }
            if (previousSpeaker != null && !previousSpeaker.equals(message.playerId())) {
                transitions++;
            }
            previousSpeaker = message.playerId();
        }
        if (previousSpeaker != null && !previousSpeaker.equals(sourceId)) {
            transitions++;
        }
        if (transitions >= Math.max(2, minimumAlternations)) {
            return true;
        }

        long gap = Math.max(0L, now - latestOther.timestampMillis());
        return gap <= CONTEXTUAL_REPLY_MAX_GAP_MILLIS && looksContextual(currentMessage);
    }

    synchronized void record(Player source, String message, long now, long windowMillis) {
        if (source == null || message == null || message.isBlank()) {
            return;
        }
        prune(now, windowMillis);
        messages.addLast(new Message(
                source.getUniqueId(),
                safeName(source.getName()),
                compact(message),
                now
        ));
        while (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }
    }

    private boolean hasDirectAddressHistory(
            Player source,
            Message latestOther,
            List<Message> recent,
            Collection<? extends Player> onlinePlayers
    ) {
        String sourceName = safeName(source.getName()).toLowerCase(Locale.ROOT);
        String otherName = latestOther.playerName().toLowerCase(Locale.ROOT);
        if (!sourceName.isBlank() && mentionsName(latestOther.text(), sourceName)) {
            return true;
        }
        if (recentSourceMessagesMention(recent, source.getUniqueId(), otherName)) {
            return true;
        }
        if (onlinePlayers == null) {
            return false;
        }
        for (Player player : onlinePlayers) {
            if (player == null || player.equals(source) || player.getName() == null) {
                continue;
            }
            String name = player.getName().toLowerCase(Locale.ROOT);
            if (recentSourceMessagesMention(recent, source.getUniqueId(), name)) {
                return true;
            }
        }
        return false;
    }

    private boolean recentSourceMessagesMention(List<Message> recent, UUID sourceId, String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        int checked = 0;
        for (int index = recent.size() - 1; index >= 0 && checked < DIRECT_ADDRESS_HISTORY_MESSAGES; index--) {
            Message message = recent.get(index);
            if (!message.playerId().equals(sourceId)) {
                continue;
            }
            checked++;
            if (mentionsName(message.text(), playerName)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksContextual(String message) {
        String normalized = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return normalized.matches(".*\\b(je|jij|jou|jouw|you|your)\\b.*")
                || normalized.startsWith("maar ")
                || normalized.startsWith("en ")
                || normalized.startsWith("dus ")
                || normalized.startsWith("waarom ")
                || normalized.startsWith("hoezo ")
                || normalized.startsWith("wat bedoel")
                || normalized.startsWith("welke dan")
                || normalized.startsWith("waar dan")
                || normalized.startsWith("but ")
                || normalized.startsWith("and ")
                || normalized.startsWith("so ")
                || normalized.startsWith("why ")
                || normalized.startsWith("what do you mean")
                || normalized.matches(".*\\b(dit|dat|daar|dan|deze|die|this|that|there|then)\\b.*");
    }

    private boolean mentionsName(String text, String name) {
        if (text == null || name == null || name.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        int index = normalized.indexOf(name);
        while (index >= 0) {
            int end = index + name.length();
            boolean before = index == 0 || !Character.isLetterOrDigit(normalized.charAt(index - 1));
            boolean after = end == normalized.length() || !Character.isLetterOrDigit(normalized.charAt(end));
            if (before && after) {
                return true;
            }
            index = normalized.indexOf(name, index + 1);
        }
        return false;
    }

    private void prune(long now, long windowMillis) {
        long cutoff = now - Math.max(5_000L, windowMillis);
        while (!messages.isEmpty() && messages.getFirst().timestampMillis() < cutoff) {
            messages.removeFirst();
        }
    }

    private String compact(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 239) + "…";
    }

    private String safeName(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= 32 ? normalized : normalized.substring(0, 32);
    }

    private record Message(UUID playerId, String playerName, String text, long timestampMillis) {
    }
}
