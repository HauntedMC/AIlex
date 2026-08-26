package nl.hauntedmc.ailex.assistant.application.routing;

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
    private static final Set<String> LIVE_WORDS = Set.of(
            "waar", "hier", "dichtbij", "nearby", "near", "biome", "coord", "coords", "coördinaten",
            "positie", "location", "locatie", "weer", "weather", "online", "spelers", "players", "tps",
            "mspt", "ping", "health", "gezondheid", "honger", "food", "item", "hand", "holding", "vast",
            "kijk", "level", "xp", "ervaring", "experience", "effect", "armor", "pantser", "light", "licht",
            "difficulty", "moeilijkheid", "environment", "omgeving", "version", "versie", "uptime", "playtime"
    );
    private static final Set<String> SERVER_WORDS = Set.of(
            "rank", "elite", "legend", "supreme", "claim", "regels", "rules", "vote", "stem",
            "store", "winkel", "warp", "command", "commando", "server", "hauntedmc"
    );
    private static final Set<String> GAMEPLAY_WORDS = Set.of(
            "minecraft", "kameel", "camel", "wolf", "tem", "temt", "tammen", "tame", "craft", "recept", "recipe",
            "redstone", "enchant", "betover", "potion", "drank", "mob", "farm", "bouwen", "build"
    );
    private static final Set<String> UNSAFE_WORDS = Set.of(
            "exploit", "dupe", "xray", "hack", "cheat", "dox", "doxx", "groom", "zelfmoord", "suicide"
    );

    private AssistantIntentClassifier() {
    }

    public static Analysis analyze(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, UNSAFE_WORDS)) {
            return new Analysis(AssistantIntent.SAFETY, AssistantMode.HANDOFF,
                    detectLanguage(normalized, "nl", DEFAULT_ALLOWED_LANGUAGES));
        }
        if (containsAny(normalized, SUPPORT_WORDS)) {
            return new Analysis(AssistantIntent.SUPPORT, AssistantMode.DELIBERATE,
                    detectLanguage(normalized, "nl", DEFAULT_ALLOWED_LANGUAGES));
        }
        if (containsAny(normalized, LIVE_WORDS)) {
            return new Analysis(AssistantIntent.LIVE_STATE, AssistantMode.DELIBERATE,
                    detectLanguage(normalized, "nl", DEFAULT_ALLOWED_LANGUAGES));
        }
        if (containsAny(normalized, SERVER_WORDS) || normalized.contains("/")) {
            return new Analysis(AssistantIntent.SERVER_FACT, AssistantMode.GROUNDED,
                    detectLanguage(normalized, "nl", DEFAULT_ALLOWED_LANGUAGES));
        }
        if (normalized.contains("hoe ") || normalized.contains("how ") || normalized.contains("waarom")
                || normalized.contains("why ") || normalized.contains("help") || containsAny(normalized, GAMEPLAY_WORDS)) {
            return new Analysis(AssistantIntent.GAMEPLAY_HELP, AssistantMode.GROUNDED,
                    detectLanguage(normalized, "nl", DEFAULT_ALLOWED_LANGUAGES));
        }
        return new Analysis(AssistantIntent.CONVERSATION, AssistantMode.FAST,
                detectLanguage(normalized, "nl", DEFAULT_ALLOWED_LANGUAGES));
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
        if (!allowedLanguages.contains("en")) {
            return fallback;
        }
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        int englishSignals = countSignals(normalized, Set.of(
                "the", "and", "what", "how", "why", "where", "can", "please", "hello", "thanks", "thank",
                "with", "for", "my", "does", "are"
        ));
        int dutchSignals = countSignals(normalized, Set.of(
                "de", "het", "een", "en", "wat", "hoe", "waarom", "waar", "kan", "wil", "jij", "mijn",
                "met", "voor", "dank", "hallo", "als"
        ));
        return englishSignals > dutchSignals && englishSignals > 0 ? "en" : fallback;
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
