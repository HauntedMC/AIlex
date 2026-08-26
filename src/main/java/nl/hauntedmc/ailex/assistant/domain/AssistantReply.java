package nl.hauntedmc.ailex.assistant.domain;

import java.util.List;
import java.util.Set;

/** Validated player-facing assistant output; evidence remains internal by default. */
public record AssistantReply(
        List<String> lines,
        Set<String> evidenceIds,
        String confidence,
        String handoff,
        List<String> memoryCandidates,
        boolean valid
) {
    public static AssistantReply invalid() {
        return new AssistantReply(List.of(), Set.of(), "", "", List.of(), false);
    }

    public static AssistantReply unavailable() {
        return fromPlainText("Ik kan nu even niet reageren.");
    }

    public static AssistantReply fromPlainText(String text) {
        String safe = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return new AssistantReply(safe.isBlank() ? List.of() : List.of(safe), Set.of(), "", "", List.of(),
                !safe.isBlank());
    }

    public AssistantReply withHandoff(String value) {
        return new AssistantReply(lines, evidenceIds, confidence, value, memoryCandidates, valid);
    }
}
