package nl.hauntedmc.ailex.assistant.infrastructure.knowledge;

import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic registry for exact HauntedMC identifiers such as Discord channels, commands and ranks.
 *
 * <p>Unlike free-text RAG, a canonical registry can distinguish an exact identifier from a translated or guessed one.
 * Kinds explicitly marked {@code @complete} may also produce safe negative evidence when an exact identifier is absent.
 * The language model still explains the result; Java decides whether the identifier exists.</p>
 */
public final class CanonicalIdentifierRegistry {

    private static final Pattern EXPLICIT_IDENTIFIER = Pattern.compile("(?<![\\p{L}\\p{N}_-])([#/][\\p{L}\\p{N}_:+-]+)");
    private static final int MAX_MATCHES = 4;

    private final JavaPlugin plugin;
    private volatile Map<String, Entry> exact = Map.of();
    private volatile Map<String, List<Entry>> aliases = Map.of();
    private volatile Set<String> completeKinds = Set.of();

    public CanonicalIdentifierRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Reloads the reviewed registry from the managed knowledge directory. */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "knowledge/entities.tsv");
        if (!file.isFile()) {
            exact = Map.of();
            aliases = Map.of();
            completeKinds = Set.of();
            return;
        }
        Map<String, Entry> exactEntries = new HashMap<>();
        Map<String, List<Entry>> aliasEntries = new HashMap<>();
        Set<String> complete = new HashSet<>();
        try {
            for (String rawLine : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = rawLine.split("\\t", -1);
                if (columns.length >= 2 && "@complete".equals(columns[0].trim())) {
                    String kind = normalize(columns[1]);
                    if (!kind.isBlank()) {
                        complete.add(kind);
                    }
                    continue;
                }
                if (columns.length < 2) {
                    continue;
                }
                String kind = normalize(columns[0]);
                String canonical = columns[1].trim();
                if (kind.isBlank() || canonical.isBlank()) {
                    continue;
                }
                List<String> entryAliases = columns.length >= 3 ? splitAliases(columns[2]) : List.of();
                String description = columns.length >= 4 ? columns[3].trim() : "";
                Entry entry = new Entry(kind, canonical, entryAliases, description);
                exactEntries.put(exactKey(kind, canonical), entry);
                for (String alias : entryAliases) {
                    aliasEntries.computeIfAbsent(normalize(alias), ignored -> new ArrayList<>()).add(entry);
                }
                aliasEntries.computeIfAbsent(normalize(canonical), ignored -> new ArrayList<>()).add(entry);
            }
        } catch (IOException exception) {
            LoggerUtils.logWarning("Could not load canonical identifier registry: " + exception.getMessage());
            exact = Map.of();
            aliases = Map.of();
            completeKinds = Set.of();
            return;
        }
        Map<String, List<Entry>> immutableAliases = new HashMap<>();
        aliasEntries.forEach((key, values) -> immutableAliases.put(key, List.copyOf(values.stream().distinct().toList())));
        exact = Map.copyOf(exactEntries);
        aliases = Map.copyOf(immutableAliases);
        completeKinds = Set.copyOf(complete);
    }

    /**
     * Returns authoritative synthetic evidence for exact identifiers and unambiguous natural-language aliases.
     * Explicit #channel and /command tokens never get translated through aliases.
     */
    public List<LocalKnowledgeIndex.KnowledgeChunk> evidenceFor(String message) {
        String text = message == null ? "" : message.replaceAll("\\s+", " ").trim();
        if (text.isBlank() || exact.isEmpty()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        LinkedHashSet<Entry> matches = new LinkedHashSet<>();
        List<String> negatives = new ArrayList<>();

        Matcher matcher = EXPLICIT_IDENTIFIER.matcher(lower);
        boolean explicit = false;
        while (matcher.find() && matches.size() + negatives.size() < MAX_MATCHES) {
            explicit = true;
            String token = matcher.group(1);
            String kind = token.startsWith("#") ? "discord-channel" : "command";
            Entry entry = exact.get(exactKey(kind, token));
            if (entry != null) {
                matches.add(entry);
            } else if (completeKinds.contains(kind)) {
                negatives.add(kind + "\t" + token);
            }
        }

        if (!explicit) {
            String hintedKind = hintedKind(lower);
            aliases.forEach((alias, entries) -> {
                if (matches.size() >= MAX_MATCHES || alias.length() < 3 || !containsPhrase(lower, alias)) {
                    return;
                }
                for (Entry entry : entries) {
                    if ((hintedKind.isBlank() || hintedKind.equals(entry.kind())) && matches.size() < MAX_MATCHES) {
                        matches.add(entry);
                    }
                }
            });
        }

        List<LocalKnowledgeIndex.KnowledgeChunk> evidence = new ArrayList<>();
        for (Entry entry : matches) {
            String id = "entity." + safeId(entry.kind()) + '.' + safeId(entry.canonical());
            String body = "Canonical HauntedMC " + entry.kind() + " identifier: `" + entry.canonical()
                    + "`. Copy this identifier exactly; do not translate or invent variants."
                    + (entry.description().isBlank() ? "" : " " + entry.description());
            evidence.add(chunk(id, "Canonical identifier " + entry.canonical(), body));
        }
        for (String negative : negatives) {
            String[] parts = negative.split("\\t", 2);
            String kind = parts[0];
            String token = parts[1];
            String body = "The reviewed HauntedMC canonical registry is complete for " + kind + " identifiers. `"
                    + token + "` is not registered. Do not claim it exists and do not translate another identifier into it.";
            evidence.add(chunk("entity.missing." + safeId(kind) + '.' + safeId(token),
                    "Missing canonical identifier " + token, body));
        }
        return List.copyOf(evidence);
    }

    public int size() {
        return exact.size();
    }

    private LocalKnowledgeIndex.KnowledgeChunk chunk(String id, String title, String text) {
        return new LocalKnowledgeIndex.KnowledgeChunk(
                id, title, List.of(), text, false, "canonical-identifier", "operator-confirmed",
                "knowledge/entities.tsv", ""
        );
    }

    private String hintedKind(String text) {
        if (containsAny(text, "discord", "channel", "kanaal")) {
            return "discord-channel";
        }
        if (containsAny(text, "command", "commando", "cmd")) {
            return "command";
        }
        if (containsAny(text, "rank", "rang")) {
            return "rank";
        }
        if (containsAny(text, "gamemode", "game mode", "server mode", "spelmodus")) {
            return "game-mode";
        }
        return "";
    }

    private boolean containsPhrase(String text, String phrase) {
        String escaped = Pattern.quote(phrase.toLowerCase(Locale.ROOT));
        return Pattern.compile("(?<![\\p{L}\\p{N}_-])" + escaped + "(?![\\p{L}\\p{N}_-])")
                .matcher(text).find();
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitAliases(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(alias -> !alias.isBlank())
                .limit(32)
                .toList();
    }

    private String exactKey(String kind, String canonical) {
        return normalize(kind) + '\u0000' + normalize(canonical);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String safeId(String value) {
        String normalized = normalize(value).replaceAll("[^a-z0-9._-]+", "-").replaceAll("-+", "-");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private record Entry(String kind, String canonical, List<String> aliases, String description) {
        private Entry {
            kind = kind == null ? "" : kind;
            canonical = canonical == null ? "" : canonical;
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            description = description == null ? "" : description;
        }
    }
}
