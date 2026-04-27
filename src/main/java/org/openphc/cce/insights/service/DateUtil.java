package org.openphc.cce.insights.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

public final class DateUtil {

    private static final Set<String> VALID_INTERVALS = Set.of("daily", "weekly", "monthly");

    private DateUtil() {}

    /**
     * Validate and map a user-supplied interval to a PostgreSQL DATE_TRUNC granularity.
     * Only allows 'daily', 'weekly', 'monthly' — defaults to 'week' for null.
     */
    public static String mapInterval(String interval) {
        if (interval == null) return "week";
        String lower = interval.toLowerCase();
        if (!VALID_INTERVALS.contains(lower)) {
            throw new IllegalArgumentException("Invalid interval: " + interval
                    + ". Allowed values: daily, weekly, monthly");
        }
        return switch (lower) {
            case "daily" -> "day";
            case "weekly" -> "week";
            case "monthly" -> "month";
            default -> "week";
        };
    }

    public static String extractDate(Object obj) {
        if (obj instanceof Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString();
        }
        if (obj instanceof Instant inst) {
            return inst.atOffset(ZoneOffset.UTC).toLocalDate().toString();
        }
        if (obj instanceof OffsetDateTime odt) {
            return odt.toLocalDate().toString();
        }
        return obj.toString();
    }

    public static OffsetDateTime toOffsetDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof OffsetDateTime odt) return odt;
        if (obj instanceof Instant inst) return inst.atOffset(ZoneOffset.UTC);
        if (obj instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(obj.toString());
    }
}
