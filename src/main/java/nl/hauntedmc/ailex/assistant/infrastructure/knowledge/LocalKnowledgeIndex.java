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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local hybrid knowledge index. Query retrieval combines BM25, exact command/title signals, multilingual concept
 * expansion, a compact dense projection, phrase matching and redundancy suppression. Open-ended discovery uses a
 * separate diversity sampler so requests such as "tell me a fun fact" do not fail merely because the query has no
 * useful lexical terms.
 */
public final class LocalKnowledgeIndex {

    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}/+]+");
    private static final int DENSE_DIMENSIONS = 256;
    private static final Set<String> STOP_WORDS = Set.of(
            "aan", "als", "and", "are", "bij", "can", "dan", "dat", "de", "die", "dit", "een",
            "en", "for", "hauntedmc", "haunty", "het", "hoe", "ik", "in", "is", "je", "met",
            "minecraft", "mijn", "naar", "of", "om", "op", "the", "to", "van", "wat", "wel", "wie",
            "with", "you", "your"
    );
    private static final Map<String, Set<String>> CONCEPTS = conceptMap();

    private final JavaPlugin plugin;
    private volatile List<KnowledgeChunk> chunks = List.of();
    private volatile Map<String, Integer> documentFrequency = Map.of();
    private final Map<String, CachedSearch> searchCache = new ConcurrentHashMap<>();

    public LocalKnowledgeIndex(JavaPlugin plugin) {
        this.plugin = plugin;
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
        searchCache.clear();
    }

    /** Query-focused retrieval for concrete server/gameplay questions. */
    public List<KnowledgeChunk> search(String query, AssistantSettings settings) {
        String normalizedQuery = query == null ? "" : query.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        String cacheKey = "search|" + normalizedQuery + '|' + settings.maxChunks() + '|'
                + settings.maxEvidenceCharacters() + '|' + settings.excludeExpired() + '|' + settings.hybridRetrieval();
        CachedSearch cached = searchCache.get(cacheKey);
        if (cached != null && cached.isFresh(settings.queryCacheSeconds())) {
            return cached.results();
        }

        List<String> baseTokens = tokenize(query);
        if (chunks.isEmpty() || baseTokens.isEmpty()) {
            return List.of();
        }
        List<String> queryTokens = settings.hybridRetrieval() ? expandConcepts(baseTokens) : baseTokens;
        double[] queryVector = settings.hybridRetrieval() ? denseVector(queryTokens) : new double[0];
        int documentCount = chunks.size();
        double averageLength = chunks.stream().mapToInt(chunk -> chunk.tokens().size()).average().orElse(1.0D);
        List<ScoredChunk> scored = new ArrayList<>();

        for (KnowledgeChunk chunk : chunks) {
            if (settings.excludeExpired() && chunk.expired()) {
                continue;
            }
            double score = bm25(chunk, queryTokens, documentCount, averageLength);
            if (settings.hybridRetrieval()) {
                score += hybridBoost(chunk, normalizedQuery, baseTokens, queryVector);
            }
            if (score > minimumScore(baseTokens)) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed()
                .thenComparing(scoredChunk -> scoredChunk.chunk().id()));

        List<KnowledgeChunk> selected = selectDiverse(scored, settings, settings.maxChunks());
        cache(cacheKey, selected, settings);
        return selected;
    }

    /**
     * Browses the knowledge corpus without requiring query overlap. Selection is deterministic for a seed, source-
     * diverse, authority-aware and biased toward concrete positive facts instead of short negations.
     */
    public List<KnowledgeChunk> discover(String seed, AssistantSettings settings) {
        String normalizedSeed = seed == null ? "" : seed.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        String cacheKey = "discover|" + normalizedSeed + '|' + settings.maxChunks() + '|'
                + settings.maxEvidenceCharacters() + '|' + settings.excludeExpired();
        CachedSearch cached = searchCache.get(cacheKey);
        if (cached != null && cached.isFresh(settings.queryCacheSeconds())) {
            return cached.results();
        }
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            if (settings.excludeExpired() && chunk.expired()) {
                continue;
            }
            double score = discoveryScore(chunk, normalizedSeed);
            scored.add(new ScoredChunk(chunk, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed()
                .thenComparing(scoredChunk -> scoredChunk.chunk().id()));

        int discoveryLimit = Math.min(settings.maxChunks(), 8);
        List<KnowledgeChunk> selected = selectDiverse(scored, settings, discoveryLimit);
        cache(cacheKey, selected, settings);
        return selected;
    }

    public int size() {
        return chunks.size();
    }

    private void cache(String key, List<KnowledgeChunk> selected, AssistantSettings settings) {
        if (settings.queryCacheSeconds() > 0) {
            if (searchCache.size() > 512) {
                searchCache.clear();
            }
            searchCache.put(key, new CachedSearch(System.currentTimeMillis(), selected));
        }
    }

    private List<KnowledgeChunk> selectDiverse(
            List<ScoredChunk> scored, AssistantSettings settings, int maximumChunks
    ) {
        List<KnowledgeChunk> selected = new ArrayList<>();
        Set<String> documents = new HashSet<>();
        Set<String> categories = new HashSet<>();
        int usedCharacters = 0;

        for (ScoredChunk candidate : scored) {
            KnowledgeChunk chunk = candidate.chunk();
            int next = chunk.text().length() + chunk.title().length() + 48;
            if (selected.size() >= maximumChunks || usedCharacters + next > settings.maxEvidenceCharacters()) {
                continue;
            }
            boolean nearDuplicate = selected.stream().anyMatch(existing -> tokenJaccard(existing, chunk) >= 0.78D);
            if (nearDuplicate) {
                continue;
            }

            String document = documentKey(chunk.id());
            boolean alreadyDocument = documents.contains(document);
            boolean alreadyCategory = !chunk.category().isBlank() && categories.contains(chunk.category());
            int remainingCandidates = maximumChunks - selected.size();
            if (alreadyDocument && selected.size() >= 2 && remainingCandidates > 1) {
                continue;
            }
            if (alreadyCategory && selected.size() >= 3 && remainingCandidates > 1) {
                continue;
            }

            selected.add(chunk);
            usedCharacters += next;
            documents.add(document);
            if (!chunk.category().isBlank()) {
                categories.add(chunk.category());
            }
        }

        // If strict diversity left capacity unused, fill remaining slots by score while still suppressing duplicates.
        if (selected.size() < maximumChunks) {
            for (ScoredChunk candidate : scored) {
                KnowledgeChunk chunk = candidate.chunk();
                if (selected.contains(chunk)) {
                    continue;
                }
                int next = chunk.text().length() + chunk.title().length() + 48;
                if (selected.size() >= maximumChunks || usedCharacters + next > settings.maxEvidenceCharacters()) {
                    break;
                }
                if (selected.stream().anyMatch(existing -> tokenJaccard(existing, chunk) >= 0.78D)) {
                    continue;
                }
                selected.add(chunk);
                usedCharacters += next;
            }
        }
        return List.copyOf(selected);
    }

    private double bm25(KnowledgeChunk chunk, List<String> queryTokens, int documentCount, double averageLength) {
        double score = 0.0D;
        for (String token : queryTokens) {
            int frequency = frequency(chunk.tokens(), token);
            if (frequency == 0) {
                continue;
            }
            int matchingDocuments = documentFrequency.getOrDefault(token, 0);
            double idf = Math.log(1.0D + (documentCount - matchingDocuments + 0.5D)
                    / (matchingDocuments + 0.5D));
            double denominator = frequency + 1.2D * (1.0D - 0.75D
                    + 0.75D * chunk.tokens().size() / averageLength);
            score += idf * frequency * 2.2D / denominator;
        }
        return score;
    }

    private double hybridBoost(
            KnowledgeChunk chunk,
            String normalizedQuery,
            List<String> baseTokens,
            double[] queryVector
    ) {
        String title = chunk.title().toLowerCase(Locale.ROOT);
        String body = chunk.text().toLowerCase(Locale.ROOT);
        double boost = 0.0D;
        for (String token : baseTokens) {
            if (title.contains(token)) {
                boost += 1.8D;
            }
            if (chunk.aliases().stream().anyMatch(alias -> alias.equals(token) || alias.contains(token))) {
                boost += token.startsWith("/") ? 6.0D : 2.8D;
            }
            if (token.startsWith("/") && (body.contains(token) || title.contains(token))) {
                boost += 8.0D;
            }
        }
        if (normalizedQuery.length() >= 8 && body.contains(normalizedQuery)) {
            boost += 4.0D;
        }
        double denseSimilarity = cosine(queryVector, denseVector(chunk.tokens()));
        boost += Math.max(0.0D, denseSimilarity) * 3.2D;
        if ("official".equals(chunk.authority())) {
            boost += 0.25D;
        }
        return boost;
    }

    private double discoveryScore(KnowledgeChunk chunk, String seed) {
        String body = chunk.text().toLowerCase(Locale.ROOT);
        double authority = "official".equals(chunk.authority()) ? 1.0D : 0.55D;
        double detail = Math.min(1.0D, chunk.tokens().size() / 24.0D);
        double concrete = containsAny(body, "/", "can ", "has ", "provides ", "use ", "supports ", "wordt ",
                "heeft ", "kan ", "gebruik ", "players ", "spelers ") ? 0.35D : 0.0D;
        double negativePenalty = isMostlyNegative(chunk) ? 0.65D : 0.0D;
        int hash = (seed + '|' + chunk.id()).hashCode();
        double rotation = Math.floorMod(hash, 10_000) / 10_000.0D;
        return authority * 0.8D + detail * 0.45D + concrete + rotation * 0.7D - negativePenalty;
    }

    private boolean isMostlyNegative(KnowledgeChunk chunk) {
        String text = chunk.text().toLowerCase(Locale.ROOT);
        boolean negative = containsAny(text, " is not ", " isn't ", " not active", " geen ", " niet ",
                "does not", "cannot", "can't ");
        boolean positive = containsAny(text, " can ", " has ", " use ", " provides ", " supports ", " kan ",
                " heeft ", " gebruik ", " biedt ");
        return negative && !positive;
    }

    private double minimumScore(List<String> queryTokens) {
        return queryTokens.stream().anyMatch(token -> token.startsWith("/")) ? 0.20D : 0.35D;
    }

    private Map<String, Integer> buildDocumentFrequency(List<KnowledgeChunk> sourceChunks) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (KnowledgeChunk chunk : sourceChunks) {
            for (String token : new HashSet<>(chunk.tokens())) {
                frequencies.merge(token, 1, Integer::sum);
            }
        }
        return Map.copyOf(frequencies);
    }

    private List<String> expandConcepts(List<String> tokens) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>(tokens);
        for (String token : tokens) {
            Set<String> concept = CONCEPTS.get(token);
            if (concept != null) {
                expanded.addAll(concept);
            }
        }
        return List.copyOf(expanded);
    }

    private double[] denseVector(List<String> tokens) {
        double[] vector = new double[DENSE_DIMENSIONS];
        if (tokens.isEmpty()) {
            return vector;
        }
        for (int index = 0; index < tokens.size(); index++) {
            addFeature(vector, tokens.get(index), 1.0D);
            if (index + 1 < tokens.size()) {
                addFeature(vector, tokens.get(index) + '|' + tokens.get(index + 1), 0.65D);
            }
            if (index + 2 < tokens.size()) {
                addFeature(vector, tokens.get(index) + '|' + tokens.get(index + 2), 0.25D);
            }
        }
        return vector;
    }

    private void addFeature(double[] vector, String feature, double weight) {
        int hash = feature.hashCode();
        int bucket = Math.floorMod(hash, vector.length);
        vector[bucket] += (hash & 1) == 0 ? weight : -weight;
    }

    private double cosine(double[] left, double[] right) {
        if (left.length == 0 || right.length == 0 || left.length != right.length) {
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
        if (leftNorm == 0.0D || rightNorm == 0.0D) {
            return 0.0D;
        }
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    private double tokenJaccard(KnowledgeChunk left, KnowledgeChunk right) {
        Set<String> a = new HashSet<>(left.tokens());
        Set<String> b = new HashSet<>(right.tokens());
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0D;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private int frequency(List<String> tokens, String expected) {
        return (int) tokens.stream().filter(expected::equals).count();
    }

    private String documentKey(String id) {
        int separator = id == null ? -1 : id.lastIndexOf('.');
        return separator <= 0 ? String.valueOf(id) : id.substring(0, separator);
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private record CachedSearch(long createdAtMillis, List<KnowledgeChunk> results) {
        private boolean isFresh(int ttlSeconds) {
            return ttlSeconds > 0 && System.currentTimeMillis() - createdAtMillis < ttlSeconds * 1000L;
        }
    }

    private List<KnowledgeChunk> loadKnowledgeFiles(FileConfiguration config, AssistantSettings settings) {
        if (!settings.externalKnowledgeEnabled()) {
            return List.of();
        }
        String directoryName = config.getString("openai.knowledge.external.directory", "knowledge");
        if (directoryName == null || directoryName.isBlank()) {
            return List.of();
        }
        File dataFolder = plugin.getDataFolder();
        if (dataFolder == null) {
            return List.of();
        }
        Path root = dataFolder.toPath().toAbsolutePath().normalize();
        Path directory = root.resolve(directoryName).normalize();
        if (!directory.startsWith(root) || !Files.isDirectory(directory)) {
            return List.of();
        }
        List<KnowledgeChunk> loaded = new ArrayList<>();
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isKnowledgeFile)
                    .sorted()
                    .limit(settings.externalMaxFiles())
                    .forEach(path -> readKnowledgeFile(path, loaded, settings.externalMaxCharacters()));
        } catch (IOException exception) {
            LoggerUtils.logWarning("Could not load assistant knowledge: " + exception.getMessage());
        }
        return loaded;
    }

    private boolean isKnowledgeFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return !"readme.md".equals(name) && (name.endsWith(".md") || name.endsWith(".txt"));
    }

    private void readKnowledgeFile(Path path, List<KnowledgeChunk> loaded, int maxCharacters) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            loaded.addAll(parseDocument(path.getFileName().toString(),
                    content.substring(0, Math.min(content.length(), maxCharacters))));
        } catch (IOException exception) {
            LoggerUtils.logWarning("Could not read assistant knowledge " + path.getFileName() + ": "
                    + exception.getMessage());
        }
    }

    private List<KnowledgeChunk> parseDocument(String source, String document) {
        String normalized = document == null ? "" : document.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        DocumentParts parts = parseFrontMatter(normalized, source);
        List<String> sections = splitSections(parts.body());
        List<KnowledgeChunk> result = new ArrayList<>();
        int sectionNumber = 0;
        for (String section : sections) {
            String text = section.trim();
            if (text.isBlank()) {
                continue;
            }
            String title = sectionNumber == 0 ? parts.title() : firstLine(text, parts.title());
            String id = parts.id() + "." + sectionNumber++;
            result.add(new KnowledgeChunk(
                    id, title, parts.aliases(), stripHeading(text), parts.expired(), parts.category(), parts.authority()
            ));
        }
        return result;
    }

    private List<String> splitSections(String body) {
        if (body.matches("(?sm).*^##+\\s+.*")) {
            return List.of(body.split("(?m)^##+\\s+"));
        }
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : body.split("\\R")) {
            if (line.stripLeading().startsWith("- ") && !current.isEmpty()) {
                sections.add(current.toString().trim());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line.trim());
        }
        if (!current.isEmpty()) {
            sections.add(current.toString().trim());
        }
        return sections;
    }

    private DocumentParts parseFrontMatter(String document, String source) {
        Map<String, String> metadata = new HashMap<>();
        String body = document;
        if (document.startsWith("---\n")) {
            int closing = document.indexOf("\n---", 4);
            if (closing > 0) {
                String header = document.substring(4, closing);
                for (String line : header.split("\\R")) {
                    int separator = line.indexOf(':');
                    if (separator > 0) {
                        metadata.put(line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                                line.substring(separator + 1).trim());
                    }
                }
                body = document.substring(closing + 4).trim();
            }
        }
        String defaultTitle = source.replaceFirst("\\.[^.]+$", "").replace('-', ' ');
        String id = metadata.getOrDefault("id", source.replaceAll("[^A-Za-z0-9]+", "."));
        String title = metadata.getOrDefault("title", defaultTitle);
        List<String> aliases = tokenize(metadata.getOrDefault("aliases", ""));
        boolean expired = isExpired(metadata.get("expires"));
        String category = cleanMetadata(metadata.getOrDefault("category", "general"));
        String authority = cleanMetadata(metadata.getOrDefault("authority", source.equals("config.knowledge") ? "official" : "reviewed"));
        return new DocumentParts(id, title, aliases, body, expired, category, authority);
    }

    private String cleanMetadata(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]+", "-").toLowerCase(Locale.ROOT);
    }

    private boolean isExpired(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return false;
        }
        try {
            return LocalDate.parse(value).isBefore(LocalDate.now());
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private String firstLine(String value, String fallback) {
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline).trim();
    }

    private String stripHeading(String value) {
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(newline + 1).trim();
    }

    private static List<String> tokenize(String value) {
        List<String> tokens = new ArrayList<>();
        for (String token : TOKEN_SEPARATOR.split(value == null ? "" : value.toLowerCase(Locale.ROOT))) {
            String normalized = normalizeToken(token);
            if (normalized.length() >= 2 && !STOP_WORDS.contains(normalized)) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    private static String normalizeToken(String token) {
        String value = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) {
            return value;
        }
        if (value.length() > 6 && value.endsWith("ing")) {
            return value.substring(0, value.length() - 3);
        }
        if (value.length() > 5 && value.endsWith("en")) {
            return value.substring(0, value.length() - 2);
        }
        return value;
    }

    private static Map<String, Set<String>> conceptMap() {
        List<Set<String>> groups = List.of(
                Set.of("claim", "claims", "plot", "plots", "protect", "bescherm"),
                Set.of("money", "geld", "balance", "saldo", "economy", "eco"),
                Set.of("rank", "ranks", "donor", "perk", "perks", "voordeel"),
                Set.of("vote", "votes", "voting", "stem", "stemmen"),
                Set.of("friend", "friends", "vriend", "vrienden"),
                Set.of("vanish", "invisible", "onzichtbaar"),
                Set.of("combat", "combattag", "combatlog", "pvp"),
                Set.of("lottery", "lotto", "loterij"),
                Set.of("survival", "creative", "minigames", "gamemode", "server"),
                Set.of("fun", "interesting", "feit", "feitje", "fact", "weetje"),
                Set.of("biome", "bioom", "environment", "dimension", "world", "wereld")
        );
        Map<String, Set<String>> map = new HashMap<>();
        for (Set<String> group : groups) {
            for (String token : group) {
                map.put(token, group);
            }
        }
        return Map.copyOf(map);
    }

    /** Source-attributed, player-safe article fragment. */
    public record KnowledgeChunk(
            String id,
            String title,
            List<String> aliases,
            String text,
            boolean expired,
            String category,
            String authority
    ) {
        public KnowledgeChunk(String id, String title, List<String> aliases, String text, boolean expired) {
            this(id, title, aliases, text, expired, "general", "reviewed");
        }

        public KnowledgeChunk {
            id = id == null ? "unknown" : id.trim();
            title = title == null ? "" : title.trim();
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            text = text == null ? "" : text.trim();
            category = category == null ? "general" : category.trim().toLowerCase(Locale.ROOT);
            authority = authority == null ? "reviewed" : authority.trim().toLowerCase(Locale.ROOT);
        }

        private List<String> tokens() {
            return tokenize(title + " " + String.join(" ", aliases) + " " + category + " " + text);
        }
    }

    private record DocumentParts(
            String id,
            String title,
            List<String> aliases,
            String body,
            boolean expired,
            String category,
            String authority
    ) {
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {
    }
}
