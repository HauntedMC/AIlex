package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.AIlexPlugin;

import org.bukkit.GameMode;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Selective episodic-memory recorder. It records meaningful state transitions with bounded TTLs and deliberately ignores
 * high-volume events such as movement, block changes and damage ticks. Public chat is recorded only when an AIlex NPC is
 * explicitly addressed, which gives the NPC a bounded lived history without turning the whole server transcript into
 * durable memory.
 */
public final class AssistantEventMemoryService implements Listener {

    private static final Duration SESSION_TTL = Duration.ofHours(6);
    private static final Duration WORLD_TTL = Duration.ofHours(12);
    private static final Duration PUBLIC_CHAT_TTL = Duration.ofHours(12);
    private static final Duration DEATH_TTL = Duration.ofDays(2);
    private static final Duration ADVANCEMENT_TTL = Duration.ofDays(30);
    private static final Duration RELATIONSHIP_TTL = Duration.ofDays(365);
    private static final int MAX_PUBLIC_CHAT_CHARACTERS = 220;

    private final AssistantMemoryService memory;

    public AssistantEventMemoryService(AIlexPlugin plugin) {
        this(plugin, plugin.getAssistantMemoryService());
    }

    AssistantEventMemoryService(AIlexPlugin plugin, AssistantMemoryService memory) {
        this.memory = memory;
    }

    /** Records one integration-defined event without exposing repository internals to downstream features. */
    public MemoryRecord recordCustomEvent(
            String eventType,
            UUID playerId,
            String npcId,
            String summary,
            double salience,
            Duration ttl,
            Set<String> tags
    ) {
        if (memory == null || eventType == null || eventType.isBlank() || summary == null || summary.isBlank()) {
            return null;
        }
        long now = System.currentTimeMillis();
        String type = normalizeType(eventType);
        String player = playerId == null ? "" : playerId.toString();
        String npc = npcId == null ? "" : npcId.trim();
        Set<String> effectiveTags = new HashSet<>(tags == null ? Set.of() : tags);
        effectiveTags.add("event");
        effectiveTags.add(type);
        return memory.rememberTrusted(
                MemoryScope.EVENT,
                player,
                npc,
                MemoryKind.EVENT,
                type + '.' + now + '.' + UUID.randomUUID().toString().substring(0, 8),
                summary,
                1.0D,
                Math.clamp(salience, 0.0D, 1.0D),
                "event-listener",
                type,
                now,
                ttl == null ? Duration.ofDays(7) : ttl,
                Set.copyOf(effectiveTags)
        );
    }

    /**
     * Records the fact that this NPC witnessed a public player message addressed to it.
     *
     * <p>The event is NPC-owned rather than player-owned: another player may ask the same NPC what it publicly witnessed,
     * while private semantic memory about the speaker remains player-scoped. The stored value asserts only that the
     * speaker said the quoted text; it does not promote the payload itself to trusted server knowledge.</p>
     */
    public MemoryRecord recordObservedPublicChat(
            UUID speakerId,
            String speakerName,
            String npcId,
            String npcName,
            String message
    ) {
        if (memory == null || speakerId == null || npcId == null || npcId.isBlank() || message == null || message.isBlank()) {
            return null;
        }
        long now = System.currentTimeMillis();
        String safeSpeaker = compact(speakerName, 32);
        String safeNpc = compact(npcName, 32);
        String safeMessage = compact(message, MAX_PUBLIC_CHAT_CHARACTERS);
        String summary = safeSpeaker + " said to " + safeNpc + ": \"" + safeMessage + "\"";
        Set<String> tags = new HashSet<>();
        tags.add("event");
        tags.add("chat");
        tags.add("public-chat");
        tags.add("npc-observed");
        tags.add("speaker:" + normalizeType(safeSpeaker));
        return memory.rememberTrusted(
                MemoryScope.EVENT,
                "",
                npcId.trim(),
                MemoryKind.EVENT,
                "chat.public." + now + '.' + UUID.randomUUID().toString().substring(0, 8),
                summary,
                1.0D,
                0.78D,
                "observed-public-chat",
                speakerId.toString(),
                now,
                PUBLIC_CHAT_TTL,
                Set.copyOf(tags)
        );
    }

    /**
     * Factual player↔NPC continuity. Only timestamps/count/familiarity derived from the count are stored; this is not an
     * inferred friendship, mood or personality score.
     */
    public void recordInteraction(UUID playerId, String npcId) {
        if (memory == null || playerId == null || npcId == null || npcId.isBlank()) {
            return;
        }
        List<MemoryRecord> relationship = memory.search(
                playerId, npcId, "interaction count", Set.of(MemoryKind.RELATIONSHIP), 8
        ).stream().filter(record -> record.scope() == MemoryScope.PLAYER_NPC).toList();
        int previous = relationship.stream()
                .filter(record -> record.key().equals("interaction_count"))
                .findFirst()
                .map(MemoryRecord::value)
                .map(this::safeInteger)
                .orElse(0);
        long now = System.currentTimeMillis();
        long first = relationship.stream()
                .filter(record -> record.key().equals("first_interaction_at"))
                .findFirst()
                .map(MemoryRecord::value)
                .map(this::safeLong)
                .orElse(now);
        int count = Math.min(previous + 1, 1_000_000);

        rememberRelationship(playerId, npcId, "first_interaction_at", String.valueOf(first), 0.50D);
        rememberRelationship(playerId, npcId, "last_interaction_at", String.valueOf(now), 0.48D);
        rememberRelationship(playerId, npcId, "interaction_count", String.valueOf(count), 0.55D);
        rememberRelationship(playerId, npcId, "familiarity", familiarity(count), 0.50D);
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        recordPlayerEvent(
                player, "session.start", "Player joined the server", 0.20D, SESSION_TTL, Set.of("session", "join")
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        recordPlayerEvent(
                player, "session.end", "Player left the server", 0.15D, SESSION_TTL, Set.of("session", "quit")
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String summary = "Player moved from world " + event.getFrom().getName() + " to " + player.getWorld().getName();
        recordPlayerEvent(player, "world.change", summary, 0.35D, WORLD_TTL, Set.of("world"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChanged(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        GameMode mode = event.getNewGameMode();
        recordPlayerEvent(
                player,
                "gamemode.change",
                "Player changed game mode to " + mode.name().toLowerCase(Locale.ROOT),
                0.40D,
                WORLD_TTL,
                Set.of("gamemode")
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        recordCustomEvent(
                "player.death",
                player.getUniqueId(),
                "",
                "Player died in world " + player.getWorld().getName(),
                0.72D,
                DEATH_TTL,
                Set.of("death", "world:" + player.getWorld().getName().toLowerCase(Locale.ROOT))
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Advancement advancement = event.getAdvancement();
        recordCustomEvent(
                "advancement",
                player.getUniqueId(),
                "",
                "Player completed advancement " + advancement.getKey(),
                0.85D,
                ADVANCEMENT_TTL,
                Set.of("advancement", advancement.getKey().getKey().toLowerCase(Locale.ROOT))
        );
    }

    private void rememberRelationship(UUID playerId, String npcId, String key, String value, double salience) {
        memory.rememberTrusted(
                MemoryScope.PLAYER_NPC,
                playerId.toString(),
                npcId,
                MemoryKind.RELATIONSHIP,
                key,
                value,
                1.0D,
                salience,
                "assistant-runtime",
                "accepted-chat",
                System.currentTimeMillis(),
                RELATIONSHIP_TTL,
                Set.of("relationship", "interaction", key)
        );
    }

    private void recordPlayerEvent(
            Player player,
            String type,
            String summary,
            double salience,
            Duration ttl,
            Set<String> tags
    ) {
        if (player == null) {
            return;
        }
        recordCustomEvent(type, player.getUniqueId(), "", summary, salience, ttl, tags);
    }

    private int safeInteger(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long safeLong(String value) {
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String familiarity(int interactions) {
        if (interactions >= 100) {
            return "long_term_regular";
        }
        if (interactions >= 25) {
            return "regular";
        }
        if (interactions >= 5) {
            return "familiar";
        }
        return "acquainted";
    }

    private String compact(String value, int maximumCharacters) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maximumCharacters) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maximumCharacters - 1)) + "…";
    }

    private String normalizeType(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }
}
