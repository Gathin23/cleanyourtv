package io.github.deepakola.familyrenewals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.LocalDate;

public class DateRulesTest {
    @Test
    public void parsesIsoDate() {
        assertEquals(LocalDate.of(2026, 11, 12), DateRules.parse("2026-11-12"));
    }

    @Test
    public void rejectsInvalidDate() {
        assertNull(DateRules.parse("2026-02-30"));
        assertNull(DateRules.parse("12/11/2026"));
    }

    @Test
    public void distinguishesOverdueTodayAndUpcoming() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        assertEquals("OVERDUE", DateRules.status("2026-11-02", today));
        assertEquals("DUE TODAY", DateRules.status("2026-11-03", today));
        assertEquals("DUE TOMORROW", DateRules.status("2026-11-04", today));
        assertEquals("DUE IN 7 DAYS", DateRules.status("2026-11-10", today));
        assertEquals("UPCOMING", DateRules.status("2026-11-11", today));
    }

    @Test
    public void invalidDueDateIsNotSilentlyAccepted() {
        assertThrows(IllegalArgumentException.class,
                () -> DateRules.daysUntil("not-a-date", LocalDate.of(2026, 11, 3)));
    }
}
