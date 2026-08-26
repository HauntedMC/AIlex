package nl.hauntedmc.ailex.assistant.infrastructure.live;

import nl.hauntedmc.ailex.npc.NPC;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects small supplemental Paper facts that are expensive or too detailed for the baseline live snapshot.
 *
 * <p>The collector runs on the server thread and is deliberately message-selective. The returned metadata is still
 * filtered by {@code AssistantService}'s {@code RequiredContextPlanner} before it can enter a prompt, so this class
 * cannot broaden the model's live-data capabilities on its own.</p>
 */
public final class PaperLiveContextEnricher {

    private static final int MAX_METADATA_CHARACTERS = 1_800;

    private PaperLiveContextEnricher() {
    }

    public static String collect(Player player, NPC npc, String message) {
        if (player == null || message == null || message.isBlank()) {
            return "";
        }
        String text = message.toLowerCase(Locale.ROOT);
        List<String> metadata = new ArrayList<>();

        appendRequester(metadata, player, text);
        appendWorld(metadata, player, text);
        appendServer(metadata, text);
        appendNearbyEntities(metadata, player, text);
        appendNpc(metadata, npc, text);

        String result = String.join(" | ", metadata);
        return result.length() <= MAX_METADATA_CHARACTERS
                ? result : result.substring(0, MAX_METADATA_CHARACTERS);
    }

    private static void appendRequester(List<String> metadata, Player player, String text) {
        if (containsAny(text, "level", "xp", "experience", "ervaring")) {
            metadata.add("player_level=" + player.getLevel() + ",progress="
                    + String.format(Locale.ROOT, "%.0f%%", player.getExp() * 100.0F));
        }
        if (containsAny(text, "item", "hand", "holding", "vasthoud", "vast", "hold")) {
            metadata.add("player_main_hand=" + describeItem(player.getInventory().getItemInMainHand()));
        }
        if (containsAny(text, "effect", "potion", "buff", "debuff")) {
            String effects = player.getActivePotionEffects().stream()
                    .limit(5)
                    .map(effect -> effect.getType().getKey() + "_" + (effect.getAmplifier() + 1))
                    .collect(Collectors.joining(","));
            metadata.add("player_effects=" + (effects.isBlank() ? "none" : effects));
        }
        if (containsAny(text, "armor", "armour", "pantser")) {
            metadata.add("player_armor=" + describeArmor(player.getInventory().getArmorContents()));
        }
        if (containsAny(text, "ping", "latency")) {
            metadata.add("player_ping_ms=" + player.getPing());
        }
        if (containsAny(text, "playtime", "speeltijd", "played", "gespeeld")) {
            long playedTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            metadata.add("player_playtime=" + formatDuration(Duration.ofSeconds(playedTicks / 20L)));
        }
    }

    private static void appendWorld(List<String> metadata, Player player, String text) {
        if (!containsAny(text, "biome", "bioom", "facing", "richting", "direction", "welke kant", "difficulty",
                "moeilijkheid", "environment", "omgeving", "dimension", "dimensie", "overworld", "nether", " end",
                "light", "licht") || player.getWorld() == null) {
            return;
        }
        if (containsAny(text, "biome", "bioom")) {
            metadata.add("player_biome=" + player.getWorld().getBiome(player.getLocation()).getKey());
        }
        if (containsAny(text, "facing", "richting", "direction", "welke kant")) {
            metadata.add("player_facing=" + directionFromYaw(player.getLocation().getYaw()));
        }
        if (containsAny(text, "difficulty", "moeilijkheid")) {
            metadata.add("world_difficulty=" + player.getWorld().getDifficulty().name());
        }
        if (containsAny(text, "environment", "omgeving", "dimension", "dimensie", "overworld", "nether", " end")) {
            metadata.add("world_environment=" + player.getWorld().getEnvironment().name());
        }
        if (containsAny(text, "light", "licht")) {
            metadata.add("player_light=" + player.getLocation().getBlock().getLightLevel());
        }
    }

    private static void appendServer(List<String> metadata, String text) {
        if (containsAny(text, "versie", "version")) {
            metadata.add("server_minecraft_version=" + Bukkit.getMinecraftVersion());
        }
        if (containsAny(text, "tps", "mspt", "performance", "lag")) {
            metadata.add("server_tps=" + String.format(Locale.ROOT, "%.2f", Bukkit.getTPS()[0])
                    + ",mspt=" + String.format(Locale.ROOT, "%.2f", Bukkit.getAverageTickTime()));
        }
        if (text.contains("uptime")) {
            metadata.add("server_uptime=" + formatDuration(
                    Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime())
            ));
        }
    }

    private static void appendNearbyEntities(List<String> metadata, Player player, String text) {
        if (!containsAny(text, "dichtbij", "nearby", "around", "om me heen", "entities", "entity", "mobs", "mob")) {
            return;
        }
        Map<String, Long> entities = player.getNearbyEntities(24, 24, 24).stream()
                .filter(entity -> !(entity instanceof Player))
                .map(Entity::getType)
                .collect(Collectors.groupingBy(type -> type.getKey().toString(), Collectors.counting()));
        String compact = entities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(8)
                .map(entry -> entry.getKey() + "x" + entry.getValue())
                .collect(Collectors.joining(","));
        metadata.add("nearby_entities=" + (compact.isBlank() ? "none" : compact));
    }

    private static void appendNpc(List<String> metadata, NPC npc, String text) {
        if (npc == null || !containsAny(text, "jij", "jou", "jouw", "you", "your", "npc", "bot")) {
            return;
        }
        metadata.add("bot_id=" + npc.getId());
        if (npc.getMovementBehaviour() != null) {
            metadata.add("bot_movement=" + npc.getMovementBehaviour().getFriendlyName());
        }
        if (npc.getCurrentAction() != null) {
            metadata.add("bot_action=" + npc.getCurrentAction().getFriendlyName());
        }
    }

    private static String describeItem(ItemStack item) {
        if (item == null || isAir(item.getType())) {
            return "empty";
        }
        return item.getType().getKey() + "x" + item.getAmount();
    }

    private static String describeArmor(ItemStack[] armor) {
        List<String> pieces = new ArrayList<>();
        if (armor != null) {
            for (ItemStack item : armor) {
                if (item != null && !isAir(item.getType())) {
                    pieces.add(item.getType().getKey().toString());
                }
            }
        }
        return pieces.isEmpty() ? "none" : String.join(",", pieces);
    }

    private static boolean isAir(Material material) {
        return material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static String directionFromYaw(float yaw) {
        String[] directions = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};
        return directions[Math.floorMod(Math.round(yaw / 45.0F), directions.length)];
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        return seconds / 3600 + "h" + (seconds % 3600) / 60 + "m";
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
