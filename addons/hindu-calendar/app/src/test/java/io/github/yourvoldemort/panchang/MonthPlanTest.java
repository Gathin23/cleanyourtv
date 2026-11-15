package io.github.yourvoldemort.panchang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

/**
 * The month grid is six weeks of cells, Sunday first, filled from the same month the day screen
 * names. Every civil date of the month appears once, with its number, tithi and nakshatra.
 */
public class MonthPlanTest {
    private static final City DELHI = City.cityNamed("Delhi");

    @Test
    public void theGridHoldsEveryDayOfTheMonthOnce() {
        for (Region region : Region.values()) {
            MonthPlan plan = MonthPlan.of(LocalDate.of(2026, 9, 20), DELHI, region);
            assertEquals("the first date's weekday sets the blanks",
                    plan.first.getDayOfWeek().getValue() % 7, plan.leading);
            for (int i = 0; i < plan.leading; i++) assertNull(plan.cells[i]);
            assertEquals(plan.first, plan.cells[plan.leading].date);

            int count = 0;
            for (int i = 0; i < MonthPlan.CELLS; i++) {
                MonthPlan.Cell cell = plan.cells[i];
                if (cell == null) continue;
                count++;
                assertTrue(!cell.date.isBefore(plan.first));
                assertTrue(!cell.date.isAfter(plan.last));
                assertTrue(cell.dayNumber >= 1);
                assertTrue(cell.tithi >= 1 && cell.tithi <= 30);
                assertTrue(cell.nakshatra >= 1 && cell.nakshatra <= 27);
                if (cell.date.isBefore(plan.last)) {
                    assertEquals(cell.date.plusDays(1), plan.cells[i + 1].date);
                }
            }
            assertEquals("one cell per civil date, no gaps",
                    plan.last.toEpochDay() - plan.first.toEpochDay() + 1, count);
            assertTrue("six rows are enough", plan.leading + count <= MonthPlan.CELLS);
            assertTrue("the next month starts after this one",
                    plan.last.isBefore(MonthPlan.of(plan.last.plusDays(1), DELHI, region).first));
        }
    }

    @Test
    public void aSolarMonthNumbersItsDaysFromOne() {
        MonthPlan plan = MonthPlan.of(LocalDate.of(2026, 6, 15),
                City.cityNamed("Chennai"), Region.TAMIL);
        int expected = 1;
        for (MonthPlan.Cell cell : plan.cells) {
            if (cell != null) assertEquals(expected++, cell.dayNumber);
        }
        assertEquals(plan.last.toEpochDay() - plan.first.toEpochDay() + 1, expected - 1);
    }

    @Test
    public void theMarkedTithisAreMarkedInTheGrid() {
        MonthPlan plan = MonthPlan.of(LocalDate.of(2025, 3, 14), DELHI, Region.NORTH);
        MonthPlan.Cell holi = null;
        for (MonthPlan.Cell cell : plan.cells) {
            if (cell != null && cell.date.equals(LocalDate.of(2025, 3, 14))) holi = cell;
        }
        assertNotNull(holi);
        assertEquals(15, holi.tithi);
        assertTrue(holi.marked());
    }

    @Test
    public void aDateAndItsSunriseFindTheSameMonth() {
        LocalDate date = LocalDate.of(2026, 6, 15);
        Panchang p = Panchang.at(date, DELHI);
        for (Region region : Region.values()) {
            MonthSpan byDate = MonthSpan.of(date, DELHI, region);
            MonthSpan bySunrise = MonthSpan.of(p.sunrise, DELHI, region);
            assertEquals(region.name(), bySunrise.startMillis, byDate.startMillis);
            assertEquals(region.name(), bySunrise.nextMillis, byDate.nextMillis);
        }
    }
}