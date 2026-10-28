package com.oneyuv.tv.nepalicalendar;

import java.time.LocalDate;

/**
 * Offline Bikram Sambat (Nepali solar calendar) conversion.
 *
 * The month-length table and its encoding are copied from medic/bikram-sambat
 * (https://github.com/medic/bikram-sambat, java/lib/.../BsCalendar.java, Apache License 2.0,
 * see LICENSE-bikram-sambat). Changed here: java.time epoch days replace the string and
 * millisecond arithmetic, and dates outside the table return null instead of throwing.
 */
final class BikramSambat {
    static final int FIRST_YEAR = 1970;
    /** BS 1970-01-01 (Baisakh 1). */
    private static final LocalDate EPOCH = LocalDate.of(1913, 4, 13);
    /** Two bits per month, Baisakh in the lowest bits: days = 29 + value. */
    private static final long[] ENCODED_MONTH_LENGTHS = {
            5315258L, 5314490L, 9459438L, 8673005L, 5315258L, 5315066L, 9459438L, 8673005L, 5315258L, 5314298L,
            9459438L, 5327594L, 5315258L, 5314298L, 9459438L, 5327594L, 5315258L, 5314286L, 9459438L, 5315306L,
            5315258L, 5314286L, 8673006L, 5315306L, 5315258L, 5265134L, 8673006L, 5315258L, 5315258L, 9459438L,
            8673005L, 5315258L, 5314298L, 9459438L, 8673005L, 5315258L, 5314298L, 9459438L, 8473322L, 5315258L,
            5314298L, 9459438L, 5327594L, 5315258L, 5314298L, 9459438L, 5327594L, 5315258L, 5314286L, 8673006L,
            5315306L, 5315258L, 5265134L, 8673006L, 5315306L, 5315258L, 9459438L, 8673005L, 5315258L, 5314490L,
            9459438L, 8673005L, 5315258L, 5314298L, 9459438L, 8473325L, 5315258L, 5314298L, 9459438L, 5327594L,
            5315258L, 5314298L, 9459438L, 5327594L, 5315258L, 5314286L, 9459438L, 5315306L, 5315258L, 5265134L,
            8673006L, 5315306L, 5315258L, 5265134L, 8673006L, 5315258L, 5314490L, 9459438L, 8673005L, 5315258L,
            5314298L, 9459438L, 8669933L, 5315258L, 5314298L, 9459438L, 8473322L, 5315258L, 5314298L, 9459438L,
            5327594L, 5315258L, 5314286L, 9459438L, 5315306L, 5315258L, 5265134L, 8673006L, 5315306L, 5315258L,
            5265134L, 8673006L, 5315258L, 5315258L, 5527226L, 5528046L, 5527277L, 5528250L, 5528057L, 5527277L,
            5527277L
    };
    static final int LAST_YEAR = FIRST_YEAR + ENCODED_MONTH_LENGTHS.length - 1;

    final int year, month, day;

    BikramSambat(int year, int month, int day) { this.year = year; this.month = month; this.day = day; }

    static boolean supported(int year) { return year >= FIRST_YEAR && year <= LAST_YEAR; }

    /** 29 to 32; month is 1 (Baisakh) to 12 (Chaitra). */
    static int daysInMonth(int year, int month) {
        if (!supported(year) || month < 1 || month > 12) throw new IllegalArgumentException(year + "/" + month);
        return 29 + (int) ((ENCODED_MONTH_LENGTHS[year - FIRST_YEAR] >>> ((month - 1) << 1)) & 3);
    }

    /** The BS date of a Gregorian day, or null outside the table. */
    static BikramSambat of(LocalDate date) {
        long days = date.toEpochDay() - EPOCH.toEpochDay();
        if (days < 0) return null;
        for (int year = FIRST_YEAR; year <= LAST_YEAR; year++) {
            for (int month = 1; month <= 12; month++) {
                int length = daysInMonth(year, month);
                if (days < length) return new BikramSambat(year, month, (int) days + 1);
                days -= length;
            }
        }
        return null;
    }

    LocalDate toGregorian() {
        if (day < 1 || day > daysInMonth(year, month)) throw new IllegalArgumentException(toString());
        long days = day - 1;
        for (int y = FIRST_YEAR; y < year; y++) for (int m = 1; m <= 12; m++) days += daysInMonth(y, m);
        for (int m = 1; m < month; m++) days += daysInMonth(year, m);
        return EPOCH.plusDays(days);
    }

    /** The first day of the month a number of months away, or null outside the table. */
    BikramSambat plusMonths(int months) {
        int index = year * 12 + month - 1 + months;
        return supported(index / 12) ? new BikramSambat(index / 12, index % 12 + 1, 1) : null;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof BikramSambat)) return false;
        BikramSambat that = (BikramSambat) other;
        return year == that.year && month == that.month && day == that.day;
    }

    @Override public int hashCode() { return (year * 13 + month) * 32 + day; }

    @Override public String toString() { return String.format(java.util.Locale.ROOT, "%d-%02d-%02d", year, month, day); }
}
