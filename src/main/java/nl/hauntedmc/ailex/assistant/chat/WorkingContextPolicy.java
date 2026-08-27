package nl.hauntedmc.ailex.assistant.chat;

import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;

import java.util.Locale;

/** Decides when raw short-term chat history is worth spending prompt tokens on. */
public final class WorkingContextPolicy {

    private WorkingContextPolicy() {
    }

    public static boolean includeRawHistory(String message, AssistantDialogueContext dialogue) {
        AssistantIntent intent = AssistantIntentClassifier.analyze(message, dialogue).intent();
        // Durable MEMORY_RECALL already receives typed semantic memory plus bounded dialogue context. Automatically adding
        // the ambient/raw transcript duplicated context, regularly filled the request budget, and slowed the exact turns
        // that should primarily inspect remembered facts. EVENT_RECALL remains transcript-aware because recent chat can be
        // the event the player is explicitly asking about.
        if (intent == AssistantIntent.EVENT_RECALL) {
            return true;
        }
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return containsAny(text,
                "eerder", "net", "vorige", "chat", "zei", "gezegd", "gebeurde", "ging mis",
                "earlier", "just now", "previous", "said", "happened", "went wrong"
        );
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
