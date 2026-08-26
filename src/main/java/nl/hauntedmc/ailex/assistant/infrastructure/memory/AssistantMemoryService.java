package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.security.AssistantDataSafety;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * Typed semantic/episodic memory facade. Durable writes are serialized to SQLite/WAL while reads use an audience-
 * indexed hot store, so a player request scores only memories that can actually be visible to that player/NPC rather
 * than scanning network-wide memory. Semantic keys are correctable and associative retrieval remains bounded.
 */
public final class AssistantMemoryService implements AutoCloseable {

    private static final String DATABASE_FILE_NAME = "assistant-memory.db";
    private static final String GLOBAL_AUDIENCE = "global";
    private static final String PLAYER_AUDIENCE_PREFIX = "player:";
    private static final String NPC_AUDIENCE_PREFIX = "npc:";
    private static final int MAX_VALUE_LENGTH = 320;
    private static final int MAX_KEY_LENGTH = 96;
    private static final int MAX_RECENT_TOPIC_TERMS = 192;
    private static final int ASSOCIATION_SEEDS = 8;
    private static final List<String> SENSITIVE_TERMS = List.of(
            "password", "wachtwoord", "ip address", "ip-adres", "email", "e-mail", "telefoon", "phone",
            "discord token", "api key", "apikey", "secret", "verification code", "verificatiecode",
            "coordinates", "coordinaten", "coördinaten", "home address", "woonadres", "report", "rapport",
            "ban reason", "sanction", "strafreden"
    );
    private static final Set<MemoryKind> PLAYER_SEMANTIC_KINDS = Set.of(
            MemoryKind.PREFERENCE,
            MemoryKind.FACT,
            MemoryKind.OPINION,
            MemoryKind.INTEREST,
            MemoryKind.GOAL
    );

    private final JavaPlugin plugin;
    private final MemoryRepository repository;
    private final Map<String, MemoryRecord> activeRecords = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> audienceIndex = new ConcurrentHashMap<>();
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
        audienceIndex.clear();
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
        forgetConflictingSemanticKinds(
                MemoryScope.PLAYER, playerId.toString(), "", MemoryKind.PREFERENCE, "language"
        );
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
            if (record != null) {
                evictExpired(record);
            }
            return "";
        }
        return AssistantSettings.from(plugin.getConfig()).languageAllowed(record.value()) ? record.value() : "";
    }

    /**
     * Validates and stores one model-proposed semantic memory operation. Only explicit source-supported information is
     * accepted. Shared memory is strictly factual and additionally requires trusted shared-write permission.
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
        if (kind == null || (scope == MemoryScope.GLOBAL && kind != MemoryKind.FACT)) {
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
            if (scope == MemoryScope.PLAYER) {
                forgetSemanticKey(scope, subject, "", key);
            } else {
                forget(scope, subject, "", kind, key);
            }
            return null;
        }

        String value = normalizeValue(candidate.value());
        if (!safeMemory(key, value) || !candidateSupportedByMessage(key, value, kind, playerMessage, playerId)) {
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
            case INTEREST -> 0.94D;
            case GOAL -> 0.93D;
            case FACT -> scope == MemoryScope.GLOBAL ? 0.97D : 0.93D;
            default -> 0.90D;
        };
        double salience = switch (kind) {
            case PREFERENCE -> 0.90D;
            case GOAL -> 0.84D;
            case INTEREST -> 0.80D;
            case OPINION -> 0.76D;
            case FACT -> scope == MemoryScope.GLOBAL ? 0.88D : 0.72D;
            default -> 0.65D;
        };
        Set<String> tags = new HashSet<>();
        tags.add("semantic");
        tags.add(kind.name().toLowerCase(Locale.ROOT));
        tags.add(scope == MemoryScope.GLOBAL ? "shared" : "player");
        tags.addAll(keyTags(key));
        tags.addAll(valueTags(value));

        if (scope == MemoryScope.PLAYER) {
            forgetConflictingSemanticKinds(scope, subject, "", kind, key);
        }
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
            enforceLimit(MemoryScope.PLAYER, playerId.toString(), PLAYER_SEMANTIC_KINDS, maxPlayerMemories());
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
        String canonicalKey = canonicalKey(key);
        String normalizedValue = normalizeValue(value);
        if (!memoryFeatureEnabled() || scope == null || kind == null || !safeMemory(canonicalKey, normalizedValue)) {
            return null;
        }
        long now = System.currentTimeMillis();
        long expiresAt = ttl == null || ttl.isZero() || ttl.isNegative() ? 0L : now + ttl.toMillis();
        Set<String> enrichedTags = new HashSet<>(tags == null ? Set.of() : tags);
        enrichedTags.addAll(keyTags(canonicalKey));
        enrichedTags.addAll(valueTags(normalizedValue));
        MemoryRecord record = nextRecord(
                scope,
                clean(subjectId),
                clean(relationId),
                kind,
                canonicalKey,
                normalizedValue,
                confidence,
                salience,
                sourceType,
                sourceId,
                occurredAt,
                expiresAt,
                Set.copyOf(enrichedTags)
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
        appendSection(output, "Player interests", records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == MemoryKind.INTEREST)
                .map(record -> record.key() + '=' + record.value()).toList());
        appendSection(output, "Player goals", records.stream()
                .filter(record -> record.scope() == MemoryScope.PLAYER && record.kind() == MemoryKind.GOAL)
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
        Set<String> queryTerms = Set.copyOf(significantWords(query));
        String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);

        List<ScoredMemory> primary = visibleCandidates(player, npc, now).stream()
                .filter(record -> effectiveKinds.contains(record.kind()))
                .map(record -> new ScoredMemory(
                        record,
                        memoryScore(record, queryTerms, normalizedQuery, player, npc, now)
                ))
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed())
                .toList();
        if (primary.isEmpty()) {
            return List.of();
        }

        Set<String> bridgeTerms = associationBridgeTerms(primary, queryTerms);
        List<ScoredMemory> associated = primary.stream()
                .map(scored -> new ScoredMemory(
                        scored.record(),
                        scored.score() + associationBonus(scored.record(), bridgeTerms, queryTerms)
                ))
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed()
                        .thenComparing(scored -> scored.record().lastConfirmed(), Comparator.reverseOrder()))
                .limit(Math.max(64L, Math.clamp(maximumResults, 1, 96) * 4L))
                .toList();
        return selectDiverse(associated, Math.clamp(maximumResults, 1, 96));
    }

    public List<MemoryRecord> activeSnapshot() {
        long now = System.currentTimeMillis();
        List<MemoryRecord> snapshot = new ArrayList<>();
        for (MemoryRecord record : activeRecords.values()) {
            if (record.activeAt(now)) {
                snapshot.add(record);
            } else {
                evictExpired(record);
            }
        }
        snapshot.sort(Comparator.comparingLong(MemoryRecord::lastConfirmed).reversed());
        return List.copyOf(snapshot);
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
        activeRecords.values().forEach(this::indexRecord);
    }

    private List<MemoryRecord> visibleCandidates(String playerId, String npcId, long now) {
        Set<String> identities = new LinkedHashSet<>();
        addAudience(identities, GLOBAL_AUDIENCE);
        addAudience(identities, PLAYER_AUDIENCE_PREFIX + playerId);
        if (!npcId.isBlank()) {
            addAudience(identities, NPC_AUDIENCE_PREFIX + npcId);
        }
        List<MemoryRecord> result = new ArrayList<>(identities.size());
        for (String identity : identities) {
            MemoryRecord record = activeRecords.get(identity);
            if (record == null) {
                continue;
            }
            if (!record.activeAt(now)) {
                evictExpired(record);
                continue;
            }
            if (visibleTo(record, playerId, npcId)) {
                result.add(record);
            }
        }
        return result;
    }

    private void addAudience(Set<String> target, String audience) {
        Set<String> indexed = audienceIndex.get(audience);
        if (indexed != null) {
            target.addAll(indexed);
        }
    }

    private void indexRecord(MemoryRecord record) {
        for (String audience : audiences(record)) {
            audienceIndex.computeIfAbsent(audience, ignored -> ConcurrentHashMap.newKeySet())
                    .add(record.identityKey());
        }
    }

    private void unindexRecord(MemoryRecord record) {
        for (String audience : audiences(record)) {
            Set<String> indexed = audienceIndex.get(audience);
            if (indexed == null) {
                continue;
            }
            indexed.remove(record.identityKey());
            if (indexed.isEmpty()) {
                audienceIndex.remove(audience, indexed);
            }
        }
    }

    private Set<String> audiences(MemoryRecord record) {
        Set<String> audiences = new HashSet<>();
        switch (record.scope()) {
            case GLOBAL -> audiences.add(GLOBAL_AUDIENCE);
            case PLAYER, SESSION -> {
                if (!record.subjectId().isBlank()) {
                    audiences.add(PLAYER_AUDIENCE_PREFIX + record.subjectId());
                }
            }
            case NPC -> {
                if (!record.subjectId().isBlank()) {
                    audiences.add(NPC_AUDIENCE_PREFIX + record.subjectId());
                }
            }
            case PLAYER_NPC -> {
                if (!record.subjectId().isBlank()) {
                    audiences.add(PLAYER_AUDIENCE_PREFIX + record.subjectId());
                }
                if (!record.relationId().isBlank()) {
                    audiences.add(NPC_AUDIENCE_PREFIX + record.relationId());
                }
            }
            case EVENT -> {
                if (record.subjectId().isBlank()) {
                    audiences.add(GLOBAL_AUDIENCE);
                } else {
                    audiences.add(PLAYER_AUDIENCE_PREFIX + record.subjectId());
                }
                if (!record.relationId().isBlank()) {
                    audiences.add(NPC_AUDIENCE_PREFIX + record.relationId());
                }
            }
            case WORLD -> {
                // World-scoped memories are intentionally not exposed by the player memory facade.
            }
        }
        return Set.copyOf(audiences);
    }

    private void evictExpired(MemoryRecord record) {
        if (record != null && activeRecords.remove(record.identityKey(), record)) {
            unindexRecord(record);
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
        boolean confirmation = previous != null && previous.value().equalsIgnoreCase(value);
        double reinforcedConfidence = confirmation
                ? Math.min(1.0D, Math.max(confidence, previous.confidence() + 0.025D))
                : confidence;
        double reinforcedSalience = confirmation
                ? Math.min(1.0D, Math.max(salience, previous.salience() + 0.035D))
                : salience;
        long expiresAt = kind == MemoryKind.GOAL
                ? System.currentTimeMillis() + Duration.ofDays(30).toMillis()
                : 0L;
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
                expiresAt,
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
        if (previous != null) {
            unindexRecord(previous);
        }
        indexRecord(record);
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
        unindexRecord(previous);
        long now = System.currentTimeMillis();
        writer.execute(() -> repository.upsert(previous.expireAt(now)));
    }

    private void forgetSemanticKey(MemoryScope scope, String subject, String relation, String key) {
        for (MemoryKind kind : PLAYER_SEMANTIC_KINDS) {
            forget(scope, subject, relation, kind, key);
        }
    }

    private void forgetConflictingSemanticKinds(
            MemoryScope scope,
            String subject,
            String relation,
            MemoryKind expectedKind,
            String key
    ) {
        if (scope != MemoryScope.PLAYER || !PLAYER_SEMANTIC_KINDS.contains(expectedKind)) {
            return;
        }
        for (MemoryKind kind : PLAYER_SEMANTIC_KINDS) {
            if (kind != expectedKind) {
                forget(scope, subject, relation, kind, key);
            }
        }
    }

    private void enforceLimit(MemoryScope scope, String subjectId, Set<MemoryKind> kinds, int limit) {
        String audience = scope == MemoryScope.GLOBAL ? GLOBAL_AUDIENCE : PLAYER_AUDIENCE_PREFIX + subjectId;
        Set<String> indexed = audienceIndex.getOrDefault(audience, Set.of());
        List<MemoryRecord> matching = indexed.stream()
                .map(activeRecords::get)
                .filter(java.util.Objects::nonNull)
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
                unindexRecord(record);
                long now = System.currentTimeMillis();
                writer.execute(() -> repository.upsert(record.expireAt(now)));
            }
        }
    }

    private double memoryScore(
            MemoryRecord record,
            Set<String> queryTerms,
            String normalizedQuery,
            String playerId,
            String npcId,
            long now
    ) {
        Set<String> terms = recordTerms(record);
        double lexical = overlapRatio(queryTerms, terms);
        double phrase = !normalizedQuery.isBlank()
                && (record.value().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || normalizedQuery.contains(record.value().toLowerCase(Locale.ROOT))) ? 1.0D : 0.0D;
        long age = Math.max(0L, now - record.lastConfirmed());
        double recency = 1.0D / (1.0D + age / (double) recencyHalfLife(record.kind()).toMillis());
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
            case GOAL -> 0.97D;
            case INTEREST -> 0.95D;
            case OPINION -> 0.92D;
            case FACT -> 0.90D;
            case RELATIONSHIP -> 0.78D;
            case EVENT, EPISODE -> 0.72D;
        };
        return record.salience() * 0.23D
                + record.confidence() * 0.16D
                + recency * 0.11D
                + lexical * 0.27D
                + phrase * 0.08D
                + scope * 0.08D
                + kind * 0.07D;
    }

    private Duration recencyHalfLife(MemoryKind kind) {
        return switch (kind) {
            case EVENT -> Duration.ofDays(3);
            case EPISODE -> Duration.ofDays(14);
            case GOAL -> Duration.ofDays(14);
            case RELATIONSHIP -> Duration.ofDays(60);
            case OPINION -> Duration.ofDays(90);
            case INTEREST -> Duration.ofDays(120);
            case PREFERENCE -> Duration.ofDays(180);
            case FACT -> Duration.ofDays(365);
        };
    }

    private Set<String> associationBridgeTerms(List<ScoredMemory> primary, Set<String> queryTerms) {
        if (queryTerms.isEmpty()) {
            return Set.of();
        }
        Set<String> bridge = new HashSet<>();
        int seeds = 0;
        for (ScoredMemory scored : primary) {
            Set<String> terms = recordTerms(scored.record());
            if (queryTerms.stream().noneMatch(terms::contains)) {
                continue;
            }
            for (String term : terms) {
                if (!queryTerms.contains(term)) {
                    bridge.add(term);
                }
            }
            if (++seeds >= ASSOCIATION_SEEDS) {
                break;
            }
        }
        return Set.copyOf(bridge);
    }

    private double associationBonus(MemoryRecord record, Set<String> bridgeTerms, Set<String> queryTerms) {
        if (bridgeTerms.isEmpty()) {
            return 0.0D;
        }
        Set<String> terms = recordTerms(record);
        long linked = bridgeTerms.stream().filter(terms::contains).count();
        if (linked == 0) {
            return 0.0D;
        }
        boolean direct = queryTerms.stream().anyMatch(terms::contains);
        double activation = Math.min(1.0D, linked / 4.0D);
        return activation * (direct ? 0.045D : 0.10D);
    }

    private List<MemoryRecord> selectDiverse(List<ScoredMemory> scored, int maximumResults) {
        List<MemoryRecord> selected = new ArrayList<>();
        for (ScoredMemory candidate : scored) {
            if (selected.size() >= maximumResults) {
                break;
            }
            boolean nearDuplicate = selected.stream().anyMatch(existing ->
                    tokenJaccard(existing, candidate.record()) >= 0.86D
                            && existing.kind() == candidate.record().kind()
            );
            if (!nearDuplicate) {
                selected.add(candidate.record());
            }
        }
        return List.copyOf(selected);
    }

    private double tokenJaccard(MemoryRecord left, MemoryRecord right) {
        Set<String> a = recordTerms(left);
        Set<String> b = recordTerms(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0D;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private double overlapRatio(Set<String> expected, Set<String> actual) {
        if (expected.isEmpty()) {
            return 0.0D;
        }
        long overlap = expected.stream().filter(actual::contains).count();
        return (double) overlap / expected.size();
    }

    private Set<String> recordTerms(MemoryRecord record) {
        return new HashSet<>(significantWords(
                record.key() + " " + record.value() + " " + String.join(" ", record.tags())
        ));
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
            case "interest" -> MemoryKind.INTEREST;
            case "goal" -> MemoryKind.GOAL;
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

        boolean directSupport = !valueTerms.isEmpty()
                && valueOverlap >= Math.min(2, Math.max(1, valueTerms.size()));
        boolean semanticSupport = keyOverlap >= 1 && valueOverlap >= 1;
        if (kind == MemoryKind.OPINION || kind == MemoryKind.PREFERENCE) {
            return (directSupport || semanticSupport)
                    && (hasPreferenceOrOpinionSignal(normalizedMessage) || hasRememberSignal(normalizedMessage)
                    || hasRepeatedTopic(playerId, valueTerms, messageTerms));
        }
        if (kind == MemoryKind.INTEREST) {
            return (directSupport || semanticSupport)
                    && (hasInterestSignal(normalizedMessage) || hasRememberSignal(normalizedMessage)
                    || hasRepeatedTopic(playerId, valueTerms, messageTerms));
        }
        if (kind == MemoryKind.GOAL) {
            return (directSupport || semanticSupport)
                    && (hasGoalSignal(normalizedMessage) || hasRememberSignal(normalizedMessage));
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

    private boolean safeMemory(String key, String value) {
        return safeKey(key)
                && safeValue(value)
                && !AssistantDataSafety.forbiddenDurableMemory(key, value);
    }

    private boolean safeKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            return false;
        }
        return key.matches("[a-z0-9._-]+")
                && !containsSensitiveTerm(key.replace('_', ' ').replace('.', ' '))
                && !AssistantDataSafety.forbiddenDurableMemory(key, "");
    }

    private boolean safeValue(String value) {
        String normalized = normalizeValue(value);
        return !normalized.isBlank()
                && normalized.length() <= MAX_VALUE_LENGTH
                && !containsSensitiveTerm(normalized)
                && !AssistantDataSafety.forbiddenDurableMemory("", normalized);
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

    private boolean hasInterestSignal(String message) {
        return containsAny(message,
                "ik ben geïnteresseerd", "ik ben geinteresseerd", "ik ben fan van", "ik speel veel", "ik doe graag",
                "ik bouw graag", "ik verzamel graag", "interesse in", "i am interested", "i'm interested", "im interested",
                "i am into", "i'm into", "im into", "i am a fan of", "i play a lot", "i enjoy"
        );
    }

    private boolean hasGoalSignal(String message) {
        return containsAny(message,
                "mijn doel", "ik wil bouwen", "ik wil maken", "ik wil halen", "ik probeer", "ik ben bezig met",
                "ik werk aan", "ik spaar voor", "my goal", "i want to build", "i want to make", "i want to get",
                "i am trying", "i'm trying", "im trying", "i am working on", "i'm working on", "im working on",
                "i am saving for", "i'm saving for", "im saving for"
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
        return significantWords(key.replace('_', ' ').replace('.', ' ')).stream()
                .limit(6)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<String> valueTags(String value) {
        return significantWords(value).stream()
                .distinct()
                .filter(term -> term.length() >= 4)
                .limit(8)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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

    private record ScoredMemory(MemoryRecord record, double score) {
    }
}
