package io.github.gagantk.kannadacalendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The days the cache holds, looked up by date. */
final class Book {
    private final Map<LocalDate, List<Entry>> byDay = new HashMap<>();
    private LocalDate first, last;
    /** Saved before holidays were read: fetch it again. */
    boolean legacy;

    Book(List<Entry> entries) {
        for (Entry e : entries) {
            byDay.computeIfAbsent(e.date, d -> new ArrayList<>()).add(e);
            if (first == null || e.date.isBefore(first)) first = e.date;
            if (last == null || e.date.isAfter(last)) last = e.date;
        }
    }

    boolean covers(LocalDate date) { return byDay.containsKey(date); }

    LocalDate first() { return first; }

    LocalDate last() { return last; }

    /**
     * The day sorted out, or an empty one when the calendar has nothing for that date. Each is sorted out once:
     * the page asks for every day of the month on each move of the remote.
     */
    Day day(LocalDate date) {
        return days.computeIfAbsent(date, d -> {
            List<Entry> entries = byDay.get(d);
            return Day.of(d, entries == null ? new ArrayList<>() : entries);
        });
    }

    private final Map<LocalDate, Day> days = new HashMap<>();
}
