package io.github.yourvoldemort.panchang;

import java.time.LocalDate;

/**
 * Six weeks of cells for the month grid: which date each cell holds, the number the calendar prints
 * for it, and the tithi and nakshatra at its sunrise. Sunday is the first column, as the weekday
 * header and the muhurta table read.
 */
final class MonthPlan {
    /** Six rows of seven, enough for any lunar or solar month wherever it starts. */
    static final int CELLS = 42;

    final MonthSpan span;
    final LocalDate first, last;
    /** Blank cells before the first date, 0 (Sunday) to 6 (Saturday). */
    final int leading;
    final Cell[] cells = new Cell[CELLS];

    static final class Cell {
        final LocalDate date;
        final int dayNumber, tithi, nakshatra;

        private Cell(LocalDate date, int dayNumber, int tithi, int nakshatra) {
            this.date = date;
            this.dayNumber = dayNumber;
            this.tithi = tithi;
            this.nakshatra = nakshatra;
        }

        /** Purnima, Amavasya or Ekadashi at this cell's sunrise. */
        boolean marked() { return Panchang.specialDay(tithi) != null; }
    }

    private MonthPlan(MonthSpan span, LocalDate first, LocalDate last, int leading) {
        this.span = span;
        this.first = first;
        this.last = last;
        this.leading = leading;
    }

    static MonthPlan of(LocalDate date, City city, Region region) {
        MonthSpan span = MonthSpan.of(date, city, region);
        LocalDate first = span.firstDate();
        LocalDate last = span.lastDate();
        MonthPlan plan = new MonthPlan(span, first, last, first.getDayOfWeek().getValue() % 7);
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            long rise = sunrise(day, city);
            int index = plan.leading + (int) (day.toEpochDay() - first.toEpochDay());
            plan.cells[index] = new Cell(day, span.dayNumber(day),
                    Panchang.tithiAt(rise), Panchang.nakshatraAt(rise));
        }
        return plan;
    }

    private static long sunrise(LocalDate date, City city) {
        long[] riseSet = Astro.sunriseSunset(date, city);
        return riseSet != null ? riseSet[0]
                : date.atTime(12, 0).atZone(city.zone).toInstant().toEpochMilli();
    }
}