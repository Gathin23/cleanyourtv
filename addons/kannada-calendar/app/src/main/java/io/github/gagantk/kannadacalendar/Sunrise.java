package io.github.gagantk.kannadacalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tithi or nakshatra in force at sunrise, which is the one a printed panchanga names for the day. The publisher
 * lists, under each date, the ones that end during it ("Ekadashi Upto 2:10 AM" on 21 November 2026, the morning
 * Utthana Dwadashi is kept), so the day's own is the first of those, on that date or the next ones, that ends
 * after sunrise in Bengaluru.
 */
final class Sunrise {
    /** A tithi or nakshatra: its name, when it ends ("till 1:33 AM"), and a word on what follows or when. */
    static final class Span {
        final String name, till, after;

        Span(String name, String till, String after) {
            this.name = name;
            this.till = till;
            this.after = after;
        }
    }

    private static final Pattern TIME = Pattern.compile(
            "(?i)^till" + Day.SPACE + "+(\\d{1,2})[:.](\\d{2})" + Day.SPACE + "*([ap])\\.?" + Day.SPACE + "*m\\.?$");
    private static final Pattern WHOLE = Pattern.compile("(?i)^till" + Day.SPACE + "+whole" + Day.SPACE + "+day$");

    private Sunrise() {}

    /**
     * Sunrise in Bengaluru, to within a few minutes: about 6:20, earliest (5:53) in early June and latest
     * (6:47) in mid-January. Close enough to tell a tithi that ended in the night from one that runs on.
     */
    static LocalTime at(LocalDate date) {
        double minutes = 380 + 27 * Math.cos(2 * Math.PI * (date.getDayOfYear() - 15) / 365.25);
        return LocalTime.of(0, 0).plusMinutes(Math.round(minutes));
    }

    static Span tithi(Book book, LocalDate date) { return of(book, date, true); }

    static Span nakshatra(Book book, LocalDate date) { return of(book, date, false); }

    private static Span of(Book book, LocalDate date, boolean tithi) {
        String own = value(book.day(date), tithi);
        LocalDateTime sunrise = date.atTime(at(date));
        for (int k = 0; k < 3; k++) {
            LocalDate on = date.plusDays(k);
            List<String> items = items(value(book.day(on), tithi));
            for (int i = 0; i < items.size(); i++) {
                String[] parts = Day.until(items.get(i));
                if (WHOLE.matcher(parts[1]).matches()) continue;
                LocalTime end = time(parts[1]);
                if (end == null) return fallback(own);
                if (!on.atTime(end).isAfter(sunrise)) continue;
                String after;
                if (k == 0) {
                    String next = i + 1 < items.size() ? items.get(i + 1) : first(value(book.day(on.plusDays(1)), tithi));
                    after = next.isEmpty() ? "" : "then " + Day.until(next)[0];
                } else {
                    after = k == 1 ? "next day" : "on " + on.getDayOfMonth() + " "
                            + on.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
                }
                return new Span(parts[0], parts[1], after);
            }
        }
        return fallback(own);
    }

    /** Old or unreadable times: the first listed one, as published. */
    private static Span fallback(String value) {
        List<String> items = items(value);
        if (items.isEmpty()) return new Span("", "", "");
        String[] first = Day.until(items.get(0));
        String after = items.size() > 1 ? "then " + Day.until(items.get(1))[0] : "";
        return new Span(first[0], first[1], after);
    }

    private static String value(Day day, boolean tithi) { return tithi ? day.tithi : day.nakshatra; }

    private static String first(String value) {
        List<String> items = items(value);
        return items.isEmpty() ? "" : items.get(0);
    }

    private static List<String> items(String value) {
        List<String> out = new ArrayList<>();
        for (String s : value.split(",\\s*")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    /** "till 2:10 AM" as a clock time, or null when it is written some other way. */
    static LocalTime time(String till) {
        Matcher m = TIME.matcher(till.trim());
        if (!m.matches()) return null;
        int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
        if (h < 1 || h > 12 || min > 59) return null;
        boolean pm = m.group(3).equalsIgnoreCase("p");
        return LocalTime.of(h % 12 + (pm ? 12 : 0), min);
    }
}
