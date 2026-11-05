package com.lionlev.tv.jewishcalendar;

import org.junit.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import static org.junit.Assert.*;

public class JewishDateTest {
    @Test public void sunsetRollsForwardOnlyAtBoundaryAndHandlesDst() {
        for (LocalDate day : new LocalDate[]{LocalDate.of(2026, 3, 26), LocalDate.of(2026, 3, 27),
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 10, 25)}) {
            ZoneId zone = ZoneId.of("Asia/Jerusalem");
            int sunset = Sun.riseAndSet(day, 31.778, 35.235, zone)[1];
            ZonedDateTime boundary = day.atStartOfDay(zone).withHour(sunset / 60).withMinute(sunset % 60);
            assertEquals(day, JewishDate.day(boundary.minusMinutes(1), 31.778, 35.235));
            assertEquals(day.plusDays(1), JewishDate.day(boundary, 31.778, 35.235));
        }
    }
    @Test public void invalidOrPolarLocationDoesNotInventSunset() {
        assertFalse(JewishDate.validLocation(Double.NaN, 0));
        assertFalse(JewishDate.validLocation(0, Double.POSITIVE_INFINITY));
        assertFalse(JewishDate.validLocation(91, 0));
        assertFalse(JewishDate.validLocation(0, -181));
        assertNull(JewishDate.day(ZonedDateTime.parse("2026-06-21T12:00:00Z"), 90, 0));
    }
    @Test public void explicitRegionAndCountryBeatLanguage() {
        assertTrue(JewishDate.israel(new Locale("en", "IL"), "auto"));
        assertTrue(JewishDate.israel(new Locale("he"), "auto"));
        assertFalse(JewishDate.israel(new Locale("he", "US"), "auto"));
        assertFalse(JewishDate.israel(Locale.US, "auto"));
        assertTrue(JewishDate.israel(Locale.US, "israel"));
        assertFalse(JewishDate.israel(new Locale("he", "IL"), "diaspora"));
    }
    @Test public void holidayRangesAndDiasporaExtraDays() {
        assertEquals(JewishDate.NEW_YEAR, JewishDate.holiday(0, 1, 30, true));
        assertEquals(0, JewishDate.holiday(0, 3, 30, true));
        assertEquals(JewishDate.KIPPUR, JewishDate.holiday(0, 10, 30, true));
        assertEquals(JewishDate.HOLIDAY, JewishDate.holiday(0, 21, 30, true));
        assertEquals(JewishDate.HOLIDAY, JewishDate.holiday(0, 22, 30, true));
        assertEquals(JewishDate.HOLIDAY, JewishDate.holiday(0, 22, 30, false));
        assertEquals(JewishDate.HOLIDAY, JewishDate.holiday(0, 23, 30, false));
        assertEquals(0, JewishDate.holiday(0, 23, 30, true));
        assertEquals(JewishDate.HOLIDAY, JewishDate.holiday(7, 22, 30, false));
        assertEquals(0, JewishDate.holiday(7, 22, 30, true));
        assertEquals(JewishDate.HOLIDAY, JewishDate.holiday(9, 7, 30, false));
        assertEquals(0, JewishDate.holiday(9, 7, 30, true));
        assertEquals(0, JewishDate.holiday(5, 14, 30, true)); // Adar I is not Purim.
        assertEquals(JewishDate.PURIM, JewishDate.holiday(6, 14, 30, true));
        assertEquals(JewishDate.PURIM, JewishDate.holiday(6, 15, 30, true));
    }
    @Test public void omitYearThousandsWithoutChangingDaysOrLeapMonths() {
        assertEquals("ה׳ בחשוון תשפ״ז", JewishDate.withoutMillennium("ה׳ בחשוון ה׳תשפ״ז"));
        assertEquals("ט״ו באדר א׳ תשפ״ד", JewishDate.withoutMillennium("ט״ו באדר א׳ ה׳תשפ״ד"));
        assertEquals("ט״ז באדר ב׳ תשפ״ד", JewishDate.withoutMillennium("ט״ז באדר ב׳ ה׳תשפ״ד"));
        assertEquals("י״ג בחשוון תשפ״ז", JewishDate.withoutMillennium("י״ג בחשוון ה׳תשפ״ז"));
        assertEquals("א׳ בתשרי ה׳", JewishDate.withoutMillennium("א׳ בתשרי ה׳"));
        assertEquals("13 Heshvan 5787", JewishDate.withoutMillennium("13 Heshvan 5787"));
    }

    @Test public void holidaysAndTheirEvesOverrideShabbat() {
        LocalDate shabbat = LocalDate.of(2026, 10, 24);
        assertEquals(JewishDate.SHABBAT, JewishDate.preferredGreeting(0, 0, shabbat));
        assertEquals(JewishDate.NEW_YEAR,
                JewishDate.preferredGreeting(JewishDate.NEW_YEAR, 0, shabbat));
        assertEquals(JewishDate.HOLIDAY,
                JewishDate.preferredGreeting(0, JewishDate.HOLIDAY, LocalDate.of(2025, 4, 12)));
        assertEquals(JewishDate.HANUKKAH, JewishDate.preferredGreeting(
                JewishDate.HANUKKAH, JewishDate.NEW_YEAR, shabbat));
        assertEquals(0, JewishDate.preferredGreeting(0, 0, shabbat.plusDays(1)));
        assertEquals(JewishDate.SHABBAT,
                JewishDate.preferredGreeting(0, 0, LocalDate.of(1969, 12, 27)));
    }

    @Test public void hanukkahHasEightDaysWithShortOrLongKislev() {
        for (int length : new int[]{29, 30}) {
            int count = 0;
            for (int month : new int[]{2, 3}) for (int d = 1; d <= (month == 2 ? length : 29); d++)
                if (JewishDate.holiday(month, d, length, true) == JewishDate.HANUKKAH) count++;
            assertEquals(8, count);
        }
    }
}
