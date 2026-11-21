package io.github.gagantk.kannadacalendar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sorts a festival line into the kinds the side panel colours, and writes out the publisher's
 * abbreviations ("Kudligi CF" is the car festival at Kudligi, "Hubli Swamy Ara." an aradhana). The kind comes
 * from the words in the name alone, so a line nobody planned for still shows, as "Other".
 */
final class Festival {
    enum Kind { FESTIVAL, TEMPLE, SAINT, VRATA, DAY, OTHER }

    /** The main festivals and national days, kept across Karnataka; a day's listed holidays count too. */
    private static final Pattern MAJOR = Pattern.compile(
            "(?i)\\b(ugadi|makara sankranti|deepavali|diwali|naraka chaturdashi|bali ?pa+dyami|dhana trayodashi|"
                    + "ganesh\\w* chaturthi|swarna ?gowri|gowri habba|vara ?mahalakshmi|naga(ra)? panchami|"
                    + "raksha bandhan|janmashtami|gokulashtami|rama ?navami|ramanavami|maha ?shivaratri|holi|"
                    + "mahanavami|ayudha pooja|vijayadashami|dasara|sharan navaratri|mahalaya amavasya|"
                    + "vaikunta ekadashi|akshaya tritiya|utthana dwadashi|hanuman jayanti|basava jayanti|"
                    + "mahaveer jayanti|mahavir jayanti|buddha poornima|buddha purnima|kanakadasa jayanti|"
                    + "valmiki jayanti|christmas|good friday|ramzan|id-milad|bakrid|muharram|kannada rajyotsava|"
                    + "independence day|republic day|gandhi jayant\\w*|ambedkar\\W*s birthday|may day)\\b");

    private static final Pattern TEMPLE = Pattern.compile(
            "(?i)\\b(car festival|rath|ratha|rathotsava|jatre|jathre|utsav|utsava|deepotsav|deepotsava|teppotsav|"
                    + "lakshadeep|brahmotsav|dhwaja|kalyanotsav|habba|mahotsav|darshan|abhishek)\\w*\\b");
    private static final Pattern SAINT = Pattern.compile(
            "(?i)\\b(aradhana|jayanti|jayanthi|punyatithi|punyadina|punyaradhane|birthday|samadhi|tirtha|swamy|swami|"
                    + "maharshi|acharya|guru)\\b");
    private static final Pattern VRATA = Pattern.compile(
            "(?i)\\b(ekadashi|ekadasi|vrata|vratha|pradosha|chaturthi|dwadashi|purnima|poornima|amavasya|sankramana|"
                    + "sankranti|navaratri|shivaratri|deepavali|diwali|ugadi|ashtami|navami|shashti|manvadi|yugadi|"
                    + "punyakala|pooja|puja|homa|snana|anadhyayana|parva|vratharamba|tarpana)\\b");
    private static final Pattern DAY = Pattern.compile("(?i)\\b(day|week|year)\\b");

    /** The publisher's short forms and what they stand for, compiled once: this runs for every line on every key. */
    private static final Pattern[] SHORT = {
            Pattern.compile("\\bCF\\b"), Pattern.compile("(?i)\\bAra\\.?$"), Pattern.compile("(?i)\\bvr\\.$"),
            Pattern.compile("(?i)\\bIntl\\."), Pattern.compile("(?i)\\bCom\\. of\\b")};
    private static final String[] LONG = {"Car Festival", "Aradhana", "vrata", "International", "Commencement of"};

    private Festival() {}

    /** The line with the publisher's short forms written out. */
    static String expand(String name) {
        for (int i = 0; i < SHORT.length; i++) name = SHORT[i].matcher(name).replaceAll(LONG[i]);
        return name.trim();
    }

    static List<String> shown(List<String> names) {
        List<String> out = new ArrayList<>(names.size());
        for (String n : names) out.add(expand(n));
        return out;
    }

    /** The kind of one of a day's lines: one of the day's listed holidays is a festival whatever its words. */
    static Kind kind(String name, Day day) {
        String n = expand(name);
        if (day != null) {
            for (String h : day.publicHolidays) if (expand(h).equalsIgnoreCase(n)) return Kind.FESTIVAL;
            for (String h : day.restrictedHolidays) if (expand(h).equalsIgnoreCase(n)) return Kind.FESTIVAL;
        }
        return kind(name);
    }

    /**
     * A day's lines in the order they matter, written out: its public holidays first (the number of them is
     * {@code holidays} of the result), then the main festivals, then the rest as published.
     */
    static Ranked ranked(Day day) {
        List<String> holidays = shown(day.publicHolidays), main = new ArrayList<>(), rest = new ArrayList<>();
        for (String f : day.festivals) {
            String n = expand(f);
            if (containsIgnoreCase(holidays, n) || containsIgnoreCase(main, n) || containsIgnoreCase(rest, n)) continue;
            (kind(f, day) == Kind.FESTIVAL ? main : rest).add(n);
        }
        List<String> all = new ArrayList<>(holidays);
        all.addAll(main);
        all.addAll(rest);
        return new Ranked(all, holidays.size());
    }

    static final class Ranked {
        final List<String> names;
        final int holidays;

        Ranked(List<String> names, int holidays) {
            this.names = names;
            this.holidays = holidays;
        }
    }

    private static boolean containsIgnoreCase(List<String> list, String s) {
        for (String x : list) if (x.equalsIgnoreCase(s)) return true;
        return false;
    }

    static Kind kind(String name) {
        String n = expand(name);
        if (MAJOR.matcher(n).find()) return Kind.FESTIVAL;
        if (TEMPLE.matcher(n).find()) return Kind.TEMPLE;
        if (SAINT.matcher(n).find()) return Kind.SAINT;
        if (VRATA.matcher(n).find()) return Kind.VRATA;
        if (DAY.matcher(n).find() && !n.toLowerCase(Locale.ROOT).contains("saturday")) return Kind.DAY;
        return Kind.OTHER;
    }
}
