package nl.hauntedmc.ailex.assistant.application.routing;

import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;

import java.util.Locale;
import java.util.Set;

/**
 * Performs the inexpensive first routing pass. The model never decides whether it may use a tool;
 * this classifier is intentionally conservative and routes uncertainty to grounded processing.
 */
public final class AssistantIntentClassifier {

    private static final Set<String> DEFAULT_ALLOWED_LANGUAGES = Set.of("nl", "en");
    private static final Set<String> SUPPORT_WORDS = Set.of(
            "support", "betaling", "betaal", "refund", "chargeback", "aankoop", "purchase", "2fa",
            "wachtwoord", "password", "ban", "appeal", "report", "ticket", "unban"
    );
    private static final Set<String> STRONG_LIVE_WORDS = Set.of(
            "online", "tps", "mspt", "ping", "latency", "uptime", "performance", "lag", "playtime", "speeltijd"
    );
    private static final Set<String> PLAYER_STATE_WORDS = Set.of(
            "health", "gezondheid", "leven", "honger", "food", "item", "hand", "holding", "vasthoud", "vast",
            "level", "xp", "ervaring", "experience", "effect", "armor", "armour", "pantser", "gespeeld"
    );
    private static final Set<String> WORLD_STATE_WORDS = Set.of(
            "biome", "bioom", "coord", "coords", "coördinaten", "positie", "location", "locatie", "weer",
            "weather", "time", "tijd", "light", "licht", "difficulty", "moeilijkheid", "environment", "omgeving",
            "dimension", "dimensie", "facing", "richting"
    );
    private static final Set<String> LOCAL_CUES = Set.of("hier", "here", "dichtbij", "nearby", "near");
    private static final Set<String> SERVER_WORDS = Set.of(
            "rank", "elite", "legend", "supreme", "claim", "regels", "rules", "vote", "stem",
            "store", "winkel", "warp", "command", "commando", "server", "hauntedmc"
    );
    private static final Set<String> GAMEPLAY_WORDS = Set.of(
            "minecraft", "kameel", "camel", "wolf", "tem", "temt", "tammen", "tame", "craft", "recept", "recipe",
            "redstone", "enchant", "betover", "potion", "drank", "mob", "farm", "bouwen", "build", "diamond",
            "diamonds", "ore", "erts"
    );
    private static final Set<String> MEMORY_WORDS = Set.of(
            "onthoud", "onthouden", "herinner", "herinneren", "remember", "remembered", "weet", "wist"
    );
    private static final Set<String> EVENT_WORDS = Set.of(
            "gebeurde", "gebeurd", "mis", "bug", "bugged", "fout", "probleem", "vorige", "eerder", "net",
            "happened", "wrong", "problem", "before", "earlier"
    );
    private static final Set<String> UNSAFE_WORDS = Set.of(
            "exploit", "dupe", "xray", "hack", "cheat", "dox", "doxx", "groom", "zelfmoord", "suicide"
    );

    private AssistantIntentClassifier() {
    }

    public static Analysis analyze(String message) {
        return analyze(message, AssistantDialogueContext.empty());
    }

    public static Analysis analyze(String message, AssistantDialogueContext dialogue) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        AssistantDialogueContext context = dialogue == null ? AssistantDialogueContext.empty() : dialogue;
        String language = detectLanguage(normalized, "nl", DEFAULT_ALLOWED_LANGUAGES);
        if (containsAny(normalized, UNSAFE_WORDS)) {
            return new Analysis(AssistantIntent.SAFETY, AssistantMode.HANDOFF, language);
        }
        if (containsAny(normalized, SUPPORT_WORDS)) {
            return new Analysis(AssistantIntent.SUPPORT, AssistantMode.DELIBERATE, language);
        }
        if (context.active() && containsAny(normalized, MEMORY_WORDS)) {
            return new Analysis(AssistantIntent.MEMORY_RECALL, AssistantMode.GROUNDED, language);
        }
        if (context.active() && containsAny(normalized, EVENT_WORDS)) {
            return new Analysis(AssistantIntent.EVENT_RECALL, AssistantMode.GROUNDED, language);
        }
        if (isLiveStateQuestion(normalized)) {
            // Live-state answers are evidence-oriented but normally simple; Terra is enough unless validation escalates.
            return new Analysis(AssistantIntent.LIVE_STATE, AssistantMode.GROUNDED, language);
        }
        if (containsAny(normalized, SERVER_WORDS) || normalized.contains("/")) {
            return new Analysis(AssistantIntent.SERVER_FACT, AssistantMode.GROUNDED, language);
        }
        if (normalized.contains("hoe ") || normalized.contains("how ") || normalized.startsWith("waar ")
                || normalized.startsWith("where ") || normalized.contains("waarom") || normalized.contains("why ")
                || normalized.contains("help") || containsAny(normalized, GAMEPLAY_WORDS)) {
            return new Analysis(AssistantIntent.GAMEPLAY_HELP, AssistantMode.GROUNDED, language);
        }
        if (context.active() && isContextualFollowUp(normalized, context)) {
            AssistantMode mode = context.previousIntent() == AssistantIntent.SERVER_FACT
                    || context.previousIntent() == AssistantIntent.GAMEPLAY_HELP
                    || context.previousIntent() == AssistantIntent.EVENT_RECALL
                    || context.previousIntent() == AssistantIntent.MEMORY_RECALL
                    ? AssistantMode.GROUNDED : AssistantMode.FAST;
            return new Analysis(AssistantIntent.CONTEXT_FOLLOWUP, mode, language);
        }
        return new Analysis(AssistantIntent.CONVERSATION, AssistantMode.FAST, language);
    }

    private static boolean isLiveStateQuestion(String message) {
        if (containsAny(message, STRONG_LIVE_WORDS) || containsLivePhrase(message)) {
            return true;
        }
        boolean currentSelfReference = containsAnyPhrase(
                message,
                "mijn ", "my ", "heb ik", "ik heb", "houd ik", "hou ik", "am i", "i have", "i'm ", "im "
        );
        if (currentSelfReference && containsAny(message, PLAYER_STATE_WORDS)) {
            return true;
        }
        if (containsAny(message, LOCAL_CUES) && containsAny(message, WORLD_STATE_WORDS)) {
            return true;
        }
        return containsAnyPhrase(
                message,
                "waar ben ik", "waar sta ik", "where am i", "where do i stand",
                "waar sta je", "waar ben jij", "where are you", "wat doe je", "what are you doing"
        );
    }

    private static boolean containsLivePhrase(String message) {
        return containsAnyPhrase(message, "om me heen", "around me", "near me");
    }

    private static boolean containsAnyPhrase(String message, String... phrases) {
        for (String phrase : phrases) {
            if (message.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isContextualFollowUp(String message, AssistantDialogueContext context) {
        if (context.pendingAnswer() && message.length() <= 96) {
            return true;
        }
        if (message.endsWith("?")) {
            return true;
        }
        return Set.of(
                "ja", "nee", "maar", "dus", "waarom", "hoezo", "wat", "welke", "waar", "wacht", "bedoel",
                "huh", "yes", "no", "but", "so", "why", "how", "what", "which", "where", "wait"
        ).stream().anyMatch(prefix -> message.equals(prefix) || message.startsWith(prefix + " "));
    }

    private static boolean containsAny(String message, Set<String> words) {
        for (String token : message.split("[^\\p{L}\\p{N}/+]+")) {
            if (words.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects only an explicitly allowed output language. Ambiguous, unsupported, and empty input
     * always uses the configured fallback rather than treating every non-Dutch message as English.
     */
    public static String detectLanguage(String message, String fallbackLanguage, Set<String> allowedLanguages) {
        String fallback = allowedLanguages.contains(fallbackLanguage) ? fallbackLanguage : "nl";
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        int englishSignals = countSignals(normalized, Set.of(
                "the", "and", "what", "how", "why", "where", "can", "please", "hello", "thanks", "thank",
                "with", "for", "my", "does", "are"
        ));
        int dutchSignals = countSignals(normalized, Set.of(
                "de", "het", "een", "en", "wat", "hoe", "waarom", "waar", "kan", "wil", "jij", "mijn",
                "met", "voor", "dank", "hallo", "als"
        ));
        int germanSignals = countSignals(normalized, Set.of(
                "der", "die", "das", "und", "was", "wie", "warum", "wo", "kann", "bitte", "hallo",
                "danke", "mit", "für", "ich", "mein", "nicht", "auf", "deutsch"
        ));
        String detected = fallback;
        int bestScore = dutchSignals;
        if (allowedLanguages.contains("en") && englishSignals > bestScore) {
            detected = "en";
            bestScore = englishSignals;
        }
        if (allowedLanguages.contains("de") && germanSignals > bestScore) {
            detected = "de";
        }
        return detected;
    }

    private static int countSignals(String message, Set<String> signals) {
        int count = 0;
        for (String token : message.split("[^\\p{L}\\p{N}]+")) {
            if (signals.contains(token)) {
                count++;
            }
        }
        return count;
    }

    /** Result of deterministic query routing. */
    public record Analysis(AssistantIntent intent, AssistantMode mode, String language) {
    }
}
