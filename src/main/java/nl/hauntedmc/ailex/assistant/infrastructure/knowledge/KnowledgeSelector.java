package nl.hauntedmc.ailex.assistant.infrastructure.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Selects the relevant part of an operator-maintained knowledge base for one legacy chat turn. */
public final class KnowledgeSelector {

    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}/+]+");
    private static final Set<String> COMMON_WORDS = Set.of(
            "aan", "als", "and", "are", "bij", "can", "dan", "dat", "de", "den", "der", "die", "dit",
            "een", "en", "for", "het", "hoe", "ik", "in", "is", "je", "kan", "met", "mijn", "naar",
            "of", "om", "op", "the", "to", "van", "wat", "wel", "wie", "with", "you", "your", "ze"
    );

    private KnowledgeSelector() {
    }

    public static String select(String knowledge, String query, int maxCharacters) {
        String normalizedKnowledge = knowledge == null ? "" : knowledge.trim();
        if (normalizedKnowledge.isEmpty() || maxCharacters <= 0) {
            return "";
        }
        if (normalizedKnowledge.length() <= maxCharacters) {
            return normalizedKnowledge;
        }

        List<String> sections = splitSections(normalizedKnowledge);
        if (sections.size() <= 1) {
            return truncate(normalizedKnowledge, maxCharacters);
        }

        String header = sections.removeFirst();
        List<ScoredSection> ranked = new ArrayList<>();
        List<String> queryWords = queryWords(query);
        for (int index = 0; index < sections.size(); index++) {
            int score = score(sections.get(index), queryWords);
            if (score > 0) {
                ranked.add(new ScoredSection(index, sections.get(index), score));
            }
        }
        ranked.sort(Comparator.comparingInt(ScoredSection::score).reversed()
                .thenComparingInt(ScoredSection::index));

        List<String> selected = new ArrayList<>();
        int usedCharacters = header.length();
        for (ScoredSection section : ranked) {
            if (fits(section.text(), usedCharacters, maxCharacters)) {
                selected.add(section.text());
                usedCharacters += 1 + section.text().length();
            }
        }

        if (selected.isEmpty()) {
            for (String section : sections) {
                if (!fits(section, usedCharacters, maxCharacters)) {
                    break;
                }
                selected.add(section);
                usedCharacters += 1 + section.length();
                if (selected.size() == 2) {
                    break;
                }
            }
        }

        return truncate(header + '\n' + String.join("\n", selected), maxCharacters);
    }

    private static List<String> splitSections(String knowledge) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : knowledge.split("\\R")) {
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

    private static List<String> queryWords(String query) {
        Set<String> uniqueWords = new HashSet<>();
        for (String word : WORD_SEPARATOR.split(query == null ? "" : query.toLowerCase(Locale.ROOT))) {
            if (word.length() >= 3 && !COMMON_WORDS.contains(word)) {
                uniqueWords.add(word);
            }
        }
        return new ArrayList<>(uniqueWords);
    }

    private static int score(String section, List<String> queryWords) {
        String normalizedSection = section.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String word : queryWords) {
            if (normalizedSection.contains(word)) {
                score += word.startsWith("/") ? 4 : 1;
            }
        }
        return score;
    }

    private static boolean fits(String section, int usedCharacters, int maxCharacters) {
        return usedCharacters + 1 + section.length() <= maxCharacters;
    }

    private static String truncate(String value, int maxCharacters) {
        return value.length() <= maxCharacters ? value : value.substring(0, maxCharacters);
    }

    private record ScoredSection(int index, String text, int score) {
    }
}
