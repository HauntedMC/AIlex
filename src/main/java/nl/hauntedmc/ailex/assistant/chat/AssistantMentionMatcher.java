package nl.hauntedmc.ailex.assistant.chat;

import java.util.Locale;

/** Exact, case-insensitive assistant-name matching with Minecraft-name boundaries. */
public final class AssistantMentionMatcher {

    private AssistantMentionMatcher() {
    }

    public static boolean isMentioned(String message, String name) {
        if (message == null || message.isBlank() || name == null || name.isBlank()) {
            return false;
        }
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        String normalizedName = name.toLowerCase(Locale.ROOT);
        int index = normalizedMessage.indexOf(normalizedName);
        while (index >= 0) {
            int before = index - 1;
            int after = index + normalizedName.length();
            boolean validBefore = before < 0 || !isNameCharacter(normalizedMessage.charAt(before));
            boolean validAfter = after >= normalizedMessage.length() || !isNameCharacter(normalizedMessage.charAt(after));
            if (validBefore && validAfter) {
                return true;
            }
            index = normalizedMessage.indexOf(normalizedName, index + normalizedName.length());
        }
        return false;
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}
