package nl.hauntedmc.ailex.assistant.domain;

/** Small structured dialogue state supplied to routing; raw transcripts remain outside the router. */
public record AssistantDialogueContext(
        boolean active,
        boolean pendingAnswer,
        AssistantIntent previousIntent,
        String previousUserMessage,
        String previousAssistantMessage
) {
    public static AssistantDialogueContext empty() {
        return new AssistantDialogueContext(false, false, null, "", "");
    }

    public AssistantDialogueContext {
        previousUserMessage = compact(previousUserMessage, 240);
        previousAssistantMessage = compact(previousAssistantMessage, 240);
    }

    private static String compact(String value, int maximum) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum - 1) + "…";
    }
}
