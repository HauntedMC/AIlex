package nl.hauntedmc.ailex.assistant.proactive;

import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantRelationshipMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryRecord;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryScope;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.RelationshipProfile;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic goal recognizer for proactive community behavior. It never profiles friendship/personality and never
 * reads another player's private memory. Persistent follow-ups are derived only from the requester's explicit goals.
 */
public final class ProactiveGoalService {

    private static final long FOLLOW_UP_MIN_AGE = Duration.ofHours(6).toMillis();
    private static final long FOLLOW_UP_MAX_AGE = Duration.ofDays(30).toMillis();
    private static final long FOLLOW_UP_COOLDOWN = Duration.ofDays(3).toMillis();

    private final AssistantMemoryService memory;
    private final AssistantRelationshipMemoryService relationships;
    private final Map<UUID, Long> lastFollowUpByPlayer = new ConcurrentHashMap<>();

    public ProactiveGoalService(AssistantMemoryService memory) {
        this.memory = memory;
        this.relationships = new AssistantRelationshipMemoryService(memory);
    }

    public Optional<ProactiveChatTrigger> chatTrigger(
            Player source,
            String message,
            SocialConversationGraph.ThreadView thread,
            boolean activePlayerConversation
    ) {
        if (source == null || message == null || message.isBlank()) {
            return Optional.empty();
        }
        String text = normalize(message);
        if (activePlayerConversation && !GeneralQuestionDetector.hasBroadcastCue(text)) {
            return Optional.empty();
        }
        if (connectCue(text)) {
            return Optional.of(ProactiveChatTrigger.connect(message));
        }
        if (defuseCue(text) && thread != null && thread.participants().size() >= 2) {
            return Optional.of(ProactiveChatTrigger.defuse(message));
        }
        if (celebrationCue(text)) {
            return Optional.of(ProactiveChatTrigger.celebrate(source.getName(), message));
        }
        if (helpCue(text)) {
            RelationshipProfile profile = relationships.profile(source.getUniqueId(), "0");
            if (!profile.knownPlayer() || profile.interactionCount() < 5) {
                return Optional.of(ProactiveChatTrigger.newPlayerHelp(source.getName(), message));
            }
        }
        return Optional.empty();
    }

    /** Returns at most one non-sensitive explicit player goal old enough for a useful private follow-up. */
    public Optional<ProactiveChatTrigger> followUp(Player player, long now) {
        if (memory == null || player == null || player.getUniqueId() == null) {
            return Optional.empty();
        }
        long previous = lastFollowUpByPlayer.getOrDefault(player.getUniqueId(), Long.MIN_VALUE);
        if (previous != Long.MIN_VALUE && now - previous < FOLLOW_UP_COOLDOWN) {
            return Optional.empty();
        }
        String playerId = player.getUniqueId().toString();
        Optional<MemoryRecord> goal = memory.activeSnapshot().stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.subjectId().equals(playerId))
                .filter(record -> record.kind() == MemoryKind.GOAL)
                .filter(record -> !record.tags().contains("internal"))
                .filter(record -> now - record.lastConfirmed() >= FOLLOW_UP_MIN_AGE)
                .filter(record -> now - record.lastConfirmed() <= FOLLOW_UP_MAX_AGE)
                .sorted(Comparator.comparingDouble(MemoryRecord::salience).reversed()
                        .thenComparing(Comparator.comparingLong(MemoryRecord::lastConfirmed).reversed()))
                .findFirst();
        if (goal.isEmpty()) {
            return Optional.empty();
        }
        MemoryRecord record = goal.get();
        String summary = record.key() + '=' + record.value();
        return Optional.of(ProactiveChatTrigger.followUp(player.getName(), summary));
    }

    public void recordFollowUpDelivered(Player player, long now) {
        if (player != null && player.getUniqueId() != null) {
            lastFollowUpByPlayer.put(player.getUniqueId(), now);
        }
    }

    private boolean helpCue(String text) {
        return text.endsWith("?") && (containsAny(text,
                "hoe werkt", "waar vind", "wat moet ik", "hoe begin", "kan iemand helpen", "help", "how do i",
                "where do i", "where can i", "what should i", "how can i", "can someone help", "anyone help"));
    }

    private boolean connectCue(String text) {
        return containsAny(text,
                "wie wil mee", "iemand mee", "wie wil bouwen", "wie wil farmen", "wie wil spelen", "zoek iemand om",
                "looking for someone", "anyone want to", "who wants to", "looking for players", "need someone to");
    }

    private boolean celebrationCue(String text) {
        return containsAny(text,
                "eindelijk gehaald", "eindelijk gelukt", "ik heb hem", "ik heb het gehaald", "rank up", "level up",
                "finally got", "finally made", "i did it", "just got", "just completed", "achievement", "advancement");
    }

    private boolean defuseCue(String text) {
        if (containsAny(text, "kill yourself", "kys", "dox", "adres", "ip address", "bedreig", "threat")) {
            return false;
        }
        return containsAny(text,
                "hou op met ruzie", "stop met ruzie", "doe rustig", "rustig jongens", "geen ruzie", "calm down",
                "stop arguing", "chill guys", "no need to argue", "keep it civil");
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
