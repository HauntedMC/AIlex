package nl.hauntedmc.ailex.assistant.infrastructure.knowledge;

import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local hybrid knowledge index. Exact commands and server terminology remain lexical-first, while learned embeddings
 * supply real semantic/paraphrase recall. Ranking fuses BM25, exact/title/alias signals, multilingual concept expansion,
 * neural cosine similarity and reciprocal-rank evidence before adaptive second-stage diversity selection.
 */
public final class LocalKnowledgeIndex {

    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}/+]+");
    private static final double MINIMUM_SEMANTIC_SIMILARITY = 0.20D;
    private static final double SEMANTIC_WEIGHT = 2.4D;
    private static final double RRF_WEIGHT = 8.0D;
    private static final double RRF_K = 60.0D;
    private static final Set<String> STOP_WORDS = Set.of(
            "aan", "als", "and", "are", "bij", "can", "dan", "dat", "de", "die", "dit", "een",
            "en", "for", "hauntedmc", "haunty", "het", "hoe", "ik", "in", "is", "je", "met",
            "minecraft", "mijn", "naar", "of", "om", "op", "the", "to", "van", "wat", "wel", "wie",
            "with", "you", "your"
    );
    private static final Map<String, Set<String>> CONCEPTS = conceptMap();

    private final JavaPlugin plugin;
    private final KnowledgeDocumentParser documentParser = new KnowledgeDocumentParser();
    private final boolean managedEmbeddingProvider;
    private volatile SemanticEmbeddingProvider embeddingProvider;
    private final AtomicBoolean semanticWarmupRunning = new AtomicBoolean();
    private volatile List<KnowledgeChunk> chunks = List.of();
    private volatile Map<String, Integer> documentFrequency = Map.of();
    private final Map<String, CachedSearch> searchCache = new ConcurrentHashMap<>();
    private final Map<String, double[]> semanticVectors = new ConcurrentHashMap<>();

    public LocalKnowledgeIndex(JavaPlugin plugin) {
        this(plugin, null, true);
    }

    LocalKnowledgeIndex(JavaPlugin plugin, SemanticEmbeddingProvider embeddingProvider) {
        this(plugin, embeddingProvider, false);
    }

    private LocalKnowledgeIndex(
            JavaPlugin plugin,
            SemanticEmbeddingProvider embeddingProvider,
            boolean managedEmbeddingProvider
    ) {
        this.plugin = plugin;
        this.managedEmbeddingProvider = managedEmbeddingProvider;
        this.embeddingProvider = managedEmbeddingProvider ? new OpenAiEmbeddingProvider(plugin) : embeddingProvider;
        reload();
    }

    /** Rebuilds the immutable in-memory index after configuration or knowledge changes. */
    public void reload() {
        List<KnowledgeChunk> loaded = new ArrayList<>();
        FileConfiguration config = plugin.getConfig();
        if (config != null && config.getBoolean("openai.knowledge.enabled", true)) {
            String inlineKnowledge = config.getString("openai.knowledge.prompt", "");
            if (inlineKnowledge != null && !inlineKnowledge.isBlank()) {
                loaded.addAll(parseDocument("config.knowledge", inlineKnowledge));
            }
            loaded.addAll(loadKnowledgeFiles(config, AssistantSettings.from(config)));
        }
        chunks = List.copyOf(loaded);
        documentFrequency = buildDocumentFrequency(chunks);
        semanticVectors.clear();
        if (managedEmbeddingProvider) {
            embeddingProvider = new OpenAiEmbeddingProvider(plugin);
        } else if (embeddingProvider instanceof OpenAiEmbeddingProvider provider) {
            provider.clearCache();
        }
        searchCache.clear();
        if (managedEmbeddingProvider) {
            warmSemanticIndexAsync();
        }
    }

    /** Shares the same learned embedding provider/cache with semantic routing rather than duplicating API calls. */
    public SemanticEmbeddingProvider semanticEmbeddingProvider() {
        return embeddingProvider;
    }

    /** Query-focused retrieval for concrete server/gameplay questions. */
    public List<KnowledgeChunk> search(String query, AssistantSettings settings) {
        String normalizedQuery = query == null ? "" : query.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        String cacheKey = "search-v2|" + normalizedQuery + '|' + settings.maxChunks() + '|'
                + settings.maxEvidenceCharacters() + '|' + settings.excludeExpired() + '|' + settings.hybridRetrieval();
        CachedSearch cached = searchCache.get(cacheKey);
        if (cached != null && cached.isFresh(settings.queryCacheSeconds())) {
            return cached.chunks();
        }
        List<KnowledgeChunk> localChunks = chunks;
        if (localChunks.isEmpty()) {
            return List.of();
        }

        QueryFeatures features = queryFeatures(normalizedQuery);
        List<ScoredChunk> lexicalRanking = lexicalRanking(localChunks, features, settings);
        Map<String, Integer> lexicalRanks = rankMap(lexicalRanking);
        Map<String, Double> semanticScores = settings.hybridRetrieval()
                ? semanticScores(normalizedQuery, localChunks)
                : Map.of();
        Map<String, Integer> semanticRanks = semanticRankMap(semanticScores);

        List<ScoredChunk> fused = new ArrayList<>();
        for (KnowledgeChunk chunk : localChunks) {
            double lexical = lexicalRanking.stream()
                    .filter(scored -> scored.chunk().id().equals(chunk.id()))
                    .mapToDouble(ScoredChunk::score)
                    .findFirst()
                    .orElse(0.0D);
            double semantic = semanticScores.getOrDefault(chunk.id(), 0.0D);
            if (lexical <= 0.0D && semantic <= 0.0D) {
                continue;
            }
            double rrf = reciprocalRank(lexicalRanks.get(chunk.id())) + reciprocalRank(semanticRanks.get(chunk.id()));
            double combined = lexical + semantic * SEMANTIC_WEIGHT + rrf * RRF_WEIGHT
                    + freshnessWeight(chunk) * 0.30D;
            if (combined > 0.0D && eligible(chunk, settings)) {
                fused.add(new ScoredChunk(chunk, combined));
            }
        }
        fused.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        double topScore = fused.isEmpty() ? 0.0D : fused.getFirst().score();
        double secondScore = fused.size() < 2 ? 0.0D : fused.get(1).score();
        AdaptiveEvidencePolicy.Budget selection = AdaptiveEvidencePolicy.select(
                normalizedQuery,
                settings.maxChunks(),
                settings.maxEvidenceCharacters(),
                topScore,
                secondScore
        );
        List<KnowledgeChunk> selected = selectDiverse(
                fused, selection.maxChunks(), selection.maxCharacters()
        );
        searchCache.put(cacheKey, new CachedSearch(List.copyOf(selected), System.currentTimeMillis()));
        return selected;
    }

    /** Source-compatible discovery entry point used by the assistant orchestration. */
    public List<KnowledgeChunk> discover(String requesterSeed, AssistantSettings settings) {
        return discover(settings, requesterSeed);
    }

    /** Open-ended corpus discovery intentionally avoids pretending a vague prompt is a lexical query. */
    public List<KnowledgeChunk> discover(AssistantSettings settings, String requesterSeed) {
        List<KnowledgeChunk> eligible = chunks.stream().filter(chunk -> eligible(chunk, settings)).toList();
        if (eligible.isEmpty()) {
            return List.of();
        }
        int seed = requesterSeed == null ? 0 : requesterSeed.hashCode();
        List<ScoredChunk> ranked = new ArrayList<>();
        for (int index = 0; index < eligible.size(); index++) {
            KnowledgeChunk chunk = eligible.get(index);
            double authority = authorityWeight(chunk.authority());
            double deterministicJitter = Math.floorMod(seed ^ chunk.id().hashCode(), 10_000) / 10_000.0D;
            ranked.add(new ScoredChunk(chunk, authority + deterministicJitter));
        }
        ranked.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return selectDiverse(ranked, Math.min(settings.maxChunks(), 8), settings.maxEvidenceCharacters());
    }

    public int size() {
        return chunks.size();
    }

    public boolean learnedSemanticAvailable() {
        return embeddingProvider != null && embeddingProvider.available() && !semanticVectors.isEmpty();
    }

    /** Compatibility name retained for status/diagnostic callers. */
    public boolean learnedSemanticRetrievalAvailable() {
        return learnedSemanticAvailable();
    }

    private List<ScoredChunk> lexicalRanking(
            List<KnowledgeChunk> candidates,
            QueryFeatures features,
            AssistantSettings settings
    ) {
        List<ScoredChunk> scored = new ArrayList<>();
        for (KnowledgeChunk chunk : candidates) {
            if (!eligible(chunk, settings)) {
                continue;
            }
            double score = score(chunk, features);
            if (score > 0.0D) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return scored;
    }

    private Map<String, Double> semanticScores(String query, List<KnowledgeChunk> candidates) {
        SemanticEmbeddingProvider provider = embeddingProvider;
        if (provider == null || query.isBlank() || !provider.available()) {
            return Map.of();
        }
        ensureSemanticVectors(candidates, provider);
        warmSemanticIndexAsync();
        List<double[]> queryVector = provider.embed(List.of(query));
        if (queryVector.size() != 1 || queryVector.getFirst().length == 0) {
            return Map.of();
        }
        double[] queryEmbedding = queryVector.getFirst();
        Map<String, Double> scores = new HashMap<>();
        for (KnowledgeChunk chunk : candidates) {
            double[] vector = semanticVectors.get(chunk.id());
            if (vector == null) {
                continue;
            }
            double similarity = cosine(queryEmbedding, vector);
            if (similarity >= MINIMUM_SEMANTIC_SIMILARITY) {
                scores.put(chunk.id(), similarity);
            }
        }
        return Map.copyOf(scores);
    }

    /** Ensures a cold query has document vectors instead of silently degrading a semantic-only request. */
    private void ensureSemanticVectors(List<KnowledgeChunk> candidates, SemanticEmbeddingProvider provider) {
        List<KnowledgeChunk> missing = candidates.stream()
                .filter(chunk -> !semanticVectors.containsKey(chunk.id()))
                .toList();
        for (int offset = 0; offset < missing.size(); offset += 48) {
            List<KnowledgeChunk> batch = missing.subList(offset, Math.min(missing.size(), offset + 48));
            List<double[]> vectors = provider.embed(batch.stream().map(this::embeddingText).toList());
            if (vectors.size() != batch.size()) {
                return;
            }
            for (int index = 0; index < batch.size(); index++) {
                double[] vector = vectors.get(index);
                if (vector != null && vector.length > 0) {
                    semanticVectors.putIfAbsent(batch.get(index).id(), vector);
                }
            }
        }
    }

    private void warmSemanticIndexAsync() {
        SemanticEmbeddingProvider provider = embeddingProvider;
        if (provider == null || !provider.available() || chunks.isEmpty() || semanticVectors.size() >= chunks.size()
                || !semanticWarmupRunning.compareAndSet(false, true)) {
            return;
        }
        List<KnowledgeChunk> snapshot = chunks;
        CompletableFuture.runAsync(() -> {
            try {
                for (int offset = 0; offset < snapshot.size(); offset += 48) {
                    List<KnowledgeChunk> batch = snapshot.subList(offset, Math.min(snapshot.size(), offset + 48));
                    List<KnowledgeChunk> missing = batch.stream()
                            .filter(chunk -> !semanticVectors.containsKey(chunk.id()))
                            .toList();
                    if (missing.isEmpty()) {
                        continue;
                    }
                    List<double[]> vectors = provider.embed(missing.stream().map(this::embeddingText).toList());
                    if (vectors.size() != missing.size()) {
                        return;
                    }
                    for (int index = 0; index < missing.size(); index++) {
                        semanticVectors.put(missing.get(index).id(), vectors.get(index));
                    }
                }
            } catch (RuntimeException exception) {
                LoggerUtils.logWarning("[AIlex retrieval] Semantic corpus warmup failed; lexical retrieval remains active: "
                        + exception.getMessage());
            } finally {
                semanticWarmupRunning.set(false);
            }
        });
    }

    private String embeddingText(KnowledgeChunk chunk) {
        return (chunk.title() + "\n" + String.join(" ", chunk.aliases()) + "\n" + chunk.text()).trim();
    }

    private Map<String, Integer> rankMap(List<ScoredChunk> ranking) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int index = 0; index < ranking.size(); index++) {
            ranks.putIfAbsent(ranking.get(index).chunk().id(), index + 1);
        }
        return ranks;
    }

    private Map<String, Integer> semanticRankMap(Map<String, Double> scores) {
        List<Map.Entry<String, Double>> ordered = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();
        Map<String, Integer> ranks = new HashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            ranks.put(ordered.get(index).getKey(), index + 1);
        }
        return ranks;
    }

    private double reciprocalRank(Integer rank) {
        return rank == null ? 0.0D : 1.0D / (RRF_K + rank);
    }

    private List<KnowledgeChunk> selectDiverse(List<ScoredChunk> ranked, int maxChunks, int maxCharacters) {
        List<KnowledgeChunk> selected = new ArrayList<>();
        Set<String> normalizedFingerprints = new LinkedHashSet<>();
        int characters = 0;
        for (ScoredChunk scored : ranked) {
            KnowledgeChunk chunk = scored.chunk();
            String fingerprint = normalizeForDedup(chunk.title() + ' ' + chunk.text());
            boolean duplicate = normalizedFingerprints.stream()
                    .anyMatch(existing -> similarity(existing, fingerprint) > 0.82D);
            if (duplicate) {
                continue;
            }
            if (!selected.isEmpty() && characters + chunk.text().length() > maxCharacters) {
                continue;
            }
            selected.add(chunk);
            normalizedFingerprints.add(fingerprint);
            characters += chunk.text().length();
            if (selected.size() >= maxChunks) {
                break;
            }
        }
        return List.copyOf(selected);
    }

    private double score(KnowledgeChunk chunk, QueryFeatures query) {
        List<String> tokens = tokens(chunk.title() + " " + String.join(" ", chunk.aliases()) + " " + chunk.text());
        Map<String, Integer> termFrequency = new HashMap<>();
        tokens.forEach(token -> termFrequency.merge(token, 1, Integer::sum));
        int length = Math.max(1, tokens.size());
        double score = 0.0D;
        for (String term : query.expandedTerms()) {
            int frequency = termFrequency.getOrDefault(term, 0);
            if (frequency <= 0) {
                continue;
            }
            int df = documentFrequency.getOrDefault(term, 0);
            double idf = Math.log(1.0D + (chunks.size() - df + 0.5D) / (df + 0.5D));
            score += idf * (frequency * 2.2D) / (frequency + 1.2D * (0.25D + 0.75D * length / 220.0D));
        }
        String queryText = query.normalized();
        if (!queryText.isBlank()) {
            String lowerTitle = chunk.title().toLowerCase(Locale.ROOT);
            String lowerText = chunk.text().toLowerCase(Locale.ROOT);
            if (lowerTitle.contains(queryText)) {
                score += 8.0D;
            }
            if (lowerText.contains(queryText)) {
                score += 4.0D;
            }
            for (String alias : chunk.aliases()) {
                if (queryText.contains(alias.toLowerCase(Locale.ROOT))) {
                    score += 6.0D;
                }
            }
        }
        for (String token : query.baseTerms()) {
            if (token.startsWith("/") && (chunk.text().contains(token) || chunk.title().contains(token))) {
                score += 10.0D;
            }
        }
        return score * authorityWeight(chunk.authority());
    }

    private QueryFeatures queryFeatures(String query) {
        List<String> base = tokens(query).stream().filter(token -> !STOP_WORDS.contains(token)).distinct().toList();
        Set<String> expanded = new LinkedHashSet<>(base);
        for (String term : base) {
            Set<String> concept = CONCEPTS.get(term);
            if (concept != null) {
                expanded.addAll(concept);
            }
        }
        return new QueryFeatures(query, List.copyOf(base), Set.copyOf(expanded));
    }

    private List<String> tokens(String value) {
        return TOKEN_SEPARATOR.splitAsStream(value == null ? "" : value.toLowerCase(Locale.ROOT))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private Map<String, Integer> buildDocumentFrequency(List<KnowledgeChunk> documents) {
        Map<String, Integer> frequency = new HashMap<>();
        for (KnowledgeChunk chunk : documents) {
            Set<String> unique = new HashSet<>(tokens(chunk.title() + " " + chunk.text()));
            unique.forEach(token -> frequency.merge(token, 1, Integer::sum));
        }
        return Map.copyOf(frequency);
    }

    private boolean eligible(KnowledgeChunk chunk, AssistantSettings settings) {
        return !settings.excludeExpired() || !chunk.expired();
    }

    private double authorityWeight(String authority) {
        return switch (authority == null ? "" : authority.toLowerCase(Locale.ROOT)) {
            case "operator-confirmed" -> 1.32D;
            case "official" -> 1.25D;
            case "reviewed" -> 1.15D;
            case "trusted" -> 1.08D;
            default -> 1.0D;
        };
    }

    private double freshnessWeight(KnowledgeChunk chunk) {
        if (chunk == null || chunk.updated().isBlank()) {
            return 0.35D;
        }
        try {
            long days = Math.max(0L, java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.parse(chunk.updated()), LocalDate.now()
            ));
            boolean volatileCategory = chunk.category().contains("current") || chunk.category().contains("event")
                    || chunk.category().contains("status");
            double halfLife = volatileCategory ? 45.0D : 365.0D;
            return 1.0D / (1.0D + days / halfLife);
        } catch (DateTimeParseException ignored) {
            return 0.35D;
        }
    }

    private double cosine(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) {
            return 0.0D;
        }
        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm <= 0.0D || rightNorm <= 0.0D) {
            return 0.0D;
        }
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    private String normalizeForDedup(String value) {
        return String.join(" ", tokens(value).stream().distinct().sorted().toList());
    }

    private double similarity(String left, String right) {
        Set<String> leftTerms = new HashSet<>(tokens(left));
        Set<String> rightTerms = new HashSet<>(tokens(right));
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) {
            return 0.0D;
        }
        Set<String> intersection = new HashSet<>(leftTerms);
        intersection.retainAll(rightTerms);
        Set<String> union = new HashSet<>(leftTerms);
        union.addAll(rightTerms);
        return (double) intersection.size() / union.size();
    }

    private static Map<String, Set<String>> conceptMap() {
        Map<String, Set<String>> map = new HashMap<>();
        Set<String> currency = Set.of("currency", "valuta", "money", "geld", "balance", "saldo", "credits", "crowns");
        Set<String> claims = Set.of("claim", "claims", "protect", "protection", "bescherm", "bescherming", "land");
        Set<String> ranks = Set.of("rank", "ranks", "donor", "perk", "perks", "voordeel", "voordelen");
        Set<String> vote = Set.of("vote", "votes", "voten", "stem", "stemmen", "reward", "rewards");
        for (String term : currency) {
            map.put(term, currency);
        }
        for (String term : claims) {
            map.put(term, claims);
        }
        for (String term : ranks) {
            map.put(term, ranks);
        }
        for (String term : vote) {
            map.put(term, vote);
        }
        return Map.copyOf(map);
    }

    private List<KnowledgeChunk> loadKnowledgeFiles(FileConfiguration config, AssistantSettings settings) {
        if (!config.getBoolean("openai.knowledge.external.enabled", true)) {
            return List.of();
        }
        String configuredDirectory = config.getString("openai.knowledge.external.directory", "knowledge");
        File directory = new File(plugin.getDataFolder(), configuredDirectory == null ? "knowledge" : configuredDirectory);
        if (!directory.exists() || !directory.isDirectory()) {
            return List.of();
        }
        int maxFiles = Math.clamp(config.getInt("openai.knowledge.external.max_files", 64), 1, 256);
        int maxCharacters = Math.clamp(config.getInt(
                "openai.knowledge.external.max_characters", 120_000
        ), 1_000, 1_000_000);
        List<Path> files;
        try (Stream<Path> stream = Files.list(directory.toPath())) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted()
                    .limit(maxFiles)
                    .toList();
        } catch (IOException exception) {
            LoggerUtils.logWarning("Could not scan external knowledge directory: " + exception.getMessage());
            return List.of();
        }
        List<KnowledgeChunk> loaded = new ArrayList<>();
        int characters = 0;
        for (Path path : files) {
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (content.length() + characters > maxCharacters) {
                    content = content.substring(0, Math.max(0, maxCharacters - characters));
                }
                loaded.addAll(parseDocument("file." + path.getFileName(), content));
                characters += content.length();
                if (characters >= maxCharacters) {
                    break;
                }
            } catch (IOException exception) {
                LoggerUtils.logWarning("Could not read knowledge file " + path.getFileName() + ": " + exception.getMessage());
            }
        }
        return List.copyOf(loaded);
    }

    private List<KnowledgeChunk> parseDocument(String source, String content) {
        return documentParser.parse(source, content).stream()
                .map(section -> new KnowledgeChunk(
                        section.id(), section.title(), section.aliases(), section.text(), section.expired(),
                        section.category(), section.authority(), section.source(), section.updated()
                ))
                .toList();
    }

    private Map<String, String> metadata(String body) {
        Map<String, String> result = new HashMap<>();
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("@") || !trimmed.contains(":")) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            result.put(
                    trimmed.substring(1, separator).trim().toLowerCase(Locale.ROOT),
                    trimmed.substring(separator + 1).trim()
            );
        }
        return result;
    }

    private String stripMetadata(String body) {
        return body.lines()
                .filter(line -> !line.trim().startsWith("@"))
                .collect(java.util.stream.Collectors.joining("\n"))
                .trim();
    }

    private List<String> aliases(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .limit(24)
                .toList();
    }

    private boolean expired(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            return LocalDate.parse(raw.trim()).isBefore(LocalDate.now());
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private String safeId(String value) {
        String id = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-");
        return id.length() <= 96 ? id : id.substring(0, 96);
    }

    public record KnowledgeChunk(
            String id,
            String title,
            List<String> aliases,
            String text,
            boolean expired,
            String category,
            String authority,
            String source,
            String updated
    ) {
        public KnowledgeChunk {
            id = id == null ? "" : id;
            title = title == null ? "" : title;
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            text = text == null ? "" : text;
            category = category == null ? "" : category;
            authority = authority == null ? "" : authority;
            source = source == null ? "" : source;
            updated = updated == null ? "" : updated;
        }

        /** Source-compatible constructor retained for deterministic tests/integrations. */
        public KnowledgeChunk(
                String id,
                String title,
                List<String> aliases,
                String text,
                boolean expired,
                String category,
                String authority
        ) {
            this(id, title, aliases, text, expired, category, authority, "", "");
        }
    }

    private record QueryFeatures(String normalized, List<String> baseTerms, Set<String> expandedTerms) {
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {
    }

    private record CachedSearch(List<KnowledgeChunk> chunks, long createdAt) {
        private boolean isFresh(long maxAgeSeconds) {
            return System.currentTimeMillis() - createdAt <= maxAgeSeconds * 1_000L;
        }
    }
}
