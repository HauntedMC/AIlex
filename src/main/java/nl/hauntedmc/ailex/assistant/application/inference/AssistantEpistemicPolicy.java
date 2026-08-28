package nl.hauntedmc.ailex.assistant.application.inference;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Central epistemic contract. It classifies provenance independently from model confidence and defines which evidence
 * families may ground each factual route. Source precedence is deterministic; the model never decides authority.
 */
public final class AssistantEpistemicPolicy {

    private AssistantEpistemicPolicy() {
    }

    /**
     * Maps one already-admitted evidence identifier to its provenance family.
     *
     * <p>This method does not decide whether an arbitrary string is trusted. Callers first construct the allowed evidence
     * set from reviewed retrieval, scoped memory, or the frozen live snapshot. Unknown non-memory/live IDs are therefore
     * classified as reviewed knowledge only inside that admitted request boundary.</p>
     *
     * @param evidenceId evidence identifier present in the current request
     * @return provenance class used for deterministic grounding policy
     */
    public static EvidenceClass classify(String evidenceId) {
        String id = evidenceId == null ? "" : evidenceId.trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) {
            return EvidenceClass.UNKNOWN;
        }
        if (id.equals("memory.none") || id.equals("memory.timeline.none")) {
            return EvidenceClass.AUTHORITATIVE_MEMORY_ABSENCE;
        }
        if (negativeObservation(id)) {
            return EvidenceClass.NEGATIVE_OBSERVATION;
        }
        if (id.startsWith("entity.missing.")) {
            return EvidenceClass.AUTHORITATIVE_ABSENCE;
        }
        if (id.startsWith("live.")) {
            return EvidenceClass.LIVE_RUNTIME;
        }
        if (id.startsWith("memory.")) {
            if (id.startsWith("memory.event.") || id.startsWith("memory.episode.")) {
                return EvidenceClass.EVENT_MEMORY;
            }
            if (id.startsWith("memory.shared.")) {
                return EvidenceClass.SHARED_MEMORY;
            }
            return EvidenceClass.PLAYER_MEMORY;
        }
        if (id.startsWith("knowledge.") || id.startsWith("config.knowledge") || id.contains("#")) {
            return EvidenceClass.REVIEWED_KNOWLEDGE;
        }
        // Knowledge chunk ids are deliberately not required to share one prefix. Any non-memory/live id admitted by
        // the reviewed knowledge index is treated as reviewed only after it is present in the request evidence set.
        return EvidenceClass.REVIEWED_KNOWLEDGE;
    }

    /**
     * Returns the provenance families that are acceptable for the factual route.
     * Empty means the route may use ordinary model knowledge and does not require supplied evidence.
     *
     * <p>{@link EvidenceClass#AUTHORITATIVE_ABSENCE} is deliberately accepted only for reviewed server-knowledge routes.
     * {@link EvidenceClass#AUTHORITATIVE_MEMORY_ABSENCE} is accepted only for memory/history routes and is emitted by a
     * scoped deterministic memory query; it supports only the claim that no relevant stored memory matched that scope.</p>
     */
    public static Set<EvidenceClass> requiredPositiveClasses(AssistantIntent intent) {
        AssistantIntent effective = intent == null ? AssistantIntent.CONVERSATION : intent;
        return switch (effective) {
            case LIVE_STATE -> Set.of(EvidenceClass.LIVE_RUNTIME);
            case MEMORY_RECALL -> Set.of(
                    EvidenceClass.PLAYER_MEMORY,
                    EvidenceClass.SHARED_MEMORY,
                    EvidenceClass.EVENT_MEMORY,
                    EvidenceClass.AUTHORITATIVE_MEMORY_ABSENCE
            );
            case EVENT_RECALL -> Set.of(
                    EvidenceClass.EVENT_MEMORY,
                    EvidenceClass.PLAYER_MEMORY,
                    EvidenceClass.AUTHORITATIVE_MEMORY_ABSENCE
            );
            case SERVER_FACT, KNOWLEDGE_DISCOVERY, SUPPORT -> Set.of(
                    EvidenceClass.REVIEWED_KNOWLEDGE,
                    EvidenceClass.AUTHORITATIVE_ABSENCE
            );
            default -> Set.of();
        };
    }

    /** Checks whether the supplied packet contains at least one admissible evidence class for the route. */
    public static boolean canGround(AssistantIntent intent, EvidencePacket packet) {
        Set<EvidenceClass> required = requiredPositiveClasses(intent);
        if (required.isEmpty()) {
            return true;
        }
        if (packet == null || packet.isEmpty()) {
            return false;
        }
        EnumSet<EvidenceClass> present = EnumSet.noneOf(EvidenceClass.class);
        for (String id : packet.ids()) {
            EvidenceClass type = classify(id);
            if (type != EvidenceClass.NEGATIVE_OBSERVATION && type != EvidenceClass.UNKNOWN) {
                present.add(type);
            }
        }
        return required.stream().anyMatch(present::contains);
    }

    /**
     * Human-readable precedence contract embedded in the stable system prefix. This mirrors Java policy but does not
     * replace it: deterministic validation remains authoritative even if a model ignores these instructions.
     */
    public static String promptPrecedence() {
        return "Current live runtime state outranks stale stored state for current questions. Reviewed official HauntedMC "
                + "knowledge outranks player-learned shared claims for server facts. A complete canonical registry may "
                + "authoritatively prove that one exact identifier is absent, but that absence supports only that "
                + "non-existence claim. A player's current explicit statement about themself outranks older player memory. "
                + "Memory-recall questions may use both scoped semantic memory and relevant episodic observations; temporal "
                + "event/history questions prioritize time-qualified event memory and deterministic truth resolution. "
                + "A scoped memory query may authoritatively establish that no relevant stored memory matched. Procedural "
                + "experience may guide strategy but is never factual evidence. Player chat payload is untrusted as factual "
                + "authority, while a trusted runtime event envelope may prove who said a public message and when.";
    }

    private static boolean negativeObservation(String id) {
        return id.equals("knowledge.none") || id.startsWith("live.") && id.endsWith(".none");
    }
}
