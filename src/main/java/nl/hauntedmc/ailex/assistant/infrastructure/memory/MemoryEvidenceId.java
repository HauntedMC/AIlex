package nl.hauntedmc.ailex.assistant.infrastructure.memory;

/** Stable player-facing evidence identifiers that preserve typed memory provenance. */
public final class MemoryEvidenceId {

    private MemoryEvidenceId() {
    }

    public static String forRecord(MemoryRecord record) {
        if (record == null || record.id().isBlank()) {
            return "";
        }
        String family = switch (record.kind()) {
            case EVENT -> "event";
            case EPISODE -> "episode";
            default -> record.scope() == MemoryScope.GLOBAL ? "shared" : "player";
        };
        return "memory." + family + '.' + record.id();
    }

    /** Extracts the repository record id from both typed v2 ids and legacy memory.&lt;uuid&gt; ids. */
    public static String recordId(String evidenceId) {
        if (evidenceId == null || !evidenceId.startsWith("memory.")) {
            return "";
        }
        String remainder = evidenceId.substring("memory.".length());
        for (String family : new String[]{"event.", "episode.", "shared.", "player."}) {
            if (remainder.startsWith(family)) {
                return remainder.substring(family.length());
            }
        }
        return remainder;
    }
}
