package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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
 * Typed semantic/episodic memory facade. Reads are served from a hot active-record map while durable writes are
 * serialized to SQLite/WAL on one dedicated writer. Semantic records are key-addressable and therefore correctable:
 * a new value for the same scope/kind/key supersedes the old value instead of accumulating contradictory sentences.
 */
public final class AssistantMemoryService implements AutoCloseable {

    private static final String DATABASE_FILE_NAME = "assistant-memory.db";
    private static final int MAX_VALUE_LENGTH = 320;
    private static final int MAX_KEY_LENGTH = 96;
    private static final int MAX_RECENT_TOPIC_TERMS = 192;
    private static final List<String> SENSITIVE_TERMS = List.of(
            "password", "wachtwoord", "ip address", "ip-adres", "email", "e-mail", "telefoon", "phone",
            "discord token", "api key", "apikey", "secret", "verification code", "verificatiecode",
            "coordinates", "coordinaten", "coördinaten", "home address", "woonadres", "report", "rapport",
            "ban reason", "sanction", "strafreden"
    );

    private final JavaPlugin plugin;
    private final MemoryRepository repository;
    private final Map<String, MemoryRecord> activeRecords = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> recentTopicTerms = new ConcurrentHashMap<>();
    private final ExecutorService writer;
    private volatile boolean closed;

    public AssistantMemoryService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.repository = createRepository(plugin.getDataFolder());
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AIlex-MemoryWriter");
            thread.setDaemon(true);
            return thread;
        });
        loadFromRepository();
        writer.execute(() -> repository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(30)).toEpochMilli()));
    }

    public boolean isEnabled(UUID playerId) {
        return memoryFeatureEnabled();
    }

    /** Reloads the hot index after queued writes have completed. */
    public synchronized void reload() {
        flush();
        activeRecords.clear();
        recentTopicTerms.clear();
        loadFromRepository();
    }

    /** Tracks repeated topic terms in volatile session memory; raw chat is never persisted here. */
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
            observed.entrySet().removeIf(entry -> entry.getValue() <= 1);
            if (observed.size() + terms.size() > MAX_RECENT_TOPIC_TERMS) {
                observed.clear();
            }
        }
        terms.forEach(term -> observed.merge(term, 1, (oldValue, ignored) -> Math.min(oldValue + 1, 5)));
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
        if (language.isBlank()) {
            return;
        }
        storeSemantic(
                MemoryScope.PLAYER,
                playerId.toString(),
                "",
                MemoryKind.PREFERENCE,
                "language",
                language,
                1.0D,
                1.0D,
                "player-explicit",
                playerId.toString(),
                Set.of("preference", "language", "explicit")
        );
    }

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

    /**
     * Validates and stores one model-proposed semantic memory operation. Only explicit source-supported information is
     * accepted. Shared facts additionally require the caller's trusted shared-write permission.
     */
    public synchronized MemoryRecord rememberCandidate(
            UUID playerId,
            String playerName,
            MemoryCandidate candidate,
            String playerMessage,
            boolean canWriteSharedMemory
    ) {
        if (!memoryFeatureEnabled() || playerId == null || candidate == null || candidate.key().isBlank()) {
            return null;
        }
        MemoryScope scope = candidateScope(candidate.scope(), canWriteSharedMemory);
        if (scope == null) {
            return null;
        }
        MemoryKind kind = candidateKind(candidate.kind());
        if (kind == null) {
            return null;
        }
        String key = canonicalKey(candidate.key());
        if (!safeKey(key)) {
            return null;
        }

        String subject = scope == MemoryScope.PLAYER ? playerId.toString() : "";
        if (candidate.forget()) {
            if (!hasForgetSignal(playerMessage) || !keySupportedByMessage(key, playerMessage)) {
                return null;
            }
            forget(scope, subject, "", kind, key);
            return null;
        }

        String value = normalizeValue(candidate.value());
        if (!safeValue(value) || !candidateSupportedByMessage(key, value, kind, playerMessage, playerId)) {
            return null;
        }
        if (kind == MemoryKind.PREFERENCE && "language".equals(key)) {
            String normalizedLanguage = normalizeLanguage(value);
            if (normalizedLanguage.isBlank()) {
                return null;
            }
            value = normalizedLanguage;
        }

        double confidence = switch (kind) {
            case PREFERENCE -> 0.99D;
            case OPINION -> 0.96D;
            case FACT -> scope == MemoryScope.GLOBAL ? 0.97D : 0.93D;
            default -> 0.90D;
        };
        double salience = switch (kind) {
            case PREFERENCE -> 0.90D;
            case OPINION -> 0.76D;
            case FACT -> scope == MemoryScope.GLOBAL ? 0.88D : 0.72D;
            default -> 0.65D;
        };
        Set<String> tags = new HashSet<>();
        tags.add("semantic");
        tags.add(kind.name().toLowerCase(Locale.ROOT));
        tags.add(scope == MemoryScope.GLOBAL ? "shared" : "player");
        tags.addAll(keyTags(key));

        MemoryRecord record = storeSemantic(
                scope,
                subject,
                "",
                kind,
                key,
                value,
                confidence,
                salience,
                scope == MemoryScope.GLOBAL ? "authorized-player" : "player-explicit",
                safePlayerName(playerName),
                Set.copyOf(tags)
        );
        if (scope == MemoryScope.GLOBAL) {
            enforceLimit(MemoryScope.GLOBAL, "", Set.of(MemoryKind.FACT), maxSharedFacts());
        } else {
            enforceLimit(
                    MemoryScope.PLAYER,
                    playerId.toString(),
                    Set.of(MemoryKind.FACT, MemoryKind.PREFERENCE, MemoryKind.OPINION),
                    maxPlayerMemories()
            );
        }
        return record;
    }

    /** Stores trusted runtime knowledge such as an event or factual relationship update. */
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
        if (!memoryFeatureEnabled() || scope == null || kind == null || !safeKey(canonicalKey(key)) || !safeValue(value)) {
            return null;
        }
        long now = System.currentTimeMillis();
        long expiresAt = ttl == null || ttl.isZero() || ttl.isNegative() ? 0L : now + ttl.toMillis();
        MemoryRecord record = nextRecord(
                scope,
                clean(subjectId),
                clean(relationId),
                kind,
                canonicalKey(key),
                normalizeValue(value),
                confidence,
                salience,
                sourceType,
                sourceId,
                occurredAt,
                expiresAt,
                tags
        );
        store(record);
        return record;
    }

    /** Concise player-aware memory summary for diagnostics and callers that do not need custom ranking. */
    public String summary(UUID playerId) {
        return summary(playerId, "", "");
    }

    /** Query-ranked scoped summary. */
    public String summary(UUID playerId, String npcId, String query) {
        if (!memoryFeatureEnabled() || playerId == null) {
            return "";
        }
        List<MemoryRecord> records = search(playerId, npcId, query, Set.of(MemoryKind.values()), 48);
        StringBuilder output = new StringBuilder();
        appendSection(output, "Player preferences", records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == MemoryKind.PREFERENCE)
                .map(record -> record.key() + '=' + record.value()).toList());
        appendSection(output, "Player facts", records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == MemoryKind.FACT)
                .map(record -> record.key() + '=' + record.value()).toList());
        appendSection(output, "Explicit player opinions", records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == MemoryKind.OPINION)
                .map(record -> record.key() + '=' + record.value()).toList());
        appendSection(output, "Shared server memory", records.stream()
                .filter(record -> record.scope() == MemoryScope.GLOBAL && record.kind() == MemoryKind.FACT)
                .map(record -> record.key() + '=' + record.value()).toList());
        appendSection(output, "Player-NPC relationship", records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER_NPC && record.kind() == MemoryKind.RELATIONSHIP)
                .map(record -> record.key() + '=' + record.value()).toList());
        appendSection(output, "Relevant remembered events", records.stream()
                .filter(record -> record.kind() == MemoryKind.EVENT || record.kind() == MemoryKind.EPISODE)
                .limit(8)
                .map(MemoryRecord::value)
                .toList());
        return limitContext(output.toString());
    }

    /** Searchable typed-memory view for prompt retrieval and diagnostics. */
    public List<MemoryRecord> search(
            UUID playerId,
            String npcId,
            String query,
            Set<MemoryKind> kinds,
            int maximumResults
    ) {
        if (!memoryFeatureEnabled() || playerId == null || maximumResults <= 0) {
            return List.of();
        }
        Set<MemoryKind> effectiveKinds = kinds == null || kinds.isEmpty() ? Set.of(MemoryKind.values()) : kinds;
        String player = playerId.toString();
        String npc = clean(npcId);
        long now = System.currentTimeMillis();
        return activeRecords.values().stream()
                .filter(record -> record.activeAt(now))
                .filter(record -> visibleTo(record, player, npc))
                .filter(record -> effectiveKinds.contains(record.kind()))
                .sorted(memoryComparator(query, player, npc, now))
                .limit(Math.clamp(maximumResults, 1, 96))
                .toList();
    }

    public List<MemoryRecord> activeSnapshot() {
        long now = System.currentTimeMillis();
        return activeRecords.values().stream()
                .filter(record -> record.activeAt(now))
                .sorted(Comparator.comparingLong(MemoryRecord::lastConfirmed).reversed())
                .toList();
    }

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
        File effectiveFolder = dataFolder == null ? new File("plugins/AIlex") : dataFolder;
        MemoryRepository candidate = new SqliteMemoryRepository(new File(effectiveFolder, DATABASE_FILE_NAME).toPath());
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
            activeRecords.merge(
                    record.identityKey(),
                    record,
                    (left, right) -> left.lastConfirmed() >= right.lastConfirmed() ? left : right
            );
        }
    }

    private MemoryRecord storeSemantic(
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
            Set<String> tags
    ) {
        MemoryRecord previous = activeRecords.get(identity(scope, subjectId, relationId, kind, key));
        double reinforcedConfidence = previous != null && previous.value().equalsIgnoreCase(value)
                ? Math.min(1.0D, Math.max(confidence, previous.confidence() + 0.02D))
                : confidence;
        double reinforcedSalience = previous != null && previous.value().equalsIgnoreCase(value)
                ? Math.min(1.0D, Math.max(salience, previous.salience() + 0.04D))
                : salience;
        MemoryRecord record = nextRecord(
                scope,
                clean(subjectId),
                clean(relationId),
                kind,
                canonicalKey(key),
                normalizeValue(value),
                reinforcedConfidence,
                reinforcedSalience,
                sourceType,
                sourceId,
                0L,
                0L,
                tags
        );
        store(record);
        return record;
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
                UUID.randomUUID().toString(),
                scope,
                subjectId,
                relationId,
                kind,
                key,
                value,
                confidence,
                salience,
                sourceType,
                sourceId,
                firstObserved,
                now,
                occurredAt,
                expiresAt,
                previous == null ? "" : previous.id(),
                tags
        );
    }

    private void store(MemoryRecord record) {
        if (closed || record == null) {
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

    private void forget(MemoryScope scope, String subject, String relation, MemoryKind kind, String key) {
        String identity = identity(scope, subject, relation, kind, key);
        MemoryRecord previous = activeRecords.remove(identity);
        if (previous == null) {
            return;
        }
        long now = System.currentTimeMillis();
        writer.execute(() -> repository.upsert(previous.expireAt(now)));
    }

    private void enforceLimit(MemoryScope scope, String subjectId, Set<MemoryKind> kinds, int limit) {
        List<MemoryRecord> matching = activeRecords.values().stream()
                .filter(record -> record.scope() == scope && record.subjectId().equals(subjectId))
                .filter(record -> kinds.contains(record.kind()))
                .sorted(Comparator.comparingDouble(MemoryRecord::salience)
                        .thenComparingDouble(MemoryRecord::confidence)
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

    private Comparator<MemoryRecord> memoryComparator(String query, String playerId, String npcId, long now) {
        Set<String> queryTerms = Set.copyOf(significantWords(query));
        String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);
        return Comparator.<MemoryRecord>comparingDouble(
                        record -> memoryScore(record, queryTerms, normalizedQuery, playerId, npcId, now)
                ).reversed()
                .thenComparing(Comparator.comparingLong(MemoryRecord::lastConfirmed).reversed());
    }

    private double memoryScore(
            MemoryRecord record,
            Set<String> queryTerms,
            String normalizedQuery,
            String playerId,
            String npcId,
            long now
    ) {
        Set<String> recordTerms = new HashSet<>(significantWords(
                record.key() + " " + record.value() + " " + String.join(" ", record.tags())
        ));
        double lexical = 0.0D;
        if (!queryTerms.isEmpty()) {
            long overlap = queryTerms.stream().filter(recordTerms::contains).count();
            lexical = (double) overlap / Math.max(1, queryTerms.size());
        }
        double phrase = !normalizedQuery.isBlank()
                && (record.value().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || normalizedQuery.contains(record.value().toLowerCase(Locale.ROOT))) ? 1.0D : 0.0D;
        long age = Math.max(0L, now - record.lastConfirmed());
        double recency = 1.0D / (1.0D + age / (double) Duration.ofDays(21).toMillis());
        double scope = switch (record.scope()) {
            case PLAYER -> record.subjectId().equals(playerId) ? 1.0D : 0.0D;
            case PLAYER_NPC -> record.subjectId().equals(playerId) && record.relationId().equals(npcId) ? 1.0D : 0.75D;
            case GLOBAL -> 0.78D;
            case EVENT, SESSION -> 0.72D;
            case NPC -> 0.70D;
            case WORLD -> 0.45D;
        };
        double kind = switch (record.kind()) {
            case PREFERENCE -> 1.0D;
            case OPINION -> 0.92D;
            case FACT -> 0.90D;
            case RELATIONSHIP -> 0.78D;
            case EVENT, EPISODE -> 0.72D;
        };
        return record.salience() * 0.24D
                + record.confidence() * 0.16D
                + recency * 0.12D
                + lexical * 0.26D
                + phrase * 0.08D
                + scope * 0.08D
                + kind * 0.06D;
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

    private MemoryScope candidateScope(String value, boolean canWriteSharedMemory) {
        String scope = clean(value).toLowerCase(Locale.ROOT);
        if ("player".equals(scope)) {
            return MemoryScope.PLAYER;
        }
        if ("shared".equals(scope) && canWriteSharedMemory) {
            return MemoryScope.GLOBAL;
        }
        return null;
    }

    private MemoryKind candidateKind(String value) {
        return switch (clean(value).toLowerCase(Locale.ROOT)) {
            case "preference" -> MemoryKind.PREFERENCE;
            case "fact" -> MemoryKind.FACT;
            case "opinion" -> MemoryKind.OPINION;
            default -> null;
        };
    }

    private boolean candidateSupportedByMessage(
            String key,
            String value,
            MemoryKind kind,
            String message,
            UUID playerId
    ) {
        String normalizedMessage = clean(message).toLowerCase(Locale.ROOT);
        if (normalizedMessage.isBlank()) {
            return false;
        }
        if (kind == MemoryKind.PREFERENCE && "language".equals(key)) {
            return !normalizeLanguage(value).isBlank() && isFirstPersonLanguagePreference(normalizedMessage);
        }

        List<String> valueTerms = significantWords(value);
        List<String> messageTerms = significantWords(normalizedMessage);
        long valueOverlap = valueTerms.stream().distinct().filter(messageTerms::contains).count();
        List<String> keyTerms = significantWords(key.replace('_', ' ').replace('.', ' '));
        long keyOverlap = keyTerms.stream().distinct().filter(messageTerms::contains).count();

        boolean directSupport = valueTerms.isEmpty()
                ? false
                : valueOverlap >= Math.min(2, Math.max(1, valueTerms.size()));
        boolean semanticSupport = keyOverlap >= 1 && valueOverlap >= 1;
        if (kind == MemoryKind.OPINION || kind == MemoryKind.PREFERENCE) {
            return (directSupport || semanticSupport)
                    && (hasPreferenceOrOpinionSignal(normalizedMessage) || hasRememberSignal(normalizedMessage)
                    || hasRepeatedTopic(playerId, valueTerms, messageTerms));
        }
        return directSupport || semanticSupport || (hasCorrectionSignal(normalizedMessage) && keyOverlap >= 1);
    }

    private boolean keySupportedByMessage(String key, String message) {
        List<String> messageTerms = significantWords(message);
        return significantWords(key.replace('_', ' ').replace('.', ' ')).stream()
                .anyMatch(messageTerms::contains);
    }

    private boolean hasRepeatedTopic(UUID playerId, List<String> valueTerms, List<String> messageTerms) {
        if (playerId == null) {
            return false;
        }
        Map<String, Integer> observed = recentTopicTerms.get(playerId);
        return observed != null && valueTerms.stream()
                .filter(word -> word.length() >= 4 && messageTerms.contains(word))
                .anyMatch(word -> observed.getOrDefault(word, 0) >= 2);
    }

    private boolean safeKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            return false;
        }
        return key.matches("[a-z0-9._-]+") && !containsSensitiveTerm(key.replace('_', ' ').replace('.', ' '));
    }

    private boolean safeValue(String value) {
        String normalized = normalizeValue(value);
        return !normalized.isBlank() && normalized.length() <= MAX_VALUE_LENGTH && !containsSensitiveTerm(normalized);
    }

    private boolean containsSensitiveTerm(String value) {
        String normalized = clean(value).toLowerCase(Locale.ROOT);
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

    private boolean hasPreferenceOrOpinionSignal(String message) {
        return containsAny(message,
                "ik hou van", "ik vind", "ik speel graag", "mijn favoriete", "mijn voorkeur", "ik heb liever",
                "ik haat", "ik vind niet leuk", "i like", "i love", "i prefer", "my favorite", "i am a fan",
                "i hate", "i dislike", "i don't like", "i dont like"
        );
    }

    private boolean hasRememberSignal(String message) {
        return containsAny(message, "onthoud", "remember", "herinner");
    }

    private boolean hasForgetSignal(String message) {
        return containsAny(clean(message).toLowerCase(Locale.ROOT), "vergeet", "forget", "niet meer onthouden");
    }

    private boolean hasCorrectionSignal(String message) {
        return containsAny(message,
                "klopt niet", "niet waar", "je hebt het fout", "je zit fout", "correctie", "eigenlijk",
                "that's wrong", "that is wrong", "you're wrong", "you are wrong", "not correct", "correction",
                "actually"
        );
    }

    private boolean isFirstPersonLanguagePreference(String message) {
        return message.matches(".*\\bik\\s+(spreek|praat)\\b.*")
                || message.matches(".*\\bi\\s+(speak|talk)\\b.*")
                || message.matches(".*\\bich\\s+spreche\\b.*")
                || message.matches(".*\\b(antwoord|reageer|reply|respond)\\b.*\\b(tegen mij|to me|zu mir)\\b.*")
                || message.matches(".*\\b(antwoord|reply|respond)\\b.*\\b(nederlands|dutch|engels|english|duits|deutsch|german)\\b.*");
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

    private String normalizeLanguage(String value) {
        String normalized = clean(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "nl", "nederlands", "dutch" -> "nl";
            case "en", "engels", "english" -> "en";
            case "de", "duits", "deutsch", "german" -> "de";
            default -> "";
        };
    }

    private List<String> significantWords(String value) {
        return java.util.Arrays.stream((value == null ? "" : value.toLowerCase(Locale.ROOT))
                        .split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= 3)
                .filter(word -> !Set.of(
                        "and", "dat", "een", "het", "ik", "mijn", "the", "van", "voor", "this", "that",
                        "with", "you", "your", "jij", "jouw", "zijn", "haar", "hun", "was", "were", "ben"
                ).contains(word))
                .toList();
    }

    private Set<String> keyTags(String key) {
        List<String> terms = significantWords(key.replace('_', ' ').replace('.', ' '));
        return terms.stream().limit(6).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void appendSection(StringBuilder output, String heading, Collection<String> values) {
        List<String> cleanValues = values.stream()
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(24)
                .toList();
        if (cleanValues.isEmpty()) {
            return;
        }
        if (!output.isEmpty()) {
            output.append('\n');
        }
        output.append(heading).append(": ").append(String.join(" | ", cleanValues));
    }

    private String identity(MemoryScope scope, String subject, String relation, MemoryKind kind, String key) {
        return scope + "|" + clean(subject) + "|" + clean(relation) + "|" + kind + "|" + canonicalKey(key);
    }

    private String canonicalKey(String value) {
        return clean(value).toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-");
    }

    private String normalizeValue(String value) {
        return clean(value);
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String safePlayerName(String value) {
        String normalized = clean(value);
        return normalized.length() <= 32 ? normalized : normalized.substring(0, 32);
    }

    private String limitContext(String value) {
        int maximum = maxMemoryContextCharacters();
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    private boolean memoryFeatureEnabled() {
        FileConfiguration config = plugin.getConfig();
        return config == null || config.getBoolean("openai.assistant.memory.enabled", true);
    }

    private int maxSharedFacts() {
        FileConfiguration config = plugin.getConfig();
        return config == null ? 1_024
                : Math.clamp(config.getInt("openai.assistant.memory.max_shared_facts", 1_024), 16, 4_096);
    }

    private int maxPlayerMemories() {
        FileConfiguration config = plugin.getConfig();
        return config == null ? 256
                : Math.clamp(config.getInt("openai.assistant.memory.max_player_memories", 256), 16, 2_048);
    }

    private int maxMemoryContextCharacters() {
        FileConfiguration config = plugin.getConfig();
        return config == null ? 8_000
                : Math.clamp(config.getInt("openai.assistant.memory.max_context_characters", 8_000), 1_000, 30_000);
    }

    private boolean containsAny(String text, String... values) {
        String normalized = clean(text).toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (normalized.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private void warn(String message) {
        if (plugin.getLogger() != null) {
            plugin.getLogger().warning(message);
        }
    }
}
