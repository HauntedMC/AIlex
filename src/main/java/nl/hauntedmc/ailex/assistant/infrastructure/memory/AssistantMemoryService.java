package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatic, bounded assistant memory. It saves only explicit, non-sensitive preferences and
 * durable shared and player facts; it never saves a chat transcript.
 */
public final class AssistantMemoryService {

    private static final String PREFERENCES_FILE_NAME = "assistant-memory.yml";
    private static final String LONG_TERM_FILE_NAME = "assistant-long-term-memory.yml";
    private static final int MAX_FACT_LENGTH = 180;
    private static final int MAX_CONTEXT_CHARACTERS = 1400;
    private static final int MAX_RECENT_TOPIC_TERMS = 96;
    private static final List<String> SENSITIVE_TERMS = List.of(
            "password", "wachtwoord", "ip", "adres", "address", "email", "e-mail", "telefoon", "phone",
            "discord token", "token", "coordinaten", "coördinaten", "coordinates", "report", "rapport",
            "ban", "sanction", "straf"
    );

    private final JavaPlugin plugin;
    private final File preferencesFile;
    private final File longTermFile;
    private final Map<UUID, PreferenceMemory> preferences = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerFacts> playerFacts = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> recentTopicTerms = new ConcurrentHashMap<>();
    private final List<String> sharedFacts = new ArrayList<>();

    public AssistantMemoryService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.preferencesFile = new File(plugin.getDataFolder(), PREFERENCES_FILE_NAME);
        this.longTermFile = new File(plugin.getDataFolder(), LONG_TERM_FILE_NAME);
        loadPreferences();
        loadLongTermMemory();
    }

    /** Memory is on for every player whenever the server-wide feature is enabled. */
    public boolean isEnabled(UUID playerId) {
        return memoryFeatureEnabled();
    }

    /** Reloads persisted automatic memory. */
    public synchronized void reload() {
        preferences.clear();
        playerFacts.clear();
        sharedFacts.clear();
        recentTopicTerms.clear();
        loadPreferences();
        loadLongTermMemory();
    }

    /**
     * Saves a model-suggested candidate only when it is explicit in the player's current message.
     * Candidates are {@code preference:key=value}, {@code player:short fact}, or {@code shared:short fact}.
     */
    public synchronized void remember(UUID playerId, String playerName, String candidate, String playerMessage) {
        remember(playerId, playerName, candidate, playerMessage, true);
    }

    /**
     * Tracks concise topic terms in memory only for the current server session. This helps confirm a
     * harmless preference after a player brings it up repeatedly; no message text is persisted here.
     */
    public synchronized void observe(UUID playerId, String playerMessage) {
        if (!memoryFeatureEnabled() || playerId == null) {
            return;
        }
        List<String> terms = significantWords(playerMessage).stream().distinct().toList();
        if (terms.isEmpty()) {
            return;
        }
        Map<String, Integer> observed = recentTopicTerms.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        if (observed.size() + terms.size() > MAX_RECENT_TOPIC_TERMS) {
            observed.clear();
        }
        terms.forEach(term -> observed.merge(term, 1, (oldValue, ignored) -> Math.min(oldValue + 1, 3)));
    }

    /** Saves a candidate, allowing shared memory only for a caller authorized by server configuration. */
    public synchronized void remember(
            UUID playerId, String playerName, String candidate, String playerMessage, boolean canWriteSharedMemory
    ) {
        if (!memoryFeatureEnabled() || candidate == null || candidate.isBlank()) {
            return;
        }
        String normalizedCandidate = candidate.trim();
        if (normalizedCandidate.regionMatches(true, 0, "preference:", 0, "preference:".length())) {
            rememberPreference(playerId, normalizedCandidate.substring("preference:".length()), playerMessage);
        } else if (normalizedCandidate.regionMatches(true, 0, "player:", 0, "player:".length())) {
            rememberPlayerFact(playerId, playerName, normalizedCandidate.substring("player:".length()), playerMessage);
        } else if (canWriteSharedMemory
                && normalizedCandidate.regionMatches(true, 0, "shared:", 0, "shared:".length())) {
            rememberSharedFact(normalizedCandidate.substring("shared:".length()), playerMessage);
        }
    }

    /** Returns concise server and player facts plus saved preferences for the current player. */
    public String summary(UUID playerId) {
        if (!memoryFeatureEnabled()) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        PreferenceMemory preference = preferences.get(playerId);
        if (preference != null && !preference.expired(retentionDays())) {
            String values = preference.summary();
            if (!values.isBlank()) {
                summary.append("Player preferences: ").append(values);
            }
        }
        appendFacts(summary, "Shared server memory", sharedFacts);
        PlayerFacts facts = playerFacts.get(playerId);
        if (facts != null) {
            appendFacts(summary, "Saved player facts", facts.facts());
        }
        return limit(summary.toString());
    }

    private void rememberPreference(UUID playerId, String candidate, String playerMessage) {
        int separator = candidate.indexOf('=');
        if (separator < 1 || separator == candidate.length() - 1) {
            return;
        }
        String key = candidate.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String value = candidate.substring(separator + 1).trim().toLowerCase(Locale.ROOT);
        if (value.length() > 32 || !appearsExplicitly(key, value, playerMessage)) {
            return;
        }
        PreferenceMemory old = preferences.getOrDefault(playerId, PreferenceMemory.empty());
        PreferenceMemory updated = switch (key) {
            case "language" -> AssistantSettings.from(plugin.getConfig()).languageAllowed(value)
                    ? old.withLanguage(value) : null;
            case "answer_length" -> old.withAnswerLength(value);
            case "tone" -> old.withTone(value);
            case "preferred_gamemode" -> old.withGamemode(value);
            default -> null;
        };
        if (updated != null) {
            preferences.put(playerId, updated);
            savePreferences();
        }
    }

    private void rememberPlayerFact(UUID playerId, String playerName, String candidate, String playerMessage) {
        String fact = normalizeFact(candidate);
        if (!isSafeExplicitFact(playerId, fact, playerMessage, true)) {
            return;
        }
        PlayerFacts previous = playerFacts.getOrDefault(playerId, new PlayerFacts(playerName, List.of()));
        List<String> updatedFacts = new ArrayList<>(previous.facts());
        updatedFacts.removeIf(existing -> existing.equalsIgnoreCase(fact));
        updatedFacts.add(fact);
        if (updatedFacts.size() > maxPlayerFacts()) {
            updatedFacts = updatedFacts.subList(updatedFacts.size() - maxPlayerFacts(), updatedFacts.size());
        }
        playerFacts.put(playerId, new PlayerFacts(safePlayerName(playerName), List.copyOf(updatedFacts)));
        saveLongTermMemory();
    }

    private void rememberSharedFact(String candidate, String playerMessage) {
        String fact = normalizeFact(candidate);
        if (!isSafeExplicitFact(null, fact, playerMessage, false)) {
            return;
        }
        sharedFacts.removeIf(existing -> existing.equalsIgnoreCase(fact));
        sharedFacts.add(fact);
        if (sharedFacts.size() > maxSharedFacts()) {
            sharedFacts.subList(0, sharedFacts.size() - maxSharedFacts()).clear();
        }
        saveLongTermMemory();
    }

    private boolean isSafeExplicitFact(UUID playerId, String fact, String message, boolean personalFact) {
        if (fact.isBlank() || fact.length() > MAX_FACT_LENGTH || containsSensitiveTerm(fact)) {
            return false;
        }
        List<String> factWords = significantWords(fact);
        List<String> messageWords = significantWords(message);
        long overlap = factWords.stream().filter(messageWords::contains).distinct().count();
        if (overlap >= 2 || (factWords.size() == 1 && factWords.getFirst().length() >= 8 && overlap == 1)) {
            return true;
        }
        return personalFact && hasDistinctiveOverlap(factWords, messageWords)
                && (hasPersonalInterestSignal(message) || hasRepeatedTopic(playerId, factWords, messageWords));
    }

    private boolean hasDistinctiveOverlap(List<String> factWords, List<String> messageWords) {
        return factWords.stream().anyMatch(word -> word.length() >= 4 && messageWords.contains(word));
    }

    private boolean hasPersonalInterestSignal(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("ik hou van") || normalized.contains("ik vind") || normalized.contains("ik speel graag")
                || normalized.contains("mijn favoriete") || normalized.contains("ik ben fan")
                || normalized.contains("i like") || normalized.contains("i love") || normalized.contains("i prefer")
                || normalized.contains("my favorite") || normalized.contains("i am a fan");
    }

    private boolean hasRepeatedTopic(UUID playerId, List<String> factWords, List<String> messageWords) {
        if (playerId == null) {
            return false;
        }
        Map<String, Integer> observed = recentTopicTerms.get(playerId);
        return observed != null && factWords.stream().filter(word -> word.length() >= 4 && messageWords.contains(word))
                .anyMatch(word -> observed.getOrDefault(word, 0) >= 2);
    }

    private String normalizeFact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private boolean containsSensitiveTerm(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return SENSITIVE_TERMS.stream().anyMatch(term -> containsTerm(normalized, term));
    }

    private boolean containsTerm(String text, String term) {
        int index = text.indexOf(term);
        while (index >= 0) {
            int end = index + term.length();
            boolean validBefore = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
            boolean validAfter = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (validBefore && validAfter) {
                return true;
            }
            index = text.indexOf(term, index + term.length());
        }
        return false;
    }

    private List<String> significantWords(String value) {
        return java.util.Arrays.stream((value == null ? "" : value.toLowerCase(Locale.ROOT))
                        .split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= 3)
                .filter(word -> !List.of("and", "dat", "een", "het", "ik", "mijn", "the", "van", "voor")
                        .contains(word))
                .toList();
    }

    private void appendFacts(StringBuilder output, String heading, List<String> facts) {
        if (facts == null || facts.isEmpty()) {
            return;
        }
        if (!output.isEmpty()) {
            output.append("\n");
        }
        output.append(heading).append(": ").append(String.join(" | ", facts));
    }

    private String limit(String value) {
        return value.length() <= MAX_CONTEXT_CHARACTERS ? value
                : value.substring(0, MAX_CONTEXT_CHARACTERS - 1) + "…";
    }

    private boolean appearsExplicitly(String key, String value, String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
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

    private synchronized void loadPreferences() {
        if (!preferencesFile.isFile()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(preferencesFile);
        ConfigurationSection players = configuration.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "players." + key;
                PreferenceMemory memory = new PreferenceMemory(
                        configuration.getString(path + ".language", ""),
                        configuration.getString(path + ".answer_length", ""),
                        configuration.getString(path + ".tone", ""),
                        configuration.getString(path + ".preferred_gamemode", ""),
                        configuration.getLong(path + ".updated_at", 0L)
                );
                if (!memory.expired(retentionDays())) {
                    preferences.put(id, memory);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed operator edits rather than stopping the assistant at startup.
            }
        }
    }

    private synchronized void loadLongTermMemory() {
        if (!longTermFile.isFile()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(longTermFile);
        sharedFacts.clear();
        List<String> loadedSharedFacts = configuration.getStringList("shared_facts");
        if (loadedSharedFacts.isEmpty()) {
            // Migrate the old schema once. Subsequent automated saves use shared_facts only.
            loadedSharedFacts = configuration.getStringList("server_facts");
        }
        loadedSharedFacts.stream().map(this::normalizeFact)
                .filter(fact -> !fact.isBlank() && fact.length() <= MAX_FACT_LENGTH && !containsSensitiveTerm(fact))
                .limit(maxSharedFacts()).forEach(sharedFacts::add);
        ConfigurationSection players = configuration.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "players." + key;
                List<String> facts = configuration.getStringList(path + ".facts").stream().map(this::normalizeFact)
                        .filter(fact -> !fact.isBlank() && fact.length() <= MAX_FACT_LENGTH && !containsSensitiveTerm(fact))
                        .distinct().limit(maxPlayerFacts()).toList();
                playerFacts.put(id, new PlayerFacts(safePlayerName(configuration.getString(path + ".name", "")), facts));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed operator edits rather than stopping the assistant at startup.
            }
        }
    }

    private synchronized void savePreferences() {
        YamlConfiguration configuration = new YamlConfiguration();
        preferences.forEach((id, memory) -> {
            if (memory.expired(retentionDays())) {
                return;
            }
            String path = "players." + id;
            configuration.set(path + ".language", memory.language());
            configuration.set(path + ".answer_length", memory.answerLength());
            configuration.set(path + ".tone", memory.tone());
            configuration.set(path + ".preferred_gamemode", memory.preferredGamemode());
            configuration.set(path + ".updated_at", memory.updatedAt());
        });
        save(configuration, preferencesFile);
    }

    private synchronized void saveLongTermMemory() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("shared_facts", List.copyOf(sharedFacts));
        playerFacts.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString())).forEach(entry -> {
            String path = "players." + entry.getKey();
            configuration.set(path + ".name", entry.getValue().name());
            configuration.set(path + ".facts", entry.getValue().facts());
        });
        save(configuration, longTermFile);
    }

    private void save(YamlConfiguration configuration, File target) {
        Path temporaryFile = null;
        try {
            Path targetPath = target.toPath();
            Path parentDirectory = targetPath.getParent();
            if (parentDirectory == null) {
                throw new IOException("Assistant memory file has no parent directory: " + target);
            }
            Files.createDirectories(parentDirectory);
            temporaryFile = Files.createTempFile(parentDirectory, target.getName(), ".tmp");
            configuration.save(temporaryFile.toFile());
            try {
                Files.move(temporaryFile, targetPath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not persist assistant memory to " + target.getName() + ": "
                    + exception.getMessage());
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // A later successful write will clean up any stale temporary file.
                }
            }
        }
    }

    private String safePlayerName(String value) {
        String normalized = normalizeFact(value);
        return normalized.length() <= 32 ? normalized : normalized.substring(0, 32);
    }

    private boolean memoryFeatureEnabled() {
        FileConfiguration config = plugin.getConfig();
        return config == null || config.getBoolean("openai.assistant.memory.enabled", true);
    }

    private int retentionDays() {
        FileConfiguration config = plugin.getConfig();
        return config == null ? 90 : Math.clamp(config.getInt("openai.assistant.memory.retention_days", 90), 1, 365);
    }

    private int maxSharedFacts() {
        FileConfiguration config = plugin.getConfig();
        return config == null ? 128 : Math.clamp(config.getInt("openai.assistant.memory.max_shared_facts", 128), 1, 512);
    }

    private int maxPlayerFacts() {
        FileConfiguration config = plugin.getConfig();
        return config == null ? 24 : Math.clamp(config.getInt("openai.assistant.memory.max_player_facts", 24), 1, 128);
    }

    private record PlayerFacts(String name, List<String> facts) {
    }

    private record PreferenceMemory(
            String language, String answerLength, String tone, String preferredGamemode, long updatedAt
    ) {
        private static PreferenceMemory empty() {
            return new PreferenceMemory("", "", "", "", 0L);
        }

        private PreferenceMemory withLanguage(String value) {
            return new PreferenceMemory(value, answerLength, tone, preferredGamemode, now());
        }

        private PreferenceMemory withAnswerLength(String value) {
            return valid(value, "short", "normal", "detailed")
                    ? new PreferenceMemory(language, value, tone, preferredGamemode, now()) : null;
        }

        private PreferenceMemory withTone(String value) {
            return valid(value, "casual", "neutral", "formal")
                    ? new PreferenceMemory(language, answerLength, value, preferredGamemode, now()) : null;
        }

        private PreferenceMemory withGamemode(String value) {
            return valid(value, "survival", "creative", "minigames")
                    ? new PreferenceMemory(language, answerLength, tone, value, now()) : null;
        }

        private String summary() {
            List<String> values = new ArrayList<>();
            append(values, "language", language);
            append(values, "answer_length", answerLength);
            append(values, "tone", tone);
            append(values, "preferred_gamemode", preferredGamemode);
            return String.join(", ", values);
        }

        private boolean expired(int retentionDays) {
            return updatedAt > 0L && Instant.ofEpochMilli(updatedAt).plus(retentionDays, ChronoUnit.DAYS)
                    .isBefore(Instant.now());
        }

        private static void append(List<String> output, String key, String value) {
            if (value != null && !value.isBlank()) {
                output.add(key + '=' + value);
            }
        }

        private static boolean valid(String value, String... values) {
            return java.util.Arrays.asList(values).contains(value);
        }

        private static long now() {
            return Instant.now().toEpochMilli();
        }
    }
}
