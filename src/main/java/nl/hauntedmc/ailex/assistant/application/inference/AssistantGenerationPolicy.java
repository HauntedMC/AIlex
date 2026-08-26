package nl.hauntedmc.ailex.assistant.application.inference;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;

import java.util.Locale;
import java.util.regex.Pattern;

/** Cheap deterministic inference policy so model calls are reserved for work that benefits from them. */
public final class AssistantGenerationPolicy {

    private static final Pattern EXPLICIT_FIRST_PERSON_FACT = Pattern.compile(
            "(?:^|[.!?]\\s*)(?:ik heb|ik ben|mijn [\\p{L}0-9 _-]{1,40} (?:is|zijn)|"
                    + "i have|i am|i'm|my [a-z0-9 _-]{1,40} (?:is|are))\\b.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private AssistantGenerationPolicy() {
    }

    public static boolean useStructuredOutput(
            boolean structuredOutputEnabled,
            AssistantMode mode,
            AssistantIntent intent,
            String playerMessage
    ) {
        if (!structuredOutputEnabled) {
            return false;
        }
        if (mode != AssistantMode.FAST) {
            return true;
        }
        return hasDurableMemorySignal(playerMessage)
                || hasEmbodiedActionSignal(playerMessage)
                || intent == AssistantIntent.MEMORY_RECALL
                || intent == AssistantIntent.EVENT_RECALL;
    }

    /**
     * Enables the structured envelope for explicit first-person information as well as explicit memory operations.
     * The memory validator remains the authority on whether a proposed fact is sufficiently durable, supported and safe;
     * this method merely makes extraction possible without adding a second model call.
     */
    public static boolean hasDurableMemorySignal(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT).trim();
        return EXPLICIT_FIRST_PERSON_FACT.matcher(normalized).matches()
                || normalized.contains("onthoud")
                || normalized.contains("remember")
                || normalized.contains("vergeet")
                || normalized.contains("forget")
                || normalized.contains("ik hou van")
                || normalized.contains("ik vind ")
                || normalized.contains("mijn favoriete")
                || normalized.contains("mijn voorkeur")
                || normalized.contains("ik speel graag")
                || normalized.contains("ik speel veel")
                || normalized.contains("ik ben fan")
                || normalized.contains("ik ben geïnteresseerd")
                || normalized.contains("ik ben geinteresseerd")
                || normalized.contains("mijn doel")
                || normalized.contains("ik probeer")
                || normalized.contains("ik ben bezig met")
                || normalized.contains("ik werk aan")
                || normalized.contains("ik spaar voor")
                || normalized.contains("i like ")
                || normalized.contains("i love ")
                || normalized.contains("i prefer ")
                || normalized.contains("my favorite")
                || normalized.contains("i am a fan")
                || normalized.contains("i'm interested")
                || normalized.contains("i am interested")
                || normalized.contains("my goal")
                || normalized.contains("i'm trying")
                || normalized.contains("i am trying")
                || normalized.contains("i'm working on")
                || normalized.contains("i am working on")
                || normalized.contains("i'm saving for")
                || normalized.contains("i am saving for")
                || normalized.contains("klopt niet")
                || normalized.contains("you're wrong")
                || normalized.contains("that is wrong")
                || normalized.contains("actually");
    }

    public static boolean hasEmbodiedActionSignal(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return containsAny(normalized,
                "volg mij", "volg me", "loop met me mee", "follow me", "come with me", "walk with me",
                "kom hier", "kom naar mij", "kom naar me", "come here", "come to me", "walk over here",
                "stop met lopen", "blijf hier", "blijf staan", "stop moving", "stay here", "halt");
    }

    public static boolean mayEscalate(AssistantMode mode, int modelCalls, int maximumModelCalls, long remainingMillis) {
        return (mode == AssistantMode.GROUNDED || mode == AssistantMode.DELIBERATE)
                && modelCalls < maximumModelCalls
                && remainingMillis >= 2_000L;
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
