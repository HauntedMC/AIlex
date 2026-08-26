package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic truth resolution over potentially conflicting temporal memory claims.
 *
 * <p>The LLM never decides freshness or source authority. Claims are grouped by subject/kind/predicate, filtered by
 * validity at the requested time, and scored from source authority, explicit confidence, recency and salience. Near-
 * tied conflicting values are marked disputed instead of silently choosing one.</p>
 */
public final class MemoryTruthResolver {

    private static final double DISPUTE_MARGIN = 0.075D;

    public List<ResolvedClaim> resolve(List<MemoryRecord> records, long atEpochMillis) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<String, List<MemoryClaim>> grouped = new LinkedHashMap<>();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            MemoryClaim claim = MemoryClaim.from(record, atEpochMillis);
            if (!claim.validAt(atEpochMillis)) {
                continue;
            }
            grouped.computeIfAbsent(claim.truthKey(), ignored -> new ArrayList<>()).add(claim);
        }

        List<ResolvedClaim> resolved = new ArrayList<>();
        grouped.values().forEach(group -> {
            group.sort(Comparator.comparingDouble((MemoryClaim claim) -> effectiveScore(claim, group, atEpochMillis)).reversed()
                    .thenComparing(Comparator.comparingLong(MemoryClaim::assertedAt).reversed()));
            if (group.isEmpty()) {
                return;
            }
            MemoryClaim primary = group.getFirst();
            double primaryScore = score(primary, atEpochMillis);
            List<MemoryClaim> alternatives = new ArrayList<>();
            boolean disputed = false;
            for (int index = 1; index < group.size(); index++) {
                MemoryClaim candidate = group.get(index);
                if (sameValue(primary.object(), candidate.object())) {
                    continue;
                }
                alternatives.add(candidate);
                if (primary.supersedes().equals(candidate.id())) {
                    continue;
                }
                if (primaryScore - score(candidate, atEpochMillis) <= DISPUTE_MARGIN) {
                    disputed = true;
                }
            }
            resolved.add(new ResolvedClaim(
                    primary,
                    List.copyOf(alternatives),
                    disputed ? MemoryClaimStatus.DISPUTED : MemoryClaimStatus.ACTIVE,
                    primaryScore
            ));
        });
        resolved.sort(Comparator.comparingDouble(ResolvedClaim::score).reversed()
                .thenComparing(Comparator.comparingLong(
                        (ResolvedClaim claim) -> claim.primary().assertedAt()
                ).reversed()));
        return List.copyOf(resolved);
    }

    public List<MemoryRecord> resolveRecords(List<MemoryRecord> records, long atEpochMillis) {
        return resolve(records, atEpochMillis).stream().map(ResolvedClaim::primary).map(claim -> records.stream()
                .filter(record -> record.id().equals(claim.id()))
                .findFirst()
                .orElseThrow()).toList();
    }

    public double score(MemoryClaim claim, long atEpochMillis) {
        long ageMillis = Math.max(0L, atEpochMillis - claim.assertedAt());
        double recency = 1.0D / (1.0D + ageMillis / (double) recencyHalfLife(claim.kind()).toMillis());
        double scope = switch (claim.scope()) {
            case PLAYER -> 1.0D;
            case PLAYER_NPC -> 0.96D;
            case GLOBAL -> 0.93D;
            case NPC -> 0.90D;
            case EVENT -> 0.88D;
            case SESSION -> 0.82D;
            case WORLD -> 0.78D;
        };
        return claim.authority() * 0.38D
                + claim.confidence() * 0.26D
                + recency * 0.14D
                + claim.salience() * 0.12D
                + scope * 0.10D;
    }

    private double effectiveScore(MemoryClaim claim, List<MemoryClaim> group, long atEpochMillis) {
        boolean explicitlySupersedesAnother = group.stream()
                .anyMatch(candidate -> claim.supersedes().equals(candidate.id()));
        return score(claim, atEpochMillis) + (explicitlySupersedesAnother ? 0.10D : 0.0D);
    }

    private Duration recencyHalfLife(MemoryKind kind) {
        return switch (kind) {
            case EVENT -> Duration.ofDays(3);
            case EPISODE -> Duration.ofDays(14);
            case GOAL -> Duration.ofDays(14);
            case RELATIONSHIP -> Duration.ofDays(60);
            case OPINION -> Duration.ofDays(90);
            case INTEREST -> Duration.ofDays(120);
            case PREFERENCE -> Duration.ofDays(180);
            case FACT -> Duration.ofDays(365);
        };
    }

    private boolean sameValue(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /** Deterministically resolved claim plus any conflicting alternatives retained for explanation/audit. */
    public record ResolvedClaim(
            MemoryClaim primary,
            List<MemoryClaim> alternatives,
            MemoryClaimStatus status,
            double score
    ) {
        public ResolvedClaim {
            alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
            status = status == null ? MemoryClaimStatus.ACTIVE : status;
            score = Math.clamp(score, 0.0D, 1.0D);
        }

        public boolean disputed() {
            return status == MemoryClaimStatus.DISPUTED;
        }
    }
}
