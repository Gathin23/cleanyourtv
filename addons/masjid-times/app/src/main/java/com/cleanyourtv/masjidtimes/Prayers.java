package com.cleanyourtv.masjidtimes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure logic, no Android: which prayer (start or jamaat) comes next, from a mosque's daily timetable. */
final class Prayers {
    private Prayers() {}

    static final String[] NAMES = {"Fajr", "Zuhr", "Asr", "Maghrib", "Isha"};
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");

    /** One day's row from the timetable: five start times and five jamaat times, as the API gives them. */
    static final class Day {
        final LocalDate date;
        final String[] start;
        final String[] jamaat;

        Day(LocalDate date, String[] start, String[] jamaat) {
            this.date = date;
            this.start = start;
            this.jamaat = jamaat;
        }
    }

    /** What Home shows: "Asr 14:00 · Jamaat 14:15" above "Asr in 1h 20m". */
    static final class Event {
        final String topLine;
        final String word;
        final long atMs;

        Event(String topLine, String word, long atMs) {
            this.topLine = topLine;
            this.word = word;
            this.atMs = atMs;
        }
    }

    static LocalTime parse(String s) {
        if (s == null) return null;
        Matcher m = TIME.matcher(s);
        if (!m.find()) return null;
        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        if (h > 23 || min > 59) return null;
        return LocalTime.of(h, min);
    }

    /** A time that reads earlier than {@code after} but fits 12 hours later (a 12-hour "1:30" for 13:30). */
    static LocalTime notBefore(LocalTime t, LocalTime after, int withinHours) {
        if (t == null || after == null || !t.isBefore(after)) return t;
        if (t.getHour() >= 12) return null;
        LocalTime pm = t.plusHours(12);
        if (pm.isBefore(after)) return null;
        // Minutes of the day, not LocalTime.plusHours, which wraps past midnight.
        int gap = (pm.toSecondOfDay() - after.toSecondOfDay()) / 60;
        return gap > withinHours * 60 ? null : pm;
    }

    /** The soonest start or jamaat after {@code now}, over the given days; null when none is left. */
    static Event next(List<Day> days, String jumahTime, boolean twelveHour, ZonedDateTime now) {
        ZoneId zone = now.getZone();
        DateTimeFormatter f = DateTimeFormatter.ofPattern(twelveHour ? "h:mm" : "HH:mm", Locale.UK);
        Event best = null;
        for (Day d : days) {
            boolean friday = d.date.getDayOfWeek() == DayOfWeek.FRIDAY;
            LocalTime prev = null;
            for (int i = 0; i < NAMES.length; i++) {
                LocalTime start = parse(d.start[i]);
                if (i > 0) start = notBefore(start, prev, 14);
                if (start == null) continue;
                prev = start;
                String name = NAMES[i];
                LocalTime jam = parse(d.jamaat[i]);
                if (i == 1 && friday) {
                    name = "Jumu'ah";
                    LocalTime j = parse(jumahTime);
                    if (j != null) jam = j;
                }
                jam = notBefore(jam, start, 3);
                if (jam != null && !jam.isAfter(start)) jam = null;

                String times = name + " " + start.format(f) + (jam == null ? "" : "  ·  Jamaat " + jam.format(f));
                ZonedDateTime s = ZonedDateTime.of(d.date, start, zone);
                if (s.isAfter(now)) best = sooner(best, new Event("Next prayer  ·  " + times, name, s.toInstant().toEpochMilli()));
                if (jam != null) {
                    ZonedDateTime j = ZonedDateTime.of(d.date, jam, zone);
                    if (j.isAfter(now)) best = sooner(best, new Event(name + " has started  ·  Jamaat " + jam.format(f),
                            name + " jamaat", j.toInstant().toEpochMilli()));
                }
            }
        }
        return best;
    }

    private static Event sooner(Event a, Event b) {
        return a == null || b.atMs < a.atMs ? b : a;
    }

    /** Today and the next two days, in the order the timetable can answer them. */
    static List<LocalDate> window(LocalDate today) {
        List<LocalDate> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) out.add(today.plusDays(i));
        return out;
    }
}
