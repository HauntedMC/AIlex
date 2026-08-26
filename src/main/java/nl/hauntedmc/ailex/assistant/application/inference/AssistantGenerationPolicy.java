package nl.hauntedmc.ailex.assistant.application.inference;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;

import java.util.Locale;

/** Cheap deterministic inference policy so model calls are reserved for work that benefits from them. */
public final class AssistantGenerationPolicy {

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
                || intent == AssistantIntent.MEMORY_RECALL
                || intent == AssistantIntent.EVENT_RECALL;
    }

    public static boolean hasDurableMemorySignal(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("onthoud")
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

    public static boolean mayEscalate(AssistantMode mode, int modelCalls, int maximumModelCalls, long remainingMillis) {
        return mode == AssistantMode.GROUNDED
                && modelCalls < maximumModelCalls
                && remainingMillis >= 2_000L;
    }
}
