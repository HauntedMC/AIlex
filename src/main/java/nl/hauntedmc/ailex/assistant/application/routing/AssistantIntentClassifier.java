package nl.hauntedmc.ailex.assistant.application.routing;

import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;

import java.util.Locale;
import java.util.Set;

/**
 * Performs the inexpensive first routing pass. The model never decides whether it may use a tool;
 * uncertain factual/current-state requests are deliberately routed to grounded processing.
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
            "level", "xp", "ervaring", "experience", "effect", "armor", "armour", "pantser", "gespeeld",
            "inventory", "inventaris", "offhand", "equipment", "uitrusting", "saturation", "air", "lucht",
            "fire", "brand", "flying", "vliegen", "swimming", "zwemmen", "sprinting", "rennen",
            "rank", "balance", "saldo", "money", "geld", "currency", "valuta", "credits", "crowns", "essence",
            "claim", "claims", "combattag", "combat-tag", "tagged", "autopickup", "fly", "god", "vanish",
            "queue", "lottery", "loterij", "friends", "vrienden", "perk", "perks"
    );
    private static final Set<String> WORLD_STATE_WORDS = Set.of(
            "world", "wereld", "biome", "bioom", "coord", "coords", "coördinaten", "positie", "position", "location",
            "locatie", "weer", "weather", "time", "tijd", "light", "licht", "difficulty", "moeilijkheid",
            "environment", "omgeving", "dimension", "dimensie", "facing", "richting", "height", "hoogte", "block",
            "blok", "target", "kijk", "looking"
    );
    private static final Set<String> LOCAL_CUES = Set.of("hier", "here", "dichtbij", "nearby", "near", "nu", "now");
    private static final Set<String> SERVER_WORDS = Set.of(
            "rank", "elite", "legend", "supreme", "claim", "regels", "rules", "vote", "stem",
            "store", "winkel", "warp", "command", "commando", "server", "hauntedmc",
            "discord", "channel", "channels", "kanaal", "kanalen", "announcement", "announcements", "aankondiging",
            "aankondigingen", "changelog", "changelogs", "versie", "version", "release", "update", "updates"
    );
    private static final Set<String> STRONG_SERVER_WORDS = Set.of(
            "rank", "elite", "legend", "supreme", "claim", "regels", "rules", "vote", "stem",
            "store", "winkel", "warp", "command", "commando", "server", "hauntedmc",
            "discord", "channel", "channels", "kanaal", "kanalen", "announcement", "announcements", "aankondiging",
            "aankondigingen", "changelog", "changelogs"
    );
    private static final Set<String> VERSION_WORDS = Set.of(
            "versie", "version", "release", "update", "updates"
    );
    private static final Set<String> GAMEPLAY_WORDS = Set.of(
            "minecraft", "kameel", "camel", "wolf", "tem", "temt", "tammen", "tame", "craft", "recept", "recipe",
            "redstone", "enchant", "betover", "potion", "drank", "mob", "farm", "bouwen", "build", "diamond",
            "diamonds", "ore", "erts"
    );
    private static final Set<String> MEMORY_WORDS = Set.of(
            "onthoud", "onthouden", "herinner", "herinneren", "remember", "remembered", "weet", "wist", "vergeet",
            "forget"
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
        if (isKnowledgeDiscovery(normalized)) {
            return new Analysis(AssistantIntent.KNOWLEDGE_DISCOVERY, AssistantMode.GROUNDED, language);
        }
        if (isDirectEventRecall(normalized) && !isCorrection(normalized)) {
            return new Analysis(AssistantIntent.EVENT_RECALL, AssistantMode.GROUNDED, language);
        }
        if (isDirectMemoryRecall(normalized)) {
            return new Analysis(AssistantIntent.MEMORY_RECALL, AssistantMode.GROUNDED, language);
        }
        if (context.active() && containsAny(normalized, MEMORY_WORDS)) {
            return new Analysis(AssistantIntent.MEMORY_RECALL, AssistantMode.GROUNDED, language);
        }
        if (context.active() && containsAny(normalized, EVENT_WORDS) && !isCorrection(normalized)) {
            return new Analysis(AssistantIntent.EVENT_RECALL, AssistantMode.GROUNDED, language);
        }
        if (isLiveStateQuestion(normalized)) {
            return new Analysis(AssistantIntent.LIVE_STATE, AssistantMode.GROUNDED, language);
        }
        if (isCorrection(normalized) && (containsAny(normalized, SERVER_WORDS) || context.active())) {
            return new Analysis(context.active() ? AssistantIntent.CONTEXT_FOLLOWUP : AssistantIntent.SERVER_FACT,
                    AssistantMode.GROUNDED, language);
        }
        if (isServerFactQuestion(normalized)) {
            return new Analysis(AssistantIntent.SERVER_FACT, AssistantMode.GROUNDED, language);
        }
        if (normalized.contains("hoe ") || normalized.contains("how ") || normalized.startsWith("waar ")
                || normalized.startsWith("where ") || normalized.contains("waarom") || normalized.contains("why ")
                || normalized.contains("help") || containsAny(normalized, GAMEPLAY_WORDS)) {
            return new Analysis(AssistantIntent.GAMEPLAY_HELP, AssistantMode.GROUNDED, language);
        }
        if (context.active() && isContextualFollowUp(normalized, context)) {
            AssistantMode mode = context.previousIntent() == AssistantIntent.SERVER_FACT
                    || context.previousIntent() == AssistantIntent.KNOWLEDGE_DISCOVERY
                    || context.previousIntent() == AssistantIntent.GAMEPLAY_HELP
                    || context.previousIntent() == AssistantIntent.EVENT_RECALL
                    || context.previousIntent() == AssistantIntent.MEMORY_RECALL
                    || context.previousIntent() == AssistantIntent.LIVE_STATE
                    ? AssistantMode.GROUNDED : AssistantMode.FAST;
            return new Analysis(AssistantIntent.CONTEXT_FOLLOWUP, mode, language);
        }
        return new Analysis(AssistantIntent.CONVERSATION, AssistantMode.FAST, language);
    }

    private static boolean isServerFactQuestion(String message) {
        if (message.contains("/")) {
            return true;
        }
        if (containsAny(message, STRONG_SERVER_WORDS)) {
            return true;
        }
        if (!containsAny(message, VERSION_WORDS)) {
            return false;
        }

        // Words such as "version" and "update" are ambiguous. A question about a Minecraft version is general
        // gameplay knowledge even when the player happens to address Haunty by name. Only explicit AIlex/server context
        // turns those words into HauntedMC-specific facts.
        if (containsAny(message, GAMEPLAY_WORDS)
                && !containsAnyPhrase(message, "server versie", "server version", "versie van de server",
                "version of the server", "minecraft versie van de server", "minecraft version of the server")) {
            return false;
        }
        return containsAnyPhrase(message,
                "haunty versie", "haunty version", "ailex versie", "ailex version",
                "server versie", "server version", "versie van de server", "version of the server",
                "hauntedmc versie", "hauntedmc version", "plugin versie", "plugin version",
                "nieuwe haunty", "new haunty", "nieuwe ailex", "new ailex"
        );
    }

    private static boolean isKnowledgeDiscovery(String message) {
        return containsAnyPhrase(message,
                "fun fact", "random fact", "interesting fact", "tell me a fact", "tell me something about the server",
                "tell me something about haunted", "what do you know about haunted", "what do you know about the server",
                "leuk feitje", "willekeurig feitje", "interessant feit", "vertel een feitje", "vertel iets over de server",
                "vertel iets over haunted", "wat weet je over haunted", "wat weet je over de server", "server weetje",
                "welke functies heb je", "wat zijn je functies", "wat kan je allemaal", "wat kun je allemaal",
                "wat kan jij allemaal", "wat kun jij allemaal", "waarmee kan je helpen", "waarmee kun je helpen",
                "what can you do", "what are your capabilities", "what features do you have", "how can you help"
        );
    }

    private static boolean isDirectMemoryRecall(String message) {
        return containsAnyPhrase(message,
                "wat weet je van mij", "wat weet je over mij", "wat herinner je van mij", "wat herinner je over mij",
                "wat heb je onthouden", "wat onthoud je van mij", "herinner je mij", "herinner je je mij",
                "what do you remember about me", "what do you know about me", "what have you remembered about me",
                "what have you saved about me", "do you remember me"
        );
    }

    private static boolean isDirectEventRecall(String message) {
        return containsAnyPhrase(message,
                "wat gebeurde er", "wat is er gebeurd", "wat gebeurde vorige keer", "wat gebeurde er vorige keer",
                "vorige keer gebeurde", "wat gebeurde eerder", "eerder vandaag gebeurde", "weet je nog wat er gebeurde",
                "what happened", "what happened last time", "what happened earlier", "what happened before",
                "last time what happened", "do you remember what happened"
        );
    }

    private static boolean isLiveStateQuestion(String message) {
        if (containsAny(message, STRONG_LIVE_WORDS) || containsLivePhrase(message)) {
            return true;
        }
        if (hasCurrentSelfReference(message)
                && (containsAny(message, PLAYER_STATE_WORDS) || containsAny(message, WORLD_STATE_WORDS))) {
            return true;
        }
        if (containsAny(message, LOCAL_CUES) && containsAny(message, WORLD_STATE_WORDS)) {
            return true;
        }
        if (containsAnyPhrase(message,
                "welk bioom", "welke biome", "welke bioom", "what biome", "which biome",
                "welk blok", "welke block", "what block", "which block", "waar kijk ik", "what am i looking at",
                "welke kant kijk", "which way am i facing", "what direction am i facing",
                "welke wereld", "welk world", "what world", "which world", "welke dimensie", "what dimension")) {
            return true;
        }
        return containsAnyPhrase(
                message,
                "waar ben ik", "waar sta ik", "where am i", "where do i stand",
                "waar sta je", "waar ben jij", "where are you", "wat doe je", "what are you doing"
        );
    }

    private static boolean hasCurrentSelfReference(String message) {
        String normalized = message == null ? "" : message
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}']+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String padded = " " + normalized + " ";
        return containsAnyPhrase(
                padded,
                " mijn ", " my ", " heb ik ", " ik heb ", " ben ik ", " am i ", " i have ", " i'm ", " im ",
                " houd ik ", " hou ik ", " bij mij ", " for me "
        );
    }

    private static boolean containsLivePhrase(String message) {
        return containsAnyPhrase(message, "om me heen", "around me", "near me");
    }

    private static boolean isCorrection(String message) {
        return containsAnyPhrase(message,
                "klopt niet", "niet waar", "je hebt het fout", "je zit fout", "correctie", "eigenlijk is",
                "nee, ", "nee ", "that's wrong", "that is wrong", "you're wrong", "you are wrong", "not correct",
                "correction", "actually,", "actually "
        );
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
        if (context.pendingAnswer() && message.length() <= 160) {
            return true;
        }
        if (message.endsWith("?")) {
            return true;
        }
        return Set.of(
                "ja", "nee", "maar", "dus", "waarom", "hoezo", "wat", "welke", "waar", "wacht", "bedoel",
                "huh", "eigenlijk", "correctie", "yes", "no", "but", "so", "why", "how", "what", "which",
                "where", "wait", "actually", "correction"
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
