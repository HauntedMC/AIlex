package nl.hauntedmc.ailex.assistant.domain;

import nl.hauntedmc.ailex.assistant.action.AssistantActionProposal;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryCandidate;

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

    public static AssistantReply fromPlainText(String text) {
        String safe = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return new AssistantReply(
                safe.isBlank() ? List.of() : List.of(safe), Set.of(), "", "", List.of(), List.of(), Map.of(),
                !safe.isBlank()
        );
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
