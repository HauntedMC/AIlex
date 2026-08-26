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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A local, source-aware knowledge index. It deliberately has no network dependency: operator
 * maintained Markdown stays the source of truth and can be inspected before it is sent to a model.
 */
public final class LocalKnowledgeIndex {

    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}/+]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "aan", "als", "and", "are", "bij", "can", "dan", "dat", "de", "die", "dit", "een",
            "en", "for", "het", "hoe", "ik", "in", "is", "je", "met", "mijn", "naar", "of", "om",
            "op", "the", "to", "van", "wat", "wel", "wie", "with", "you", "your"
    );

    private final JavaPlugin plugin;
    private volatile List<KnowledgeChunk> chunks = List.of();
    private final Map<String, CachedSearch> searchCache = new ConcurrentHashMap<>();

    public LocalKnowledgeIndex(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Rebuilds the immutable in-memory index after a configuration reload. */
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
        searchCache.clear();
    }

    public List<KnowledgeChunk> search(String query, AssistantSettings settings) {
        String cacheKey = (query == null ? "" : query.trim().toLowerCase(Locale.ROOT)) + '|'
                + settings.maxChunks() + '|' + settings.maxEvidenceCharacters() + '|' + settings.excludeExpired()
                + '|' + settings.hybridRetrieval();
        CachedSearch cached = searchCache.get(cacheKey);
        if (cached != null && cached.isFresh(settings.queryCacheSeconds())) {
            return cached.results();
        }
        List<String> queryTokens = tokenize(query);
        if (chunks.isEmpty() || queryTokens.isEmpty()) {
            return List.of();
        }
        int documentCount = chunks.size();
        double averageLength = chunks.stream().mapToInt(chunk -> chunk.tokens().size()).average().orElse(1.0D);
        List<ScoredChunk> scored = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            if (settings.excludeExpired() && chunk.expired()) {
                continue;
            }
            double score = bm25(chunk, queryTokens, documentCount, averageLength, settings.hybridRetrieval());
            if (score > 0.0D) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed()
                .thenComparing(scoredChunk -> scoredChunk.chunk().id()));

        List<KnowledgeChunk> selected = new ArrayList<>();
        int used = 0;
        for (ScoredChunk candidate : scored) {
            KnowledgeChunk chunk = candidate.chunk();
            int next = chunk.text().length() + 40;
            if (selected.size() >= settings.maxChunks() || used + next > settings.maxEvidenceCharacters()) {
                continue;
            }
            selected.add(chunk);
            used += next;
        }
        List<KnowledgeChunk> results = List.copyOf(selected);
        if (settings.queryCacheSeconds() > 0) {
            searchCache.put(cacheKey, new CachedSearch(System.currentTimeMillis(), results));
        }
        return results;
    }

    private double bm25(KnowledgeChunk chunk, List<String> queryTokens, int documentCount, double averageLength,
                        boolean hybrid) {
        double score = 0.0D;
        for (String token : queryTokens) {
            int frequency = frequency(chunk.tokens(), token);
            if (frequency == 0) {
                continue;
            }
            long matchingDocuments = chunks.stream().filter(other -> other.tokens().contains(token)).count();
            double idf = Math.log(1.0D + (documentCount - matchingDocuments + 0.5D)
                    / (matchingDocuments + 0.5D));
            double denominator = frequency + 1.2D * (1.0D - 0.75D
                    + 0.75D * chunk.tokens().size() / averageLength);
            score += idf * frequency * 2.2D / denominator;
            if (hybrid && chunk.title().toLowerCase(Locale.ROOT).contains(token)) {
                score += 2.0D;
            }
            if (hybrid && chunk.aliases().stream().anyMatch(alias -> alias.contains(token))) {
                score += token.startsWith("/") ? 5.0D : 3.0D;
            }
        }
        return score;
    }

    private int frequency(List<String> tokens, String expected) {
        return (int) tokens.stream().filter(expected::equals).count();
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
        return name.endsWith(".md") || name.endsWith(".txt");
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
            result.add(new KnowledgeChunk(id, title, parts.aliases(), stripHeading(text), parts.expired()));
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
        return new DocumentParts(id, title, aliases, body, expired);
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
            if (token.length() >= 2 && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** A source-attributed, player-safe article fragment. */
    public record KnowledgeChunk(String id, String title, List<String> aliases, String text, boolean expired) {
        public KnowledgeChunk {
            aliases = List.copyOf(aliases);
        }

        private List<String> tokens() {
            return tokenize(title + " " + String.join(" ", aliases) + " " + text);
        }
    }

    private record DocumentParts(String id, String title, List<String> aliases, String body, boolean expired) {
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {
    }
}
