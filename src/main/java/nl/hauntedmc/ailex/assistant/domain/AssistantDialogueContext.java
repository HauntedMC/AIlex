package nl.hauntedmc.ailex.assistant.domain;

/**
 * Structured active dialogue supplied to routing and prompt compilation.
 * It contains only the bounded player↔assistant conversation, never ambient server chat.
 */
public record AssistantDialogueContext(
        boolean active,
        boolean pendingAnswer,
        AssistantIntent previousIntent,
        String previousUserMessage,
        String previousAssistantMessage,
        String recentTurns
) {
    public static AssistantDialogueContext empty() {
        return new AssistantDialogueContext(false, false, null, "", "", "");
    }

    public AssistantDialogueContext {
        previousUserMessage = compact(previousUserMessage, 640);
        previousAssistantMessage = compact(previousAssistantMessage, 640);
        recentTurns = compactMultiline(recentTurns, 6_000);
    }

    private static String compact(String value, int maximum) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum - 1) + "…";
    }

    private static String compactMultiline(String value, int maximum) {
        String normalized = value == null ? "" : value.replaceAll("[\\t\\x0B\\f\\r ]+", " ").trim();
        if (normalized.length() <= maximum) {
            return normalized;
        }
        return "…" + normalized.substring(normalized.length() - maximum + 1);
    }
}
