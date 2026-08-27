package nl.hauntedmc.ailex.assistant.infrastructure.knowledge;

import java.util.Locale;

/**
 * Chooses a second-stage evidence budget from query specificity and first-stage ranking confidence.
 *
 * <p>This intentionally does not invoke another model. Clear exact queries keep a small high-precision evidence set;
 * ambiguous or weakly separated rankings retain more candidates so the generator does not lose recall.</p>
 */
public final class AdaptiveEvidencePolicy {

    private static final int MINIMUM_CHARACTER_BUDGET = 4_000;
    private static final int CHARACTERS_PER_SELECTED_CHUNK = 5_000;

    private AdaptiveEvidencePolicy() {
    }

    public static Budget select(
            String query,
            int configuredMaxChunks,
            int configuredMaxCharacters,
            double topScore,
            double secondScore
    ) {
        int hardChunks = Math.max(1, configuredMaxChunks);
        int hardCharacters = Math.max(500, configuredMaxCharacters);
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        int usefulTerms = usefulTermCount(normalized);
        boolean exactIdentifier = normalized.contains("/") || normalized.contains("#") || normalized.contains("http");
        boolean clearlySeparated = topScore > 0.0D
                && (secondScore <= 0.0D || topScore >= secondScore * 1.35D);

        int targetChunks;
        if (exactIdentifier && clearlySeparated) {
            targetChunks = 4;
        } else if (clearlySeparated && usefulTerms >= 3) {
            targetChunks = 5;
        } else if (usefulTerms <= 2) {
            targetChunks = 8;
        } else {
            targetChunks = 6;
        }
        int selectedChunks = Math.min(hardChunks, targetChunks);
        int selectedCharacters = Math.min(
                hardCharacters,
                Math.max(MINIMUM_CHARACTER_BUDGET, selectedChunks * CHARACTERS_PER_SELECTED_CHUNK)
        );
        return new Budget(selectedChunks, selectedCharacters);
    }

    private static int usefulTermCount(String query) {
        if (query.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String token : query.split("[^\\p{L}\\p{N}/#]+")) {
            if (token.length() >= 3 || token.startsWith("/") || token.startsWith("#")) {
                count++;
            }
        }
        return count;
    }

    public record Budget(int maxChunks, int maxCharacters) {
        public Budget {
            maxChunks = Math.max(1, maxChunks);
            maxCharacters = Math.max(500, maxCharacters);
        }
    }
}
