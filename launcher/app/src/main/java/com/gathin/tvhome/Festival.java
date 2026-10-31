package com.gathin.tvhome;

import android.content.res.Resources;
import android.os.LocaleList;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A festive skin set by a theme's config.json: {@code festival} (or {@code seasonal_skin}), {@code festival_until},
 * {@code greeting} and {@code accent_color}. While it is active the pills get festive icons (a diya for the
 * network, a bell for Inputs, a marigold for Settings; for {@code halloween} a jack-o'-lantern, a bat and a
 * ghost), the greeting is replaced and the focus glow takes the accent colour. It switches itself off after
 * {@code festival_until}, so nobody has to remember to undo it.
 */
final class Festival {

    static final Festival NONE = new Festival("", null, "", 0);

    /** Warm diya gold, the accent when a theme names a festival but no colour. */
    static final int DEFAULT_ACCENT = 0xFFFFB23C;

    /** The festival with its own skin (jack-o'-lantern, bat, ghost, bats on the first Home) instead of the diya's. */
    static final String HALLOWEEN = "halloween";

    /** Pumpkin orange, Halloween's accent when the theme names no colour. */
    static final int PUMPKIN = 0xFFFF7A1A;

    /** The festival with its own skin (tree, bell, Santa, snow on the first Home) instead of the diya's. */
    static final String CHRISTMAS = "christmas";

    /** Christmas red, that skin's accent when the theme names no colour. */
    static final int CHRISTMAS_RED = 0xFFE0262F;

    /** Lower-case festival name, or "" for none. */
    final String name;
    /** Last day the skin shows, inclusive; null for no end date. */
    final LocalDate until;
    /** Text that replaces the time-of-day greeting; "" uses the festival's own default. */
    final String greeting;
    /** Focus glow colour as 0xAARRGGBB, or 0 for {@link #DEFAULT_ACCENT}. */
    final int accent;

    private Festival(String name, LocalDate until, String greeting, int accent) {
        this.name = name;
        this.until = until;
        this.greeting = greeting;
        this.accent = accent;
    }

    /**
     * Builds the skin from raw config values. Anything malformed turns the skin off rather than leaving a
     * festival on the screen forever: an unreadable end date or an empty name gives {@link #NONE}.
     */
    static Festival parse(String name, String until, String greeting, String accent) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty() || n.length() > 40) return NONE;
        LocalDate end = null;
        if (until != null && !until.trim().isEmpty()) {
            try {
                end = LocalDate.parse(until.trim());
            } catch (DateTimeParseException e) {
                return NONE;
            }
        }
        String g = greeting == null ? "" : Cards.cleanLabel(greeting, "");
        return new Festival(n, end, g, parseColor(accent));
    }

    /** Whether the skin shows today. */
    boolean active(LocalDate today) {
        return !name.isEmpty() && (until == null || !today.isAfter(until));
    }

    /** The greeting to show while active, or "" to keep the normal time-of-day greeting. */
    String greetingText(Resources res, LocalDate today) {
        if (!greeting.isEmpty()) return greeting;
        switch (name) {
            case "dussehra":
                return res.getString(R.string.festival_dussehra_greeting);
            case "diwali":
                return res.getString(R.string.festival_diwali_greeting);
            case HALLOWEEN:
                return res.getString(R.string.festival_halloween_greeting);
            case CHRISTMAS:
                return pick(res.getString(R.string.christmas_greetings), christmasSlot(today), locales(res));
            default:
                return "";
        }
    }

    int accentColor() {
        if (accent != 0) return accent;
        return isHalloween() ? PUMPKIN : isChristmas() ? CHRISTMAS_RED : DEFAULT_ACCENT;
    }

    /**
     * Which of the Christmas greetings a day gets: 0 Thanksgiving (4th Thursday of November), 1 8 Dec, 2 25 Dec,
     * 3 26 Dec, 4 31 Dec, 5 1 Jan, 6 6 Jan, or -1 for any other day, which keeps the time-of-day greeting.
     */
    static int christmasSlot(LocalDate d) {
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if (m == 11 && d.getDayOfWeek() == DayOfWeek.THURSDAY && (day - 1) / 7 == 3) return 0;
        if (m == 12) return day == 8 ? 1 : day == 25 ? 2 : day == 26 ? 3 : day == 31 ? 4 : -1;
        return m == 1 ? (day == 1 ? 5 : day == 6 ? 6 : -1) : -1;
    }

    /**
     * One greeting of a {@code |}-separated list, or "" when its slot is empty (a language with no real equivalent of
     * that day), or when none of the device's locales is the language the list is written in (its last item): a
     * language with no translation shows the English default, which keeps its time-of-day greeting instead. The
     * locales are tried in order, as Android picks resources, so a TV set to [ru, it] still gets the Italian list.
     * Thanksgiving (slot 0) only goes to an en-US locale.
     */
    static String pick(String packed, int slot, List<Locale> locales) {
        String[] all = packed.split("\\|", -1);
        if (slot < 0 || slot >= all.length - 1) return "";
        String of = all[all.length - 1];
        for (Locale l : locales) {
            if (!legacy(l.getLanguage()).equals(of)) continue;
            return slot == 0 && !"US".equals(l.getCountry()) ? "" : all[slot];
        }
        return "";
    }

    private static List<Locale> locales(Resources res) {
        LocaleList list = res.getConfiguration().getLocales();
        List<Locale> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) out.add(list.get(i));
        return out;
    }

    /** Android's Locale still names Hebrew and Indonesian by their old codes, as the greeting lists do. */
    private static String legacy(String language) {
        return language.equals("he") ? "iw" : language.equals("id") ? "in" : language;
    }

    /** Whether this is the Christmas skin rather than the diya, bell and marigold. */
    boolean isChristmas() {
        return CHRISTMAS.equals(name);
    }

    /** Whether this is the Halloween skin rather than the diya, bell and marigold. */
    boolean isHalloween() {
        return HALLOWEEN.equals(name);
    }

    /** {@code #RRGGBB} to an opaque colour, anything else to 0. */
    static int parseColor(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (!t.matches("#[0-9A-Fa-f]{6}")) return 0;
        return 0xFF000000 | Integer.parseInt(t.substring(1), 16);
    }
}
