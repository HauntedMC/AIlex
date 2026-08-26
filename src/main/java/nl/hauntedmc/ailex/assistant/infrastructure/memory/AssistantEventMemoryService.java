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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Selective episodic-memory recorder. It records meaningful state transitions with short TTLs and deliberately
 * ignores high-volume events such as movement, block changes, damage ticks and ordinary chat.
 */
public final class AssistantEventMemoryService implements Listener {

    private static final Duration SESSION_TTL = Duration.ofHours(6);
    private static final Duration WORLD_TTL = Duration.ofHours(12);
    private static final Duration DEATH_TTL = Duration.ofDays(2);
    private static final Duration ADVANCEMENT_TTL = Duration.ofDays(30);
    private static final Duration RELATIONSHIP_TTL = Duration.ofDays(90);

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
        Set<String> effectiveTags = new java.util.HashSet<>(tags == null ? Set.of() : tags);
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

    /** Factual player↔NPC relationship state; no inferred mood, affinity or psychological profile is stored. */
    public void recordInteraction(UUID playerId, String npcId) {
        if (memory == null || playerId == null || npcId == null || npcId.isBlank()) {
            return;
        }
        int previous = memory.search(playerId, npcId, "interaction count", Set.of(MemoryKind.RELATIONSHIP), 8).stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER_NPC)
                .filter(record -> record.key().equals("interaction_count"))
                .findFirst()
                .map(MemoryRecord::value)
                .map(this::safeInteger)
                .orElse(0);
        memory.rememberTrusted(
                MemoryScope.PLAYER_NPC,
                playerId.toString(),
                npcId,
                MemoryKind.RELATIONSHIP,
                "interaction_count",
                String.valueOf(Math.min(previous + 1, 1_000_000)),
                1.0D,
                0.45D,
                "assistant-runtime",
                "accepted-chat",
                0L,
                RELATIONSHIP_TTL,
                Set.of("relationship", "interaction")
        );
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

    private String normalizeType(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }
}
