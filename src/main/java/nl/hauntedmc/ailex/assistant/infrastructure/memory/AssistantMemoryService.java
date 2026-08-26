package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Typed Memory V2 facade. Reads are served from a hot active-record map while durable writes are serialized to
 * SQLite/WAL on a dedicated writer. The legacy YAML files are imported once and remain untouched for rollback.
 */
public final class AssistantMemoryService implements AutoCloseable {

    private static final String PREFERENCES_FILE_NAME = "assistant-memory.yml";
    private static final String LONG_TERM_FILE_NAME = "assistant-long-term-memory.yml";
    private static final String DATABASE_FILE_NAME = "assistant-memory.db";
    private static final String MIGRATION_MARKER = ".assistant-memory-v2-migrated";
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
    private final File migrationMarker;
    private final MemoryRepository repository;
    private final Map<String, MemoryRecord> activeRecords = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> recentTopicTerms = new ConcurrentHashMap<>();
    private final ExecutorService writer;
    private volatile boolean closed;

    public AssistantMemoryService(JavaPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        this.preferencesFile = new File(dataFolder, PREFERENCES_FILE_NAME);
        this.longTermFile = new File(dataFolder, LONG_TERM_FILE_NAME);
        this.migrationMarker = new File(dataFolder, MIGRATION_MARKER);
        this.repository = createRepository(dataFolder);
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AIlex-MemoryWriter");
            thread.setDaemon(true);
            return thread;
        });
        loadFromRepository();
        migrateLegacyMemoryOnce();
        writer.execute(() -> repository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(7)).toEpochMilli()));
    }

    /** Memory is available for every player whenever the server-wide feature is enabled. */
    public boolean isEnabled(UUID playerId) {
        return memoryFeatureEnabled();
    }

    /** Reloads active durable state without replacing the repository or losing queued writes. */
    public synchronized void reload() {
        flush();
        activeRecords.clear();
        recentTopicTerms.clear();
        loadFromRepository();
    }

    /** Tracks repeated topic terms in session memory only; raw chat is never persisted by this service. */
    public void observe(UUID playerId, String playerMessage) {
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

    /** Records an unambiguous first-person output-language preference before generation starts. */
    public void rememberExplicitLanguagePreference(UUID playerId, String playerMessage) {
        if (!memoryFeatureEnabled() || playerId == null || playerMessage == null) {
            return;
        }
        String normalized = playerMessage.toLowerCase(Locale.ROOT);
        if (!isFirstPersonLanguagePreference(normalized)) {
            return;
        }
        String language = explicitLanguage(normalized);
        if (!language.isBlank()) {
            rememberPreference(playerId, "language=" + language, playerMessage, "player-explicit");
        }
    }

    /** Returns the current saved language when it is still supported by active routing configuration. */
    public String preferredLanguage(UUID playerId) {
        if (playerId == null) {
            return "";
        }
        MemoryRecord record = activeRecords.get(identity(
                MemoryScope.PLAYER, playerId.toString(), "", MemoryKind.PREFERENCE, "language"
        ));
        if (record == null || !record.activeAt(System.currentTimeMillis())) {
            return "";
        }
        return AssistantSettings.from(plugin.getConfig()).languageAllowed(record.value()) ? record.value() : "";
    }

    /** Backward-compatible candidate entrypoint; shared writes are allowed for trusted callers. */
    public void remember(UUID playerId, String playerName, String candidate, String playerMessage) {
        remember(playerId, playerName, candidate, playerMessage, true);
    }

    /**
     * Saves a model candidate only when supported by the player's current message. Legacy candidate syntax remains
     * supported: preference:key=value, player:fact, shared:fact.
     */
    public synchronized void remember(
            UUID playerId, String playerName, String candidate, String playerMessage, boolean canWriteSharedMemory
    ) {
        if (!memoryFeatureEnabled() || playerId == null || candidate == null || candidate.isBlank()) {
            return;
        }
        String normalizedCandidate = candidate.trim();
        if (startsWith(normalizedCandidate, "preference:")) {
            rememberPreference(playerId, normalizedCandidate.substring("preference:".length()), playerMessage,
                    "model-candidate");
        } else if (startsWith(normalizedCandidate, "player:")) {
            rememberPlayerFact(playerId, playerName, normalizedCandidate.substring("player:".length()), playerMessage);
        } else if (canWriteSharedMemory && startsWith(normalizedCandidate, "shared:")) {
            rememberSharedFact(playerName, normalizedCandidate.substring("shared:".length()), playerMessage);
        }
    }

    /**
     * Stores trusted typed runtime knowledge such as an event or relationship update. This entrypoint is intentionally
     * separate from model candidates: callers must already be trusted AIlex code, not player-controlled text parsers.
     */
    public synchronized MemoryRecord rememberTrusted(
            MemoryScope scope,
            String subjectId,
            String relationId,
            MemoryKind kind,
            String key,
            String value,
            double confidence,
            double salience,
            String sourceType,
            String sourceId,
            long occurredAt,
            Duration ttl,
            Set<String> tags
    ) {
        if (!memoryFeatureEnabled() || key == null || key.isBlank() || value == null || value.isBlank()) {
            return null;
        }
        String safeValue = normalizeFact(value);
        if (safeValue.length() > MAX_FACT_LENGTH || containsSensitiveTerm(safeValue)) {
            return null;
        }
        long now = System.currentTimeMillis();
        long expiresAt = ttl == null || ttl.isZero() || ttl.isNegative() ? 0L : now + ttl.toMillis();
        MemoryRecord record = nextRecord(
                scope, clean(subjectId), clean(relationId), kind, canonicalKey(key), safeValue,
                confidence, salience, sourceType, sourceId, occurredAt, expiresAt, tags
        );
        store(record);
        return record;
    }

    /** Concise player-aware memory summary for prompt context. */
    public String summary(UUID playerId) {
        return summary(playerId, "", "");
    }

    /** Concise scoped summary including optional NPC relationship and query-relevant episodic memories. */
    public String summary(UUID playerId, String npcId, String query) {
        if (!memoryFeatureEnabled() || playerId == null) {
            return "";
        }
        long now = System.currentTimeMillis();
        List<MemoryRecord> records = activeRecords.values().stream()
                .filter(record -> record.activeAt(now))
                .filter(record -> visibleTo(record, playerId.toString(), clean(npcId)))
                .sorted(memoryComparator(query))
                .limit(32)
                .toList();
        StringBuilder output = new StringBuilder();
        appendSection(output, "Player preferences", records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == MemoryKind.PREFERENCE)
                .map(record -> record.key() + '=' + record.value()).toList());
        appendSection(output, "Shared server memory", records.stream()
                .filter(record -> record.scope() == MemoryScope.GLOBAL && record.kind() == MemoryKind.FACT)
                .map(MemoryRecord::value).toList());
        appendSection(output, "Saved player facts", records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == MemoryKind.FACT)
                .map(MemoryRecord::value).toList());
        appendSection(output, "Player-NPC relationship", records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER_NPC && record.kind() == MemoryKind.RELATIONSHIP)
                .map(record -> record.key() + '=' + record.value()).toList());
        appendSection(output, "Relevant remembered events", records.stream()
                .filter(record -> record.kind() == MemoryKind.EVENT || record.kind() == MemoryKind.EPISODE)
                .limit(5).map(MemoryRecord::value).toList());
        return limit(output.toString());
    }

    /** Searchable typed memory view for event recall and diagnostics. */
    public List<MemoryRecord> search(
            UUID playerId, String npcId, String query, Set<MemoryKind> kinds, int maximumResults
    ) {
        if (!memoryFeatureEnabled() || playerId == null || maximumResults <= 0) {
            return List.of();
        }
        Set<MemoryKind> effectiveKinds = kinds == null || kinds.isEmpty() ? Set.of(MemoryKind.values()) : kinds;
        long now = System.currentTimeMillis();
        return activeRecords.values().stream()
                .filter(record -> record.activeAt(now))
                .filter(record -> visibleTo(record, playerId.toString(), clean(npcId)))
                .filter(record -> effectiveKinds.contains(record.kind()))
                .sorted(memoryComparator(query))
                .limit(Math.clamp(maximumResults, 1, 32))
                .toList();
    }

    /** Snapshot used by diagnostics and tests; records are immutable. */
    public List<MemoryRecord> activeSnapshot() {
        long now = System.currentTimeMillis();
        return activeRecords.values().stream().filter(record -> record.activeAt(now))
                .sorted(Comparator.comparingLong(MemoryRecord::lastConfirmed).reversed()).toList();
    }

    /** Waits until queued durable writes preceding this call have completed. */
    public void flush() {
        if (closed) {
            return;
        }
        try {
            writer.submit(() -> { }).get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            warn("Could not flush assistant memory writer: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        flush();
        closed = true;
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        repository.close();
    }

    private MemoryRepository createRepository(File dataFolder) {
        MemoryRepository candidate = new SqliteMemoryRepository(new File(dataFolder, DATABASE_FILE_NAME).toPath());
        try {
            candidate.initialize();
            return candidate;
        } catch (RuntimeException exception) {
            warn("SQLite assistant memory unavailable; using non-persistent fail-safe: " + exception.getMessage());
            candidate.close();
            MemoryRepository fallback = new InMemoryMemoryRepository();
            fallback.initialize();
            return fallback;
        }
    }

    private void loadFromRepository() {
        long now = System.currentTimeMillis();
        for (MemoryRecord record : repository.loadActive(now)) {
            activeRecords.merge(record.identityKey(), record,
                    (left, right) -> left.lastConfirmed() >= right.lastConfirmed() ? left : right);
        }
    }

    private void rememberPreference(UUID playerId, String candidate, String playerMessage, String sourceType) {
        int separator = candidate.indexOf('=');
        if (separator < 1 || separator == candidate.length() - 1) {
            return;
        }
        String key = canonicalKey(candidate.substring(0, separator));
        String value = clean(candidate.substring(separator + 1)).toLowerCase(Locale.ROOT);
        if (value.length() > 32 || !appearsExplicitly(key, value, playerMessage) || !validPreference(key, value)) {
            return;
        }
        long now = System.currentTimeMillis();
        MemoryRecord record = nextRecord(
                MemoryScope.PLAYER, playerId.toString(), "", MemoryKind.PREFERENCE, key, value,
                1.0D, 0.95D, sourceType, playerId.toString(), 0L,
                now + Duration.ofDays(retentionDays()).toMillis(), Set.of("preference", key)
        );
        store(record);
    }

    private void rememberPlayerFact(UUID playerId, String playerName, String candidate, String playerMessage) {
        String fact = normalizeFact(candidate);
        if (!isSafeExplicitFact(playerId, fact, playerMessage, true)) {
            return;
        }
        long now = System.currentTimeMillis();
        MemoryRecord record = nextRecord(
                MemoryScope.PLAYER, playerId.toString(), "", MemoryKind.FACT, factKey(fact), fact,
                0.9D, 0.7D, "model-candidate", safePlayerName(playerName), 0L,
                now + Duration.ofDays(retentionDays()).toMillis(), Set.of("player-fact")
        );
        store(record);
        enforceLimit(MemoryScope.PLAYER, playerId.toString(), MemoryKind.FACT, maxPlayerFacts());
    }

    private void rememberSharedFact(String playerName, String candidate, String playerMessage) {
        String fact = normalizeFact(candidate);
        if (!isSafeExplicitFact(null, fact, playerMessage, false)) {
            return;
        }
        long now = System.currentTimeMillis();
        MemoryRecord record = nextRecord(
                MemoryScope.GLOBAL, "", "", MemoryKind.FACT, factKey(fact), fact,
                0.9D, 0.8D, "authorized-player", safePlayerName(playerName), 0L,
                now + Duration.ofDays(retentionDays()).toMillis(), Set.of("shared-fact")
        );
        store(record);
        enforceLimit(MemoryScope.GLOBAL, "", MemoryKind.FACT, maxSharedFacts());
    }

    private MemoryRecord nextRecord(
            MemoryScope scope,
            String subjectId,
            String relationId,
            MemoryKind kind,
            String key,
            String value,
            double confidence,
            double salience,
            String sourceType,
            String sourceId,
            long occurredAt,
            long expiresAt,
            Set<String> tags
    ) {
        long now = System.currentTimeMillis();
        String identity = identity(scope, subjectId, relationId, kind, key);
        MemoryRecord previous = activeRecords.get(identity);
        long firstObserved = previous == null ? now : previous.firstObserved();
        return new MemoryRecord(
                UUID.randomUUID().toString(), scope, subjectId, relationId, kind, key, value, confidence, salience,
                sourceType, sourceId, firstObserved, now, occurredAt, expiresAt,
                previous == null ? "" : previous.id(), tags
        );
    }

    private void store(MemoryRecord record) {
        if (closed) {
            return;
        }
        MemoryRecord previous = activeRecords.put(record.identityKey(), record);
        writer.execute(() -> {
            try {
                if (previous != null && !previous.id().equals(record.id())) {
                    repository.upsert(previous.expireAt(record.lastConfirmed()));
                }
                repository.upsert(record);
            } catch (RuntimeException exception) {
                warn("Could not persist typed assistant memory: " + exception.getMessage());
            }
        });
    }

    private void enforceLimit(MemoryScope scope, String subjectId, MemoryKind kind, int limit) {
        List<MemoryRecord> matching = activeRecords.values().stream()
                .filter(record -> record.scope() == scope && record.subjectId().equals(subjectId) && record.kind() == kind)
                .sorted(Comparator.comparingDouble(MemoryRecord::salience)
                        .thenComparingLong(MemoryRecord::lastConfirmed))
                .toList();
        int removeCount = matching.size() - limit;
        for (int index = 0; index < removeCount; index++) {
            MemoryRecord record = matching.get(index);
            if (activeRecords.remove(record.identityKey(), record)) {
                long now = System.currentTimeMillis();
                writer.execute(() -> repository.upsert(record.expireAt(now)));
            }
        }
    }

    private Comparator<MemoryRecord> memoryComparator(String query) {
        Set<String> queryTerms = Set.copyOf(significantWords(query));
        long now = System.currentTimeMillis();
        return Comparator.<MemoryRecord>comparingDouble(record -> memoryScore(record, queryTerms, now)).reversed()
                .thenComparing(Comparator.comparingLong(MemoryRecord::lastConfirmed).reversed());
    }

    private double memoryScore(MemoryRecord record, Set<String> queryTerms, long now) {
        double lexical = 0.0D;
        if (!queryTerms.isEmpty()) {
            Set<String> recordTerms = new java.util.HashSet<>(significantWords(record.key() + " " + record.value()));
            long overlap = queryTerms.stream().filter(recordTerms::contains).count();
            lexical = (double) overlap / queryTerms.size();
        }
        long age = Math.max(0L, now - record.lastConfirmed());
        double recency = 1.0D / (1.0D + age / (double) Duration.ofDays(7).toMillis());
        return record.salience() * 0.45D + record.confidence() * 0.20D + recency * 0.15D + lexical * 0.20D;
    }

    private boolean visibleTo(MemoryRecord record, String playerId, String npcId) {
        return switch (record.scope()) {
            case GLOBAL -> true;
            case PLAYER, SESSION -> record.subjectId().equals(playerId);
            case NPC -> !npcId.isBlank() && record.subjectId().equals(npcId);
            case PLAYER_NPC -> record.subjectId().equals(playerId)
                    && (npcId.isBlank() || record.relationId().equals(npcId));
            case EVENT -> record.subjectId().isBlank() || record.subjectId().equals(playerId)
                    || (!npcId.isBlank() && record.relationId().equals(npcId));
            case WORLD -> false;
        };
    }

    private void appendSection(StringBuilder output, String heading, Collection<String> values) {
        List<String> cleanValues = values.stream().map(this::clean).filter(value -> !value.isBlank()).distinct().toList();
        if (cleanValues.isEmpty()) {
            return;
        }
        if (!output.isEmpty()) {
            output.append('\n');
        }
        output.append(heading).append(": ").append(String.join(" | ", cleanValues));
    }

    private void migrateLegacyMemoryOnce() {
        if (migrationMarker.isFile()) {
            return;
        }
        try {
            migratePreferences();
            migrateFacts();
            Files.createDirectories(migrationMarker.toPath().getParent());
            Files.writeString(migrationMarker.toPath(), "AIlex Memory V2 migration completed\n", StandardCharsets.UTF_8);
        } catch (RuntimeException | IOException exception) {
            warn("Could not complete legacy assistant-memory migration: " + exception.getMessage());
        }
    }

    private void migratePreferences() {
        if (!preferencesFile.isFile()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(preferencesFile);
        ConfigurationSection players = configuration.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String rawId : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(rawId);
                String path = "players." + rawId;
                long updated = configuration.getLong(path + ".updated_at", System.currentTimeMillis());
                migratePreference(id, "language", configuration.getString(path + ".language", ""), updated);
                migratePreference(id, "answer_length", configuration.getString(path + ".answer_length", ""), updated);
                migratePreference(id, "tone", configuration.getString(path + ".tone", ""), updated);
                migratePreference(id, "preferred_gamemode", configuration.getString(path + ".preferred_gamemode", ""), updated);
            } catch (IllegalArgumentException ignored) {
                // Malformed legacy operator edits are ignored.
            }
        }
    }

    private void migratePreference(UUID id, String key, String value, long updated) {
        String cleanValue = clean(value).toLowerCase(Locale.ROOT);
        if (cleanValue.isBlank() || !validPreference(key, cleanValue)) {
            return;
        }
        long expiry = updated + Duration.ofDays(retentionDays()).toMillis();
        if (expiry <= System.currentTimeMillis()) {
            return;
        }
        installMigrated(new MemoryRecord(
                UUID.randomUUID().toString(), MemoryScope.PLAYER, id.toString(), "", MemoryKind.PREFERENCE,
                key, cleanValue, 1.0D, 0.9D, "legacy-yaml", PREFERENCES_FILE_NAME,
                updated, updated, 0L, expiry, "", Set.of("legacy", "preference")
        ));
    }

    private void migrateFacts() {
        if (!longTermFile.isFile()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(longTermFile);
        List<String> shared = configuration.getStringList("shared_facts");
        if (shared.isEmpty()) {
            shared = configuration.getStringList("server_facts");
        }
        for (String rawFact : shared.stream().limit(maxSharedFacts()).toList()) {
            String fact = normalizeFact(rawFact);
            if (!fact.isBlank() && fact.length() <= MAX_FACT_LENGTH && !containsSensitiveTerm(fact)) {
                installMigrated(legacyFact(MemoryScope.GLOBAL, "", fact));
            }
        }
        ConfigurationSection players = configuration.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String rawId : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(rawId);
                String path = "players." + rawId + ".facts";
                for (String rawFact : configuration.getStringList(path).stream().limit(maxPlayerFacts()).toList()) {
                    String fact = normalizeFact(rawFact);
                    if (!fact.isBlank() && fact.length() <= MAX_FACT_LENGTH && !containsSensitiveTerm(fact)) {
                        installMigrated(legacyFact(MemoryScope.PLAYER, id.toString(), fact));
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed legacy operator edits are ignored.
            }
        }
    }

    private MemoryRecord legacyFact(MemoryScope scope, String subject, String fact) {
        long now = System.currentTimeMillis();
        return new MemoryRecord(
                UUID.randomUUID().toString(), scope, subject, "", MemoryKind.FACT, factKey(fact), fact,
                0.85D, 0.65D, "legacy-yaml", LONG_TERM_FILE_NAME, now, now, 0L,
                now + Duration.ofDays(retentionDays()).toMillis(), "", Set.of("legacy", "fact")
        );
    }

    private void installMigrated(MemoryRecord record) {
        MemoryRecord current = activeRecords.get(record.identityKey());
        if (current != null && current.lastConfirmed() >= record.lastConfirmed()) {
            return;
        }
        activeRecords.put(record.identityKey(), record);
        repository.upsert(record);
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
        return personalFact && factWords.stream().anyMatch(word -> word.length() >= 4 && messageWords.contains(word))
                && (hasPersonalInterestSignal(message) || hasRepeatedTopic(playerId, factWords, messageWords));
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

    private List<String> significantWords(String value) {
        return java.util.Arrays.stream((value == null ? "" : value.toLowerCase(Locale.ROOT))
                        .split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= 3)
                .filter(word -> !List.of("and", "dat", "een", "het", "ik", "mijn", "the", "van", "voor")
                        .contains(word))
                .toList();
    }

    private boolean appearsExplicitly(String key, String value, String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if ("language".equals(key)) {
            return switch (value) {
                case "nl" -> containsWord(normalized, "nl") || normalized.contains("nederlands")
                        || normalized.contains("dutch");
                case "en" -> normalized.contains("english") || normalized.contains("engels")
                        || normalized.contains("in en");
                case "de" -> normalized.contains("duits") || normalized.contains("deutsch")
                        || normalized.contains("german") || normalized.contains("in de");
                default -> false;
            };
        }
        return containsWord(normalized, value);
    }

    private boolean validPreference(String key, String value) {
        return switch (key) {
            case "language" -> AssistantSettings.from(plugin.getConfig()).languageAllowed(value);
            case "answer_length" -> Set.of("short", "normal", "detailed").contains(value);
            case "tone" -> Set.of("casual", "neutral", "formal").contains(value);
            case "preferred_gamemode" -> Set.of("survival", "creative", "minigames").contains(value);
            default -> false;
        };
    }

    private boolean isFirstPersonLanguagePreference(String message) {
        return message.matches(".*\\bik\\s+(spreek|praat)\\b.*")
                || message.matches(".*\\bi\\s+(speak|talk)\\b.*")
                || message.matches(".*\\bich\\s+spreche\\b.*")
                || message.matches(".*\\b(antwoord|reageer|reply|respond)\\b.*\\b(tegen mij|to me|zu mir)\\b.*");
    }

    private String explicitLanguage(String message) {
        if (message.contains("duits") || message.contains("deutsch") || message.contains("german")) {
            return "de";
        }
        if (message.contains("engels") || message.contains("english")) {
            return "en";
        }
        if (message.contains("nederlands") || message.contains("dutch")) {
            return "nl";
        }
        return "";
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

    private String factKey(String fact) {
        return "fact." + UUID.nameUUIDFromBytes(fact.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private String identity(MemoryScope scope, String subject, String relation, MemoryKind kind, String key) {
        return scope + "|" + clean(subject) + "|" + clean(relation) + "|" + kind + "|" + canonicalKey(key);
    }

    private String canonicalKey(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizeFact(String value) {
        return clean(value);
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String safePlayerName(String value) {
        String normalized = clean(value);
        return normalized.length() <= 32 ? normalized : normalized.substring(0, 32);
    }

    private boolean startsWith(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private String limit(String value) {
        return value.length() <= MAX_CONTEXT_CHARACTERS ? value
                : value.substring(0, MAX_CONTEXT_CHARACTERS - 1) + "…";
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

    private void warn(String message) {
        if (plugin.getLogger() != null) {
            plugin.getLogger().warning(message);
        }
    }
}
