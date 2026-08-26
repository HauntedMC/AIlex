package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Opt-in, allowlisted preference memory. Chat transcripts are never stored here. */
public final class AssistantMemoryService {

    private static final String FILE_NAME = "assistant-memory.yml";
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PreferenceMemory> memories = new ConcurrentHashMap<>();

    public AssistantMemoryService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        load();
    }

    public boolean isEnabled(UUID playerId) {
        PreferenceMemory memory = memories.get(playerId);
        return memoryFeatureEnabled() && memory != null && memory.enabled() && !memory.expired(retentionDays());
    }

    public synchronized void setEnabled(UUID playerId, boolean enabled) {
        if (!memoryFeatureEnabled()) {
            return;
        }
        PreferenceMemory old = memories.getOrDefault(playerId, PreferenceMemory.empty());
        memories.put(playerId, old.withEnabled(enabled));
        save();
    }

    public synchronized void forget(UUID playerId) {
        memories.remove(playerId);
        save();
    }

    /**
     * Stores one model-suggested preference only when it is allowlisted and explicitly appears in
     * the player's current message. This prevents memory from becoming a transcript or inference store.
     */
    public synchronized void remember(UUID playerId, String candidate, String playerMessage) {
        remember(playerId, candidate, playerMessage, true);
    }

    /**
     * Stores an allowlisted preference. When opt-in is disabled by configuration, the first explicit
     * preference statement creates an enabled memory record instead of silently discarding it.
     */
    public synchronized void remember(UUID playerId, String candidate, String playerMessage, boolean optInRequired) {
        if ((!optInRequired && !memoryFeatureEnabled()) || (optInRequired && !isEnabled(playerId)) || candidate == null) {
            return;
        }
        int separator = candidate.indexOf('=');
        if (separator < 1 || separator == candidate.length() - 1) {
            return;
        }
        String key = candidate.substring(0, separator).trim().toLowerCase(java.util.Locale.ROOT);
        String value = candidate.substring(separator + 1).trim().toLowerCase(java.util.Locale.ROOT);
        if (value.length() > 32 || !appearsExplicitly(key, value, playerMessage)) {
            return;
        }
        PreferenceMemory old = memories.getOrDefault(playerId,
                optInRequired ? PreferenceMemory.empty() : PreferenceMemory.enabledEmpty());
        PreferenceMemory updated = switch (key) {
            case "language" -> AssistantSettings.from(plugin.getConfig()).languageAllowed(value)
                    ? SetPreference.language(old, value) : null;
            case "answer_length" -> SetPreference.answerLength(old, value);
            case "tone" -> SetPreference.tone(old, value);
            case "preferred_gamemode" -> SetPreference.gamemode(old, value);
            default -> null;
        };
        if (updated != null) {
            memories.put(playerId, updated);
            save();
        }
    }

    /** Returns only safe explicit preferences for model context. */
    public String summary(UUID playerId) {
        PreferenceMemory memory = memories.get(playerId);
        if (!memoryFeatureEnabled() || memory == null || !memory.enabled() || memory.expired(retentionDays())) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        append(summary, "language", memory.language());
        append(summary, "answer_length", memory.answerLength());
        append(summary, "tone", memory.tone());
        append(summary, "preferred_gamemode", memory.preferredGamemode());
        return summary.toString();
    }

    private void append(StringBuilder output, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!output.isEmpty()) {
            output.append(", ");
        }
        output.append(key).append('=').append(value);
    }

    private boolean appearsExplicitly(String key, String value, String message) {
        String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if ("language".equals(key)) {
            return switch (value) {
                case "nl" -> containsWord(normalized, "nl") || normalized.contains("nederlands")
                        || normalized.contains("dutch");
                case "en" -> normalized.contains("english") || normalized.contains("engels")
                        || normalized.contains("in en");
                default -> false;
            };
        }
        return containsWord(normalized, value);
    }

    private boolean containsWord(String text, String value) {
        int index = text.indexOf(value);
        while (index >= 0) {
            int end = index + value.length();
            boolean validBefore = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
            boolean validAfter = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (validBefore && validAfter) {
                return true;
            }
            index = text.indexOf(value, index + value.length());
        }
        return false;
    }

    private synchronized void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = configuration.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "players." + key;
                PreferenceMemory memory = new PreferenceMemory(
                        configuration.getBoolean(path + ".enabled", false),
                        configuration.getString(path + ".language", ""),
                        configuration.getString(path + ".answer_length", ""),
                        configuration.getString(path + ".tone", ""),
                        configuration.getString(path + ".preferred_gamemode", ""),
                        configuration.getLong(path + ".updated_at", 0L)
                );
                if (!memory.expired(retentionDays())) {
                    memories.put(id, memory);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed operator edits rather than stopping the assistant at startup.
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        memories.forEach((id, memory) -> {
            if (memory.expired(retentionDays())) {
                return;
            }
            String path = "players." + id;
            configuration.set(path + ".enabled", memory.enabled());
            configuration.set(path + ".language", memory.language());
            configuration.set(path + ".answer_length", memory.answerLength());
            configuration.set(path + ".tone", memory.tone());
            configuration.set(path + ".preferred_gamemode", memory.preferredGamemode());
            configuration.set(path + ".updated_at", memory.updatedAt());
        });
        try {
            configuration.save(file);
        } catch (IOException ignored) {
            // The assistant remains usable if a local preference write fails.
        }
    }

    private record PreferenceMemory(
            boolean enabled,
            String language,
            String answerLength,
            String tone,
            String preferredGamemode,
            long updatedAt
    ) {
        private static PreferenceMemory empty() {
            return new PreferenceMemory(false, "", "", "", "", 0L);
        }

        private static PreferenceMemory enabledEmpty() {
            return new PreferenceMemory(true, "", "", "", "", 0L);
        }

        private PreferenceMemory withEnabled(boolean value) {
            return new PreferenceMemory(value, language, answerLength, tone, preferredGamemode,
                    Instant.now().toEpochMilli());
        }

        private boolean expired(int retentionDays) {
            return updatedAt > 0L && Instant.ofEpochMilli(updatedAt).plus(retentionDays, ChronoUnit.DAYS)
                    .isBefore(Instant.now());
        }
    }

    private static final class SetPreference {
        private SetPreference() {
        }

        private static PreferenceMemory language(PreferenceMemory memory, String value) {
            return ("nl".equals(value) || "en".equals(value)) ? copy(memory, value, memory.answerLength(), memory.tone(),
                    memory.preferredGamemode()) : null;
        }

        private static PreferenceMemory answerLength(PreferenceMemory memory, String value) {
            return ("short".equals(value) || "normal".equals(value) || "detailed".equals(value))
                    ? copy(memory, memory.language(), value, memory.tone(), memory.preferredGamemode()) : null;
        }

        private static PreferenceMemory tone(PreferenceMemory memory, String value) {
            return ("casual".equals(value) || "neutral".equals(value) || "formal".equals(value))
                    ? copy(memory, memory.language(), memory.answerLength(), value, memory.preferredGamemode()) : null;
        }

        private static PreferenceMemory gamemode(PreferenceMemory memory, String value) {
            return ("survival".equals(value) || "creative".equals(value) || "skyblock".equals(value)
                    || "skywars".equals(value) || "kitpvp".equals(value))
                    ? copy(memory, memory.language(), memory.answerLength(), memory.tone(), value) : null;
        }

        private static PreferenceMemory copy(PreferenceMemory memory, String language, String answerLength, String tone,
                                             String gamemode) {
            return new PreferenceMemory(true, language, answerLength, tone, gamemode, Instant.now().toEpochMilli());
        }
    }

    private boolean memoryFeatureEnabled() {
        FileConfiguration config = plugin.getConfig();
        return config == null || config.getBoolean("openai.assistant.memory.enabled", true);
    }

    private int retentionDays() {
        FileConfiguration config = plugin.getConfig();
        return config == null ? 90 : Math.clamp(config.getInt("openai.assistant.memory.retention_days", 90), 1, 365);
    }
}
