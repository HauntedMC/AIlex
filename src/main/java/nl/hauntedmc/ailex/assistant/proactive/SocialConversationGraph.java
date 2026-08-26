package nl.hauntedmc.ailex.assistant.proactive;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Short-lived conversation/thread graph used only to decide whether AIlex should stay out of player-to-player chat.
 * It combines decaying pair edges with a bounded speaker window. Nothing is persisted and no inferred friendship,
 * psychological profile or durable social score is created.
 */
public final class SocialConversationGraph {

    private static final double DIRECT_ADDRESS_WEIGHT = 3.0D;
    private static final double CONTEXTUAL_REPLY_WEIGHT = 1.5D;
    private static final double ALTERNATION_WEIGHT = 0.75D;
    private static final int MAX_MESSAGES = 48;
    private static final int DIRECT_ADDRESS_HISTORY_MESSAGES = 8;
    private static final long CONTEXTUAL_REPLY_MAX_GAP_MILLIS = 20_000L;

    private final Map<Pair, Edge> edges = new ConcurrentHashMap<>();
    private final Deque<Message> messages = new ArrayDeque<>();
    private volatile UUID lastSpeaker;
    private volatile long lastMessageMillis = Long.MIN_VALUE;

    /** Evaluates the current unrecorded message against the recent transient conversation thread. */
    public synchronized boolean isLikelyConversation(
            Player source,
            String currentMessage,
            Collection<? extends Player> onlinePlayers,
            long now,
            long conversationWindowMillis,
            int minimumAlternations
    ) {
        if (source == null || currentMessage == null || source.getUniqueId() == null) {
            return false;
        }
        pruneMessages(now, conversationWindowMillis);
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
        return gap <= CONTEXTUAL_REPLY_MAX_GAP_MILLIS && looksContextualReply(currentMessage);
    }

    public synchronized void observe(
            Player source,
            String message,
            Collection<? extends Player> onlinePlayers,
            long now,
            long windowMillis
    ) {
        if (source == null || source.getUniqueId() == null || message == null || message.isBlank()) {
            return;
        }
        long effectiveWindow = Math.max(1_000L, windowMillis);
        pruneEdges(now, effectiveWindow);
        UUID sourceId = source.getUniqueId();
        Player addressed = addressedPlayer(source, message, onlinePlayers);
        if (addressed != null && addressed.getUniqueId() != null) {
            reinforce(sourceId, addressed.getUniqueId(), DIRECT_ADDRESS_WEIGHT, now, effectiveWindow);
        } else if (lastSpeaker != null && !lastSpeaker.equals(sourceId)
                && lastMessageMillis != Long.MIN_VALUE && now - lastMessageMillis <= effectiveWindow) {
            reinforce(
                    sourceId,
                    lastSpeaker,
                    looksContextualReply(message) ? CONTEXTUAL_REPLY_WEIGHT : ALTERNATION_WEIGHT,
                    now,
                    effectiveWindow
            );
        }
        lastSpeaker = sourceId;
        lastMessageMillis = now;
        messages.addLast(new Message(sourceId, safeName(source.getName()), compact(message), now));
        while (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }
    }

    public double strongestRecentConnection(UUID sourceId, long now, long windowMillis) {
        if (sourceId == null) {
            return 0.0D;
        }
        long effectiveWindow = Math.max(1_000L, windowMillis);
        pruneEdges(now, effectiveWindow);
        double strongest = 0.0D;
        for (Map.Entry<Pair, Edge> entry : edges.entrySet()) {
            if (!entry.getKey().contains(sourceId)) {
                continue;
            }
            strongest = Math.max(strongest, decayedWeight(entry.getValue(), now, effectiveWindow));
        }
        return strongest;
    }

    public boolean hasStrongRecentConnection(UUID sourceId, long now, long windowMillis, double threshold) {
        return strongestRecentConnection(sourceId, now, windowMillis) >= Math.max(0.1D, threshold);
    }

    static boolean looksContextualReply(String message) {
        String normalized = clean(message).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.matches("^(ja|nee|ok|oke|oké|thanks|dankje|thx|sure|yes|no|nah|yep|nope)\\b.*")
                || normalized.matches("^(waarom|hoezo|wat dan|welke dan|waar dan|en waarom|maar waarom|why|how come|what then|which one|where then)\\??$")
                || normalized.matches(".*\\b(jij|jou|jouw|je|u|you|your|yours|daar|dat|die|deze|it|that|there)\\b.*");
    }

    int edgeCount() {
        return edges.size();
    }

    synchronized int recentMessageCount() {
        return messages.size();
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
            if (recentSourceMessagesMention(
                    recent, source.getUniqueId(), player.getName().toLowerCase(Locale.ROOT)
            )) {
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

    private Player addressedPlayer(
            Player source,
            String message,
            Collection<? extends Player> onlinePlayers
    ) {
        if (onlinePlayers == null || message == null) {
            return null;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        for (Player candidate : onlinePlayers) {
            if (candidate == null || candidate.equals(source) || candidate.getName() == null
                    || candidate.getName().isBlank()) {
                continue;
            }
            String name = candidate.getName().toLowerCase(Locale.ROOT);
            if (normalized.contains("@" + name)
                    || normalized.matches("^" + Pattern.quote(name) + "(?:\\s*[:,!?].*|\\s+.*)")
                    || normalized.matches(".*\\b(?:to|aan|voor)\\s+" + Pattern.quote(name) + "\\b.*")) {
                return candidate;
            }
        }
        return null;
    }

    private void reinforce(UUID left, UUID right, double amount, long now, long windowMillis) {
        if (Objects.equals(left, right)) {
            return;
        }
        Pair pair = Pair.of(left, right);
        edges.compute(pair, (ignored, existing) -> {
            double current = existing == null ? 0.0D : decayedWeight(existing, now, windowMillis);
            return new Edge(Math.min(12.0D, current + amount), now);
        });
    }

    private void pruneEdges(long now, long windowMillis) {
        edges.entrySet().removeIf(entry -> now - entry.getValue().lastInteractionMillis() > windowMillis * 2L);
        if (lastMessageMillis != Long.MIN_VALUE && now - lastMessageMillis > windowMillis) {
            lastSpeaker = null;
            lastMessageMillis = Long.MIN_VALUE;
        }
    }

    private void pruneMessages(long now, long windowMillis) {
        long cutoff = now - Math.max(5_000L, windowMillis);
        while (!messages.isEmpty() && messages.getFirst().timestampMillis() < cutoff) {
            messages.removeFirst();
        }
    }

    private double decayedWeight(Edge edge, long now, long windowMillis) {
        if (edge == null) {
            return 0.0D;
        }
        long age = Math.max(0L, now - edge.lastInteractionMillis());
        if (age >= windowMillis * 2L) {
            return 0.0D;
        }
        double decay = Math.exp(-Math.log(2.0D) * age / windowMillis);
        return edge.weight() * decay;
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

    private static String compact(String value) {
        String normalized = clean(value);
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 239) + "…";
    }

    private static String safeName(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= 32 ? normalized : normalized.substring(0, 32);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private record Message(UUID playerId, String playerName, String text, long timestampMillis) {
    }

    private record Edge(double weight, long lastInteractionMillis) {
    }

    private record Pair(UUID left, UUID right) {
        static Pair of(UUID first, UUID second) {
            return first.compareTo(second) <= 0 ? new Pair(first, second) : new Pair(second, first);
        }

        boolean contains(UUID id) {
            return left.equals(id) || right.equals(id);
        }
    }
}
