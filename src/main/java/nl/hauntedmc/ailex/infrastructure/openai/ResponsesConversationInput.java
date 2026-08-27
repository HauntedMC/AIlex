package nl.hauntedmc.ailex.infrastructure.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the bounded AIlex dialogue section into real Responses API role messages.
 *
 * <p>ConversationManager deliberately serializes recent turns in a simple internal form so context budgeting remains
 * provider-independent. At the provider boundary we restore those turns to {@code user}/{@code assistant} roles instead
 * of asking the model to interpret a fake transcript embedded inside one new user message.</p>
 */
final class ResponsesConversationInput {

    private static final String DIALOGUE_MARKER = "[Active player-assistant dialogue]\n";
    private static final String DIALOGUE_STATE_HEADING = "[Dialogue state]\n";
    private static final int MAX_ROLE_MESSAGES = 28;
    private static final Pattern TURN = Pattern.compile("^(user|assistant)\\([^)]*\\):\\s*(.+)$");

    private ResponsesConversationInput() {
    }

    static Parsed parse(String prompt) {
        String source = prompt == null ? "" : prompt;
        int marker = markerIndex(source);
        if (marker < 0) {
            return new Parsed(source, List.of());
        }
        int markerLength = DIALOGUE_MARKER.length();
        int contentStart = marker + markerLength;
        int nextSection = source.indexOf("\n\n[", contentStart);
        int contentEnd = nextSection < 0 ? source.length() : nextSection;
        String dialogue = source.substring(contentStart, contentEnd).trim();

        List<RoleMessage> messages = new ArrayList<>();
        List<String> state = new ArrayList<>();
        for (String rawLine : dialogue.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            Matcher matcher = TURN.matcher(line);
            if (matcher.matches()) {
                messages.add(new RoleMessage(matcher.group(1), matcher.group(2).trim()));
                continue;
            }
            state.add(line);
        }
        if (messages.isEmpty()) {
            return new Parsed(source, List.of());
        }
        if (messages.size() > MAX_ROLE_MESSAGES) {
            messages = new ArrayList<>(messages.subList(messages.size() - MAX_ROLE_MESSAGES, messages.size()));
        }

        int prefixEnd = marker;
        if (prefixEnd >= 2 && source.substring(prefixEnd - 2, prefixEnd).equals("\n\n")) {
            prefixEnd -= 2;
        }
        String prefix = source.substring(0, prefixEnd).trim();
        String suffix = nextSection < 0 ? "" : source.substring(nextSection).trim();
        StringBuilder current = new StringBuilder(prefix);
        if (!state.isEmpty()) {
            appendSection(current, DIALOGUE_STATE_HEADING + String.join("\n", state));
        }
        if (!suffix.isBlank()) {
            appendSection(current, suffix);
        }
        return new Parsed(current.toString().trim(), List.copyOf(messages));
    }

    private static int markerIndex(String source) {
        if (source.startsWith(DIALOGUE_MARKER)) {
            return 0;
        }
        String separated = "\n\n" + DIALOGUE_MARKER;
        int found = source.indexOf(separated);
        return found < 0 ? -1 : found + 2;
    }

    private static void appendSection(StringBuilder target, String section) {
        if (section == null || section.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append("\n\n");
        }
        target.append(section.trim());
    }

    record Parsed(String currentPrompt, List<RoleMessage> history) {
        Parsed {
            currentPrompt = currentPrompt == null ? "" : currentPrompt;
            history = history == null ? List.of() : List.copyOf(history);
        }
    }

    record RoleMessage(String role, String text) {
        RoleMessage {
            role = "assistant".equals(role) ? "assistant" : "user";
            text = text == null ? "" : text.trim();
        }
    }
}
