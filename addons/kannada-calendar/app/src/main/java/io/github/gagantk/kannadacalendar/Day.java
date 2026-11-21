package io.github.gagantk.kannadacalendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One day of the calendar, sorted out of the free text its events carry. The publisher wrote the same
 * facts in a few shapes over the years ("Hemanta Ritu, Pushya Maasa, Shukla Paksha" with "Dashami Upto
 * 9:24 PM" and "Rahukalam 12:20 PM-1:45 PM" on later lines; "Ashtami tithi Upto 7.53 a.m." as its own
 * event; "Dwadasi tithi ends" as a timed one), so each line is recognised by what it says and anything
 * that is none of those is a festival or observance, shown exactly as published.
 */
final class Day {
    private static final Pattern HEADER = Pattern.compile(
            "(?i)^((?:\\w+\\s+)r?i?tu\\b[, ]*(?:\\w+\\s+){1,2}ma(?:a)?sa\\b[, ]*(?:shukla|krishna)\\s+paksha)\\b[, ]*(.*)$");
    /**
     * Any space, the publisher's too: some times are typeset with a four-per-em space (U+2005), which \s does
     * not match, and Android's regex engine has no UNICODE_CHARACTER_CLASS flag to make it.
     */
    static final String SPACE = "[\\s\\u00a0\\u2000-\\u200b\\u202f]";
    private static final Pattern UPTO = Pattern.compile(
            "(?i)^(.*?)" + SPACE + "+(?:tithi" + SPACE + "+|star" + SPACE + "+)?(?:upto|nxt\\.?" + SPACE + "*day)\\b"
                    + SPACE + "*(.*)$");
    private static final Pattern ENDS = Pattern.compile("(?i)^(.*?)\\s+(tithi|star|nakshatra)\\s+ends$");
    private static final Pattern RAHU = Pattern.compile("(?i)^rahu\\s*kala(?:m)?\\b[\\s:]*(.*)$");
    /** Starts of the tithi names, spelled as the publisher spells them. */
    private static final String[] TITHI_STARTS = {"prati", "dwit", "dvit", "trit", "chatur", "pancha", "shash",
            "shast", "sapta", "ashta", "nava", "dasha", "ekad", "dwad", "dvad", "trayo", "poorn", "purn", "amav"};

    final LocalDate date;
    String header = "", tithi = "", nakshatra = "", rahu = "";
    final List<String> festivals = new ArrayList<>();
    /** The day's holidays from the publisher's lists, by name: public ones are shown like a Sunday. */
    final List<String> publicHolidays = new ArrayList<>(), restrictedHolidays = new ArrayList<>();

    private Day(LocalDate date) { this.date = date; }

    static Day of(LocalDate date, List<Entry> entries) {
        Day day = new Day(date);
        for (Entry e : entries) {
            if (!e.holiday.isEmpty() && !e.summary.isEmpty()) {
                String name = e.summary.split("\n")[0].trim();
                if (e.holiday.equals("public")) day.publicHolidays.add(name);
                else day.restrictedHolidays.add(name);
            }
            if (!e.time.isEmpty()) {
                day.timed(e);
                continue;
            }
            for (String text : new String[]{e.summary, e.description}) {
                for (String line : text.split("\n")) day.line(line.trim());
            }
        }
        return day;
    }

    /** True when the day has nothing at all to show. */
    boolean isEmpty() {
        return header.isEmpty() && tithi.isEmpty() && nakshatra.isEmpty() && rahu.isEmpty() && festivals.isEmpty();
    }

    private void timed(Entry e) {
        String text = e.summary.trim();
        Matcher ends = ENDS.matcher(text);
        if (ends.matches()) {
            String what = ends.group(1) + " ends " + e.time;
            if (ends.group(2).equalsIgnoreCase("tithi")) tithi = join(tithi, what);
            else nakshatra = join(nakshatra, what);
        } else if (RAHU.matcher(text).matches()) {
            rahu = "from " + e.time;
        } else if (!text.isEmpty()) {
            festivals.add(text + " " + e.time);
        }
    }

    private void line(String line) {
        if (line.isEmpty()) return;
        Matcher header = HEADER.matcher(line);
        if (header.matches()) {
            if (this.header.isEmpty()) this.header = header.group(1).replaceAll("\\s*,\\s*", " · ").replaceAll("\\s+", " ");
            line = header.group(2).trim();
            if (line.isEmpty()) return;
        }
        Matcher rahu = RAHU.matcher(line);
        if (rahu.matches()) {
            if (this.rahu.isEmpty()) this.rahu = rahu.group(1).trim();
            return;
        }
        Matcher upto = UPTO.matcher(line);
        if (upto.matches()) {
            String shown = line.replaceAll("(?i)\\bupto\\b", "till").replaceAll("(?i)\\b(tithi|star)\\s+", "");
            if (isTithi(upto.group(1))) tithi = join(tithi, shown);
            else nakshatra = join(nakshatra, shown);
            return;
        }
        for (String part : line.split(",\\s*")) {
            String item = part.trim();
            if (!item.isEmpty()) festivals.add(item);
        }
    }

    private static final Pattern UNTIL = Pattern.compile("(?i)^(.*?)" + SPACE + "+(till|ends)" + SPACE + "+(.*)$");

    /** A tithi or nakshatra and the time it holds until: {"Ekadashi", "till 2:10 AM"}. */
    static String[] until(String value) {
        Matcher m = UNTIL.matcher(value.trim());
        if (!m.matches()) return new String[]{value.trim(), ""};
        return new String[]{m.group(1).trim(), "till " + m.group(3).trim()};
    }

    static boolean isTithi(String name) {
        String n = name.trim().toLowerCase(java.util.Locale.ROOT);
        for (String start : TITHI_STARTS) if (n.startsWith(start)) return true;
        return false;
    }

    private static String join(String a, String b) { return a.isEmpty() ? b : a + ", " + b; }
}
