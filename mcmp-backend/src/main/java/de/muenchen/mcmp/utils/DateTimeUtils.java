package de.muenchen.mcmp.utils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public final class DateTimeUtils {

    private DateTimeUtils() {
        // Utility class
    }

    /**
     * Compares two OffsetDateTime objects after converting them to UTC and truncating to milliseconds.
     * This ignores timezone differences as well as nanosecond-level precision differences.
     *
     * @param dt1 First OffsetDateTime to compare
     * @param dt2 Second OffsetDateTime to compare
     * @return true if both represent the same point in time up to milliseconds in UTC, false otherwise
     */
    public static boolean isDateTimeEqualUTC(final OffsetDateTime dt1, final OffsetDateTime dt2) {
        if (dt1 == null && dt2 == null) {
            return true;
        }
        if (dt1 == null || dt2 == null) {
            return false;
        }

        final Instant instant1 = dt1.withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS)
                .toInstant();

        final Instant instant2 = dt2.withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS)
                .toInstant();

        return instant1.equals(instant2);
    }
}