package nl.hauntedmc.ailex.assistant.infrastructure.live;

import nl.hauntedmc.ailex.npc.NPC;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects rich, player-helpful Paper state without exposing network addresses, plugin/config internals or private
 * information about other players. Collection happens on the server thread and remains query-selective for expensive
 * inventory/nearby/target scans.
 */
public final class PaperLiveContextEnricher {

    private static final int MAX_METADATA_CHARACTERS = 8_000;
    private static final int TARGET_DISTANCE = 16;

    private PaperLiveContextEnricher() {
    }

    public static String collect(Player player, NPC npc, String message) {
        if (player == null || message == null || message.isBlank()) {
            return "";
        }
        String text = message.toLowerCase(Locale.ROOT);
        if (!looksLikeLiveQuestion(text)) {
            return "";
        }
        List<String> metadata = new ArrayList<>();

        appendRequesterCore(metadata, player);
        appendWorldCore(metadata, player);

        if (inventorySignal(text)) {
            appendInventory(metadata, player);
        }
        if (targetSignal(text)) {
            appendTarget(metadata, player);
        }
        if (serverSignal(text)) {
            appendServer(metadata);
        }
        if (nearbySignal(text)) {
            appendNearbyEntities(metadata, player);
        }
        if (npcSignal(text)) {
            appendNpc(metadata, npc, player);
        }
        if (containsAny(text, "playtime", "speeltijd", "played", "gespeeld")) {
            long playedTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            metadata.add("player_playtime=" + formatDuration(Duration.ofSeconds(playedTicks / 20L)));
        }

        String result = String.join(" | ", metadata);
        return result.length() <= MAX_METADATA_CHARACTERS
                ? result : result.substring(0, MAX_METADATA_CHARACTERS - 1) + "…";
    }

    private static void appendRequesterCore(List<String> metadata, Player player) {
        metadata.add("player_gamemode=" + player.getGameMode().name().toLowerCase(Locale.ROOT));
        metadata.add("player_health=" + compactNumber(player.getHealth()) + "/" + compactNumber(player.getMaxHealth()));
        metadata.add("player_absorption=" + compactNumber(player.getAbsorptionAmount()));
        metadata.add("player_food=" + player.getFoodLevel() + ",saturation=" + compactNumber(player.getSaturation()));
        metadata.add("player_level=" + player.getLevel() + ",xp_progress="
                + String.format(Locale.ROOT, "%.0f%%", player.getExp() * 100.0F)
                + ",total_xp=" + player.getTotalExperience());
        metadata.add("player_air=" + player.getRemainingAir() + "/" + player.getMaximumAir());
        metadata.add("player_ping_ms=" + player.getPing());
        metadata.add("player_state=" + movementState(player));
        metadata.add("player_main_hand=" + describeItem(player.getInventory().getItemInMainHand()));
        metadata.add("player_off_hand=" + describeItem(player.getInventory().getItemInOffHand()));
        metadata.add("player_effects=" + describeEffects(player));
        if (player.getVehicle() != null) {
            metadata.add("player_vehicle=" + player.getVehicle().getType().getKey());
        }
        if (player.getFireTicks() > 0) {
            metadata.add("player_on_fire_ticks=" + player.getFireTicks());
        }
        if (player.getFreezeTicks() > 0) {
            metadata.add("player_freeze_ticks=" + player.getFreezeTicks());
        }
    }

    private static void appendWorldCore(List<String> metadata, Player player) {
        Block block = player.getLocation().getBlock();
        metadata.add("player_world=" + player.getWorld().getName());
        metadata.add("player_position=" + block.getX() + "," + block.getY() + "," + block.getZ());
        metadata.add("player_facing=" + directionFromYaw(player.getLocation().getYaw()));
        metadata.add("player_biome=" + player.getWorld().getBiome(player.getLocation()).getKey());
        metadata.add("world_environment=" + player.getWorld().getEnvironment().name().toLowerCase(Locale.ROOT));
        metadata.add("world_difficulty=" + player.getWorld().getDifficulty().name().toLowerCase(Locale.ROOT));
        metadata.add("world_time_ticks=" + player.getWorld().getTime() + ",period=" + timePeriod(player.getWorld().getTime()));
        metadata.add("weather=" + weather(player));
        metadata.add("player_light=" + block.getLightLevel() + ",sky=" + block.getLightFromSky()
                + ",block=" + block.getLightFromBlocks());
        metadata.add("player_block=" + block.getType().getKey());
        metadata.add("block_below=" + block.getRelative(0, -1, 0).getType().getKey());
        metadata.add("world_sea_level=" + player.getWorld().getSeaLevel());
        metadata.add("world_height=" + player.getWorld().getMinHeight() + ".." + player.getWorld().getMaxHeight());
    }

    private static void appendInventory(List<String> metadata, Player player) {
        metadata.add("player_selected_hotbar_slot=" + player.getInventory().getHeldItemSlot());
        metadata.add("player_armor=" + describeArmor(player.getInventory().getArmorContents()));

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || isAir(item.getType())) {
                continue;
            }
            counts.merge(item.getType().getKey().toString(), item.getAmount(), Integer::sum);
        }
        String summary = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(24)
                .map(entry -> entry.getKey() + "x" + entry.getValue())
                .collect(Collectors.joining(","));
        metadata.add("player_inventory_summary=" + (summary.isBlank() ? "empty" : summary));
    }

    private static void appendTarget(List<String> metadata, Player player) {
        Block targetBlock = player.getTargetBlockExact(TARGET_DISTANCE);
        if (targetBlock == null) {
            metadata.add("target_block=none");
        } else {
            metadata.add("target_block=" + targetBlock.getType().getKey() + "@"
                    + targetBlock.getX() + "," + targetBlock.getY() + "," + targetBlock.getZ());
        }
        Entity targetEntity = player.getTargetEntity(TARGET_DISTANCE);
        if (targetEntity == null) {
            metadata.add("target_entity=none");
        } else if (targetEntity instanceof Player) {
            metadata.add("target_entity=player");
        } else {
            metadata.add("target_entity=" + targetEntity.getType().getKey());
        }
    }

    private static void appendServer(List<String> metadata) {
        metadata.add("server_minecraft_version=" + Bukkit.getMinecraftVersion());
        metadata.add("server_online_players=" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers());
        double[] tps = Bukkit.getTPS();
        metadata.add("server_tps_1m=" + String.format(Locale.ROOT, "%.2f", tps.length == 0 ? 20.0D : tps[0]));
        metadata.add("server_mspt=" + String.format(Locale.ROOT, "%.2f", Bukkit.getAverageTickTime()));
        metadata.add("server_uptime=" + formatDuration(Duration.ofMillis(
                ManagementFactory.getRuntimeMXBean().getUptime()
        )));
    }

    private static void appendNearbyEntities(List<String> metadata, Player player) {
        List<Entity> nearby = player.getNearbyEntities(24, 24, 24);
        long nearbyPlayers = nearby.stream().filter(Player.class::isInstance).count();
        metadata.add("nearby_player_count=" + nearbyPlayers);
        Map<String, Long> entities = nearby.stream()
                .filter(entity -> !(entity instanceof Player))
                .map(Entity::getType)
                .collect(Collectors.groupingBy(type -> type.getKey().toString(), Collectors.counting()));
        String compact = entities.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(18)
                .map(entry -> entry.getKey() + "x" + entry.getValue())
                .collect(Collectors.joining(","));
        metadata.add("nearby_entities=" + (compact.isBlank() ? "none" : compact));
    }

    private static void appendNpc(List<String> metadata, NPC npc, Player player) {
        if (npc == null) {
            return;
        }
        metadata.add("bot_id=" + npc.getId());
        metadata.add("bot_name=" + npc.getName());
        if (npc.isSpawned() && npc.getLastKnownLocation() != null) {
            metadata.add("bot_position=" + String.format(Locale.ROOT, "%.0f,%.0f,%.0f",
                    npc.getLastKnownLocation().getX(), npc.getLastKnownLocation().getY(),
                    npc.getLastKnownLocation().getZ()));
            if (npc.getLastKnownLocation().getWorld() != null
                    && npc.getLastKnownLocation().getWorld().equals(player.getWorld())) {
                metadata.add("bot_distance_to_player=" + String.format(Locale.ROOT, "%.1f",
                        npc.getLastKnownLocation().distance(player.getLocation())));
            }
        }
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
        StringBuilder value = new StringBuilder(item.getType().getKey().toString()).append('x').append(item.getAmount());
        if (!item.getEnchantments().isEmpty()) {
            String enchants = item.getEnchantments().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(enchantment -> enchantment.getKey().toString())))
                    .limit(6)
                    .map(entry -> entry.getKey().getKey() + "_" + entry.getValue())
                    .collect(Collectors.joining(","));
            value.append("[").append(enchants).append(']');
        }
        return value.toString();
    }

    private static String describeArmor(ItemStack[] armor) {
        List<String> pieces = new ArrayList<>();
        if (armor != null) {
            Arrays.stream(armor)
                    .filter(item -> item != null && !isAir(item.getType()))
                    .map(PaperLiveContextEnricher::describeItem)
                    .forEach(pieces::add);
        }
        return pieces.isEmpty() ? "none" : String.join(",", pieces);
    }

    private static String describeEffects(Player player) {
        String effects = player.getActivePotionEffects().stream()
                .sorted(java.util.Comparator.comparing(effect -> effect.getType().getKey().toString()))
                .limit(12)
                .map(PaperLiveContextEnricher::describeEffect)
                .collect(Collectors.joining(","));
        return effects.isBlank() ? "none" : effects;
    }

    private static String describeEffect(PotionEffect effect) {
        return effect.getType().getKey() + "_" + (effect.getAmplifier() + 1) + "_" + effect.getDuration() + "t";
    }

    private static String movementState(Player player) {
        List<String> states = new ArrayList<>();
        if (player.isFlying()) {
            states.add("flying");
        }
        if (player.isGliding()) {
            states.add("gliding");
        }
        if (player.isSwimming()) {
            states.add("swimming");
        }
        if (player.isSprinting()) {
            states.add("sprinting");
        }
        if (player.isSneaking()) {
            states.add("sneaking");
        }
        return states.isEmpty() ? "normal" : String.join(",", states);
    }

    private static String weather(Player player) {
        if (player.getWorld().isThundering()) {
            return "thunder";
        }
        return player.getWorld().hasStorm() ? "rain" : "clear";
    }

    private static String timePeriod(long time) {
        long normalized = Math.floorMod(time, 24_000L);
        if (normalized < 1_000L) {
            return "sunrise";
        }
        if (normalized < 12_000L) {
            return "day";
        }
        if (normalized < 13_000L) {
            return "sunset";
        }
        return "night";
    }

    private static boolean isAir(Material material) {
        return material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static String directionFromYaw(float yaw) {
        String[] directions = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};
        return directions[Math.floorMod(Math.round(yaw / 45.0F), directions.length)];
    }

    private static String compactNumber(double value) {
        return Math.rint(value) == value ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        return hours + "h" + minutes + "m";
    }

    private static boolean looksLikeLiveQuestion(String text) {
        return containsAny(text,
                "waar", "where", "hier", "here", "nu", "now", "biome", "bioom", "position", "positie",
                "location", "locatie", "coord", "health", "leven", "food", "honger", "gamemode", "level", "xp",
                "item", "hand", "inventory", "inventaris", "armor", "pantser", "effect", "ping", "playtime",
                "speeltijd", "weather", "weer", "time", "tijd", "light", "licht", "difficulty", "dimension",
                "richting", "facing", "block", "blok", "looking", "kijk", "target", "nearby", "dichtbij", "mob",
                "entity", "online", "tps", "mspt", "performance", "lag", "uptime", "version", "versie", "jij",
                "jou", "you", "your"
        );
    }

    private static boolean inventorySignal(String text) {
        return containsAny(text, "inventory", "inventaris", "items", "equipment", "uitrusting", "armor", "armour",
                "pantser", "offhand", "hotbar", "slot");
    }

    private static boolean targetSignal(String text) {
        return containsAny(text, "kijk", "looking", "target", "blok", "block", "voor me", "in front");
    }

    private static boolean serverSignal(String text) {
        return containsAny(text, "online", "tps", "mspt", "performance", "lag", "uptime", "versie", "version",
                "server");
    }

    private static boolean nearbySignal(String text) {
        return containsAny(text, "dichtbij", "nearby", "around", "near me", "om me heen", "entities", "entity", "mobs",
                "mob");
    }

    private static boolean npcSignal(String text) {
        return containsAny(text, "jij", "jou", "jouw", "you", "your", "npc", "bot", "waar sta je", "where are you",
                "wat doe je", "what are you doing");
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
