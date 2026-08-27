package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic front door for explicit player-owned memory operations.
 *
 * <p>The model remains useful for opportunistic/implicit memory extraction, but a direct "remember this" command or an
 * unambiguous durable self-declaration must not depend on whether the generation model happens to emit a memory candidate.
 * Every candidate produced here still passes through {@link AssistantMemoryService#rememberCandidate}, so the existing
 * scope, source-support and data-safety policy remains authoritative.</p>
 */
public final class ExplicitPlayerMemoryService {

    private static final Pattern FAVORITE = Pattern.compile(
            "(?:mijn\\s+(?:favoriete|lievelings)|my\\s+(?:favorite|favourite))\\s+([\\p{L}0-9 _-]{1,48}?)\\s+"
                    + "(?:is|zijn|are)\\s+(.{1,160})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PREFERENCE = Pattern.compile(
            "(?:mijn\\s+voorkeur\\s+(?:voor|qua)|my\\s+preference\\s+(?:for|on))\\s+([\\p{L}0-9 _-]{1,48}?)\\s+"
                    + "(?:is|zijn|are)\\s+(.{1,160})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PLAYS_SINCE = Pattern.compile(
            "(?:ik\\s+speel(?:\\s+hier)?(?:\\s+al)?\\s+sinds|i(?:\\s+have|'ve)?\\s+played(?:\\s+here)?\\s+since)\\s+"
                    + "(\\d{4})\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern HAIR = Pattern.compile(
            "(?:ik\\s+heb\\s+(bruin|blond|zwart|rood)\\s+haar|i\\s+have\\s+(brown|blonde?|black|red)\\s+hair)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern LIKES = Pattern.compile(
            "(?:ik\\s+hou(?:d)?\\s+van|i\\s+(?:like|love))\\s+(.{1,160})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern INTEREST = Pattern.compile(
            "(?:ik\\s+ben\\s+(?:fan\\s+van|ge[iï]nteresseerd\\s+in)|i(?:\\s+am|'m)\\s+(?:a\\s+fan\\s+of|interested\\s+in))\\s+(.{1,160})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern GOAL = Pattern.compile(
            "(?:mijn\\s+doel\\s+(?:is|zijn)|my\\s+goal\\s+(?:is|are)|ik\\s+(?:werk\\s+aan|spaar\\s+voor)|"
                    + "i(?:\\s+am|'m)\\s+(?:working\\s+on|saving\\s+for))\\s+(.{1,160})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern REMEMBER_PREFIX = Pattern.compile(
            ".*?\\b(?:onthoud(?:en)?|remember)\\b(?:\\s+dat|\\s+that)?\\s+(.+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern FORGET_PREFIX = Pattern.compile(
            ".*?\\b(?:vergeet|forget)\\b(?:\\s+dat|\\s+that)?\\s+(.+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final AssistantMemoryService memory;

    public ExplicitPlayerMemoryService(AssistantMemoryService memory) {
        this.memory = memory;
    }

    /**
     * Applies zero or more deterministic player-memory operations. The return value is diagnostic only; generation still
     * runs normally and receives the newly written memory through the regular context planner.
     */
    public Result observe(UUID playerId, String playerName, String message) {
        if (memory == null || playerId == null || message == null || message.isBlank()) {
            return Result.none();
        }
        String clean = compact(message);
        if (AssistantIntentClassifier.isMemoryForgetStatement(clean)) {
            return forget(playerId, playerName, clean);
        }
        if (!AssistantIntentClassifier.isMemoryWriteStatement(clean)) {
            return Result.none();
        }

        List<MemoryCandidate> candidates = extractWriteCandidates(clean);
        int accepted = 0;
        for (MemoryCandidate candidate : candidates.stream().limit(3).toList()) {
            if (memory.rememberCandidate(playerId, playerName, candidate, clean, false) != null) {
                accepted++;
            }
        }
        return new Result(candidates.size(), accepted, false);
    }

    private Result forget(UUID playerId, String playerName, String message) {
        Matcher matcher = FORGET_PREFIX.matcher(message);
        String query = matcher.matches() ? stripTerminalPunctuation(matcher.group(1)) : message;
        List<MemoryRecord> records = memory.search(
                playerId,
                "",
                query,
                Set.of(MemoryKind.PREFERENCE, MemoryKind.FACT, MemoryKind.OPINION, MemoryKind.INTEREST, MemoryKind.GOAL),
                8
        );
        if (records.isEmpty()) {
            return new Result(0, 0, true);
        }
        MemoryRecord best = records.getFirst();
        MemoryCandidate candidate = new MemoryCandidate(
                "player", candidateKind(best.kind()), best.key(), "", "forget"
        );
        // rememberCandidate intentionally returns null for a successful forget; the selected record is already verified to
        // exist, and the validator still requires an explicit forget signal plus key support from the player message.
        memory.rememberCandidate(playerId, playerName, candidate, message, false);
        return new Result(1, 1, true);
    }

    private List<MemoryCandidate> extractWriteCandidates(String message) {
        List<MemoryCandidate> result = new ArrayList<>();

        Matcher favorite = FAVORITE.matcher(message);
        if (favorite.find()) {
            add(result, "preference", "favorite_" + slug(favorite.group(1)), favorite.group(2));
            return List.copyOf(result);
        }

        Matcher preference = PREFERENCE.matcher(message);
        if (preference.find()) {
            add(result, "preference", "preference_" + slug(preference.group(1)), preference.group(2));
            return List.copyOf(result);
        }

        Matcher playsSince = PLAYS_SINCE.matcher(message);
        if (playsSince.find()) {
            add(result, "fact", "plays_since", playsSince.group(1));
            return List.copyOf(result);
        }

        Matcher hair = HAIR.matcher(message);
        if (hair.find()) {
            String value = hair.group(1) != null ? hair.group(1) : hair.group(2);
            add(result, "fact", "hair_color", value);
            return List.copyOf(result);
        }

        Matcher likes = LIKES.matcher(message);
        if (likes.find()) {
            String value = stripTerminalPunctuation(likes.group(1));
            add(result, "preference", "likes_" + slug(value), value);
            return List.copyOf(result);
        }

        Matcher interest = INTEREST.matcher(message);
        if (interest.find()) {
            String value = stripTerminalPunctuation(interest.group(1));
            add(result, "interest", "interest_" + slug(value), value);
            return List.copyOf(result);
        }

        Matcher goal = GOAL.matcher(message);
        if (goal.find()) {
            String value = stripTerminalPunctuation(goal.group(1));
            add(result, "goal", "goal_" + slug(value), value);
            return List.copyOf(result);
        }

        // Explicit remember commands are allowed to be more general than automatic preference extraction. Preserve the
        // player's concise statement as a supported fact, with a stable content-derived key, instead of silently dropping it.
        Matcher remember = REMEMBER_PREFIX.matcher(message);
        if (remember.matches()) {
            String value = stripTerminalPunctuation(remember.group(1));
            if (!value.isBlank()) {
                add(result, "fact", "remembered_" + slug(value), value);
            }
        }
        return List.copyOf(result);
    }

    private void add(List<MemoryCandidate> target, String kind, String key, String value) {
        String cleanValue = stripTerminalPunctuation(value);
        if (!key.isBlank() && !cleanValue.isBlank()) {
            target.add(new MemoryCandidate("player", kind, key, cleanValue, "upsert"));
        }
    }

    private String candidateKind(MemoryKind kind) {
        return switch (kind) {
            case PREFERENCE -> "preference";
            case OPINION -> "opinion";
            case INTEREST -> "interest";
            case GOAL -> "goal";
            default -> "fact";
        };
    }

    private String slug(String value) {
        String normalized = compact(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            return "general";
        }
        return normalized.length() <= 52 ? normalized : normalized.substring(0, 52).replaceAll("_+$", "");
    }

    private String stripTerminalPunctuation(String value) {
        return compact(value).replaceAll("[.!?,;:]+$", "").trim();
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public record Result(int proposed, int accepted, boolean forget) {
        public Result {
            proposed = Math.max(0, proposed);
            accepted = Math.max(0, accepted);
        }

        public static Result none() {
            return new Result(0, 0, false);
        }

        public boolean changedMemory() {
            return accepted > 0;
        }
    }
}
