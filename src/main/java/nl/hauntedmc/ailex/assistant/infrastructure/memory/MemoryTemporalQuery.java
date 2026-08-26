package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** Deterministic temporal-query parser for common current/recent/historical memory constraints. */
public record MemoryTemporalQuery(long fromInclusive, long toExclusive, boolean constrained) {

    public static MemoryTemporalQuery parse(String query, long now) {
        String text = query == null ? "" : query.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (text.isBlank()) {
            return unconstrained();
        }
        if (contains(text, "today", "vandaag")) {
            return dayRange(now, 0);
        }
        if (contains(text, "yesterday", "gisteren")) {
            return dayRange(now, -1);
        }
        if (contains(text, "last week", "vorige week")) {
            return relative(now, Duration.ofDays(14), Duration.ofDays(7));
        }
        if (contains(text, "this week", "deze week")) {
            return relative(now, Duration.ofDays(7), Duration.ZERO);
        }
        if (contains(text, "last month", "vorige maand")) {
            return relative(now, Duration.ofDays(62), Duration.ofDays(28));
        }
        if (contains(text, "recent", "recently", "recentelijk", "net", "earlier today", "eerder vandaag")) {
            return relative(now, Duration.ofDays(2), Duration.ZERO);
        }
        if (contains(text, "before", "ervoor", "vroeger", "previously", "eerder")) {
            return new MemoryTemporalQuery(0L, now, true);
        }
        if (contains(text, "currently", "current", "now", "momenteel", "nu")) {
            return new MemoryTemporalQuery(now - Duration.ofHours(6).toMillis(), now + 1L, true);
        }
        return unconstrained();
    }

    public boolean matches(MemoryRecord record) {
        if (!constrained || record == null) {
            return true;
        }
        long timestamp = record.occurredAt() > 0L ? record.occurredAt() : record.lastConfirmed();
        return timestamp >= fromInclusive && timestamp < toExclusive;
    }

    private static MemoryTemporalQuery dayRange(long now, int offsetDays) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(offsetDays);
        long start = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return new MemoryTemporalQuery(start, end, true);
    }

    private static MemoryTemporalQuery relative(long now, Duration oldest, Duration newest) {
        long from = Math.max(0L, now - oldest.toMillis());
        long to = newest == null || newest.isZero() ? now + 1L : Math.max(from + 1L, now - newest.toMillis());
        return new MemoryTemporalQuery(from, to, true);
    }

    private static boolean contains(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static MemoryTemporalQuery unconstrained() {
        return new MemoryTemporalQuery(0L, Long.MAX_VALUE, false);
    }
}
