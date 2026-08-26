package nl.hauntedmc.ailex.assistant.infrastructure.knowledge;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal deterministic Markdown/front-matter parser for reviewed AIlex knowledge. It intentionally supports only the
 * small audited metadata vocabulary used by the bundled corpus; arbitrary YAML execution/deserialization is forbidden.
 */
public final class KnowledgeDocumentParser {

    public List<ParsedSection> parse(String source, String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        FrontMatter front = frontMatter(normalized);
        Map<String, String> documentMetadata = new HashMap<>(front.metadata());
        String body = front.body();
        String[] sections = body.split("(?m)^##\\s+");
        List<ParsedSection> result = new ArrayList<>();
        for (int index = 0; index < sections.length; index++) {
            String section = sections[index].trim();
            if (section.isBlank()) {
                continue;
            }
            String title = documentMetadata.getOrDefault("title", source);
            String sectionBody = section;
            if (index > 0) {
                int newline = section.indexOf('\n');
                title = newline < 0 ? section : section.substring(0, newline).trim();
                sectionBody = newline < 0 ? section : section.substring(newline + 1).trim();
            } else if (section.startsWith("# ")) {
                int newline = section.indexOf('\n');
                title = newline < 0 ? section.substring(2).trim() : section.substring(2, newline).trim();
                sectionBody = newline < 0 ? "" : section.substring(newline + 1).trim();
            }
            Map<String, String> metadata = new HashMap<>(documentMetadata);
            metadata.putAll(atMetadata(sectionBody));
            sectionBody = stripAtMetadata(sectionBody);
            if (sectionBody.isBlank()) {
                continue;
            }
            String baseId = clean(metadata.getOrDefault("id", source));
            String id = sections.length <= 1 || index == 0
                    ? safeId(baseId)
                    : safeId(baseId + "." + slug(title));
            result.add(new ParsedSection(
                    id,
                    clean(metadata.getOrDefault("title", title)),
                    list(metadata.get("aliases")),
                    sectionBody,
                    expired(metadata.get("expires")),
                    clean(metadata.getOrDefault("category", "")),
                    clean(metadata.getOrDefault("authority", "reviewed")),
                    clean(metadata.getOrDefault("source", source)),
                    clean(metadata.getOrDefault("updated", ""))
            ));
        }
        return List.copyOf(result);
    }

    private FrontMatter frontMatter(String content) {
        if (!content.startsWith("---\n")) {
            return new FrontMatter(Map.of(), content);
        }
        int closing = content.indexOf("\n---\n", 4);
        if (closing < 0) {
            return new FrontMatter(Map.of(), content);
        }
        String raw = content.substring(4, closing);
        Map<String, String> metadata = new HashMap<>();
        for (String line : raw.split("\n")) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            if (allowedKey(key)) {
                metadata.put(key, value);
            }
        }
        return new FrontMatter(Map.copyOf(metadata), content.substring(closing + 5).trim());
    }

    private boolean allowedKey(String key) {
        return switch (key) {
            case "id", "title", "aliases", "category", "authority", "updated", "expires", "source" -> true;
            default -> false;
        };
    }

    private Map<String, String> atMetadata(String body) {
        Map<String, String> result = new HashMap<>();
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("@") || !trimmed.contains(":")) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            String key = trimmed.substring(1, separator).trim().toLowerCase(Locale.ROOT);
            if (allowedKey(key)) {
                result.put(key, trimmed.substring(separator + 1).trim());
            }
        }
        return result;
    }

    private String stripAtMetadata(String body) {
        return body.lines().filter(line -> !line.trim().startsWith("@"))
                .collect(java.util.stream.Collectors.joining("\n")).trim();
    }

    private List<String> list(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .map(value -> value.replaceAll("^[\\\"']|[\\\"']$", ""))
                .filter(value -> !value.isBlank())
                .limit(32)
                .toList();
    }

    private boolean expired(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("null")) {
            return false;
        }
        try {
            return LocalDate.parse(raw.trim()).isBefore(LocalDate.now());
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private String slug(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String safeId(String value) {
        String id = clean(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("-+", "-");
        return id.length() <= 96 ? id : id.substring(0, 96);
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private record FrontMatter(Map<String, String> metadata, String body) {
    }

    public record ParsedSection(
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
        public ParsedSection {
            id = id == null ? "" : id;
            title = title == null ? "" : title;
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            text = text == null ? "" : text;
            category = category == null ? "" : category;
            authority = authority == null ? "" : authority;
            source = source == null ? "" : source;
            updated = updated == null ? "" : updated;
        }
    }
}
