package io.github.justinnewman.gameday;

import java.util.Locale;

/**
 * The sports a calendar can be set to. The sport only decides the word in front of the countdown ("Puck drop in
 * 2h 18m"); nothing here knows leagues or teams. Alphabetical, with Other last, as the picker shows them.
 */
enum Sport {
    AMERICAN_FOOTBALL("American football", "Kickoff", true, "🏈"),
    AUSTRALIAN_RULES("Australian rules football", "First bounce", true),
    BADMINTON("Badminton", "First serve", false, "🏸"),
    BASEBALL("Baseball", "First pitch", true, "⚾"),
    BASKETBALL("Basketball", "Tip-off", true, "🏀"),
    BOXING("Boxing", "Opening bell", false, "🥊"),
    CRICKET("Cricket", "First ball", true, "🏏"),
    CURLING("Curling", "First stone", true, "🥌"),
    CYCLING("Cycling", "Starts", false, "🚴"),
    DARTS("Darts", "Game on", false, "🎯"),
    ESPORTS("Esports", "Starts", true, "🎮"),
    FIELD_HOCKEY("Field hockey", "Push-back", true, "🏑"),
    GAELIC_FOOTBALL("Gaelic football", "Throw-in", true),
    GOLF("Golf", "Tee time", false, "⛳", "🏌"),
    HANDBALL("Handball", "Throw-off", true),
    HOCKEY("Hockey", "Puck drop", true, "🏒"),
    HORSE_RACING("Horse racing", "Post time", false, "🏇"),
    HURLING("Hurling", "Throw-in", true),
    LACROSSE("Lacrosse", "Face-off", true, "🥍"),
    MMA("MMA", "Opening bell", false),
    MOTOR_FLAG("Motor racing, flag start", "Green flag", false),
    MOTOR_LIGHTS("Motor racing, light start", "Lights out", false),
    PICKLEBALL("Pickleball", "First serve", false),
    PRO_WRESTLING("Pro wrestling", "Bell time", false, "🤼"),
    ROWING("Rowing", "Start gun", false, "🚣"),
    RUGBY("Rugby", "Kickoff", true, "🏉"),
    SAILING("Sailing", "Start gun", false, "⛵"),
    SOCCER("Soccer", "Kickoff", true, "⚽"),
    SOFTBALL("Softball", "First pitch", true, "🥎"),
    TABLE_TENNIS("Table tennis", "First serve", false, "🏓"),
    TENNIS("Tennis", "First serve", false, "🎾"),
    VOLLEYBALL("Volleyball", "First serve", true, "🏐"),
    OTHER("Other", "Starts", true);

    private final String label;
    private final String word;
    private final boolean team;
    private final String[] emoji;

    Sport(String label, String word, boolean team, String... emoji) {
        this.label = label;
        this.word = word;
        this.team = team;
        this.emoji = emoji;
    }

    String label() {
        return label;
    }

    /** The word in front of the countdown for the main event: "Puck drop", "Lights out". */
    String word() {
        return word;
    }

    /** Team sports name two sides in a game's title; an entry that does not is something else (a draft). */
    boolean team() {
        return team;
    }

    static Sport byLabel(String label) {
        if (label == null) return null;
        String wanted = label.trim().toLowerCase(Locale.ROOT);
        for (Sport s : values()) {
            if (s.label.toLowerCase(Locale.ROOT).equals(wanted)) return s;
        }
        return null;
    }

    /** The sport a title's leading emoji gives away (Stanza writes "🏒 Away @ Home"), or null. */
    static Sport fromEmoji(String title) {
        if (title == null) return null;
        String t = title.trim();
        for (Sport s : values()) {
            for (String e : s.emoji) {
                if (t.startsWith(e)) return s;
            }
        }
        return null;
    }

    /** Every label, in picker order, for error messages: "American football, Australian rules football, ...". */
    static String labels() {
        StringBuilder b = new StringBuilder();
        for (Sport s : values()) {
            if (b.length() > 0) b.append(", ");
            b.append(s.label);
        }
        return b.toString();
    }
}
