package nl.hauntedmc.ailex.assistant.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.hauntedmc.ailex.assistant.action.AssistantActionProposal;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryCandidate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validated player-facing assistant output; evidence remains internal and action proposals remain non-authoritative. */
public record AssistantReply(
        List<String> lines,
        Set<String> evidenceIds,
        String confidence,
        String handoff,
        List<MemoryCandidate> memoryCandidates,
        List<AssistantActionProposal> actionProposals,
        Map<Integer, Set<String>> claimEvidence,
        boolean valid
) {
    public AssistantReply {
        lines = lines == null ? List.of() : List.copyOf(lines);
        evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        confidence = confidence == null ? "" : confidence.trim();
        handoff = handoff == null ? "" : handoff.trim();
        memoryCandidates = memoryCandidates == null ? List.of() : List.copyOf(memoryCandidates);
        actionProposals = actionProposals == null ? List.of() : List.copyOf(actionProposals);
        Map<Integer, Set<String>> normalized = new HashMap<>();
        if (claimEvidence != null) {
            claimEvidence.forEach((line, ids) -> {
                if (line != null && line >= 0 && ids != null && !ids.isEmpty()) {
                    normalized.put(line, Set.copyOf(ids));
                }
            });
        }
        claimEvidence = Map.copyOf(normalized);
        if (valid && !evidenceIds.isEmpty()) {
            for (int index = 0; index < lines.size(); index++) {
                Set<String> ids = claimEvidence.get(index);
                if (ids == null || ids.isEmpty()) {
                    valid = false;
                    break;
                }
            }
        }
    }

    /** Source-compatible constructor for callers that do not use embodied actions. */
    public AssistantReply(
            List<String> lines,
            Set<String> evidenceIds,
            String confidence,
            String handoff,
            List<MemoryCandidate> memoryCandidates,
            Map<Integer, Set<String>> claimEvidence,
            boolean valid
    ) {
        this(lines, evidenceIds, confidence, handoff, memoryCandidates, List.of(), claimEvidence, valid);
    }

    public static AssistantReply invalid() {
        return new AssistantReply(List.of(), Set.of(), "", "", List.of(), List.of(), Map.of(), false);
    }

    public static AssistantReply unavailable() {
        return fromPlainText("Ik kan nu even niet reageren.");
    }

    /**
     * Creates a plain player-facing reply while defensively stripping accidental model protocol envelopes.
     * Plain-text generation is never allowed to leak JSON metadata such as response/evidence fields into Minecraft chat.
     */
    public static AssistantReply fromPlainText(String text) {
        String safe = unwrapAccidentalEnvelope(text);
        safe = safe.replaceAll("\\s+", " ").trim();
        return new AssistantReply(
                safe.isBlank() ? List.of() : List.of(safe), Set.of(), "", "", List.of(), List.of(), Map.of(),
                !safe.isBlank()
        );
    }

    private static String unwrapAccidentalEnvelope(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String candidate = text.trim();
        if (candidate.startsWith("```json") && candidate.endsWith("```")) {
            candidate = candidate.substring(7, candidate.length() - 3).trim();
        } else if (candidate.startsWith("```") && candidate.endsWith("```")) {
            candidate = candidate.substring(3, candidate.length() - 3).trim();
        }

        boolean looksLikeEnvelope = candidate.startsWith("{") && candidate.endsWith("}");
        boolean startsLikeProtocol = candidate.startsWith("\"response\"")
                || candidate.startsWith("\"answer\"")
                || candidate.startsWith("\"lines\"")
                || candidate.startsWith("\"text\"")
                || candidate.startsWith("\"message\"")
                || candidate.startsWith("\"output\"");
        if (!looksLikeEnvelope && startsLikeProtocol) {
            if (!candidate.startsWith("{")) {
                candidate = '{' + candidate;
            }
            if (!candidate.endsWith("}")) {
                candidate = candidate + '}';
            }
            looksLikeEnvelope = true;
        }
        if (!looksLikeEnvelope) {
            return candidate;
        }

        try {
            JsonElement parsed = JsonParser.parseString(candidate);
            if (!parsed.isJsonObject()) {
                return candidate;
            }
            JsonObject object = parsed.getAsJsonObject();
            for (String key : List.of("response", "answer", "text", "message", "output")) {
                if (object.has(key) && object.get(key).isJsonPrimitive()
                        && object.get(key).getAsJsonPrimitive().isString()) {
                    return object.get(key).getAsString();
                }
            }
            if (object.has("lines") && object.get("lines").isJsonArray()) {
                JsonArray lines = object.getAsJsonArray("lines");
                List<String> playerLines = new ArrayList<>();
                for (JsonElement line : lines) {
                    if (line.isJsonPrimitive() && line.getAsJsonPrimitive().isString()
                            && !line.getAsString().isBlank()) {
                        playerLines.add(line.getAsString().trim());
                    }
                }
                return String.join(" ", playerLines);
            }
            // A JSON object on the plain-text path is protocol output, not player-facing prose. Fail closed.
            return "";
        } catch (RuntimeException ignored) {
            return candidate;
        }
    }

    public AssistantReply withHandoff(String value) {
        return new AssistantReply(
                lines, evidenceIds, confidence, value, memoryCandidates, actionProposals, claimEvidence, valid
        );
    }

    public Set<String> coveredEvidenceIds() {
        Set<String> covered = new HashSet<>();
        claimEvidence.values().forEach(covered::addAll);
        return Set.copyOf(covered);
    }

    /** Returns true only when every emitted line has at least one explicit supporting evidence mapping. */
    public boolean allLinesGrounded() {
        if (lines.isEmpty()) {
            return false;
        }
        for (int index = 0; index < lines.size(); index++) {
            Set<String> ids = claimEvidence.get(index);
            if (ids == null || ids.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
