package io.github.justinnewman.gameday;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What the panel says for a calendar entry: its top line ("Minnesota Wild at Dallas Stars", always away first) and
 * the word in front of the countdown. Calendars write games in a few ways: "Away @ Home" and "Away at Home" put the
 * away side first, "Home vs Away" and "Home v Away" the home side. Anything else is shown as its own title.
 */
final class Titles {
    private static final String STARTS = "Starts";
    /** Sessions before the main event: they count down to "Starts", the race itself to "Lights out". */
    private static final Pattern SESSION = Pattern.compile(
            "\\b(practice|fp1|fp2|fp3|qualifying|qualification|quali|shootout)\\b", Pattern.CASE_INSENSITIVE);

    private Titles() {
    }

    /** The title without its leading emoji, symbols and spaces; the rest is kept as written. */
    static String clean(String title) {
        if (title == null) return "";
        String t = title.trim();
        int i = 0;
        while (i < t.length()) {
            int cp = t.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) break;
            i += Character.charCount(cp);
        }
        return t.substring(i).trim();
    }

    /** {away, home} for a cleaned title that names a game, else null. A trailing "(...)" or " - ..." is ignored. */
    static String[] matchup(String cleaned) {
        if (cleaned == null) return null;
        return split(core(cleaned));
    }

    /** "Away at Home" plus any trailing decoration; the cleaned title when it is not a game; else the calendar's name. */
    static String topLine(String title, String calendarName) {
        String cleaned = clean(title);
        if (cleaned.isEmpty()) return calendarName == null ? "" : calendarName;
        String core = core(cleaned);
        String[] sides = split(core);
        if (sides == null) return cleaned;
        return sides[0] + " at " + sides[1] + cleaned.substring(core.length());
    }

    /**
     * The word in front of the countdown. A game gets the sport's word ("Puck drop"). In a team sport anything else
     * (a draft, a welcome note) just "Starts"; in the other sports the event itself gets the word, except practice,
     * qualifying and shootout sessions.
     */
    static String word(Sport sport, String title) {
        String cleaned = clean(title);
        if (matchup(cleaned) != null) return sport.word();
        if (sport.team()) return STARTS;
        if (SESSION.matcher(cleaned).find()) return STARTS;
        return sport.word();
    }

    /** The team named in the most games, to suggest a calendar's name (Stanza names them just "NHL"); null if none. */
    static String likelyTeam(List<String> titles) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String t : titles) {
            String[] sides = matchup(clean(t));
            if (sides == null) continue;
            for (String side : sides) counts.merge(side, 1, Integer::sum);
        }
        String best = null;
        int most = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > most) {
                best = e.getKey();
                most = e.getValue();
            }
        }
        return best;
    }

    /** The title without a trailing "(Preseason)" or " - NWSL", which stays at the end of the top line. */
    private static String core(String cleaned) {
        if (cleaned.endsWith(")")) {
            int open = cleaned.lastIndexOf(" (");
            if (open > 0) return cleaned.substring(0, open);
        }
        int dash = cleaned.lastIndexOf(" - ");
        return dash > 0 ? cleaned.substring(0, dash) : cleaned;
    }

    private static String[] split(String core) {
        String lower = core.toLowerCase(Locale.ROOT);
        String[] sides = sides(core, lower.indexOf(" @ "), 3);
        if (sides != null) return sides;
        // " at " only before a name: "Pat at the Ballpark Night" is not a game.
        for (int at = lower.indexOf(" at "); at >= 0; at = lower.indexOf(" at ", at + 1)) {
            int next = at + 4;
            if (next < core.length()) {
                int cp = core.codePointAt(next);
                if (Character.isUpperCase(cp) || Character.isDigit(cp)) {
                    sides = sides(core, at, 4);
                    if (sides != null) return sides;
                }
            }
        }
        for (String vs : new String[] {" vs. ", " vs ", " v. ", " v "}) {
            sides = sides(core, lower.indexOf(vs), vs.length());
            if (sides != null) return new String[] {sides[1], sides[0]};
        }
        return null;
    }

    private static String[] sides(String core, int at, int length) {
        if (at <= 0) return null;
        String left = core.substring(0, at).trim();
        String right = core.substring(at + length).trim();
        return left.isEmpty() || right.isEmpty() ? null : new String[] {left, right};
    }
}
