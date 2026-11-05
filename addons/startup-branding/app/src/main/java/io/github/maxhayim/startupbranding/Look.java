package io.github.maxhayim.startupbranding;

/**
 * What the welcome screen looks like: the settings the owner chose, with their defaults and limits. Plain Java, so
 * the rules are tested on the computer; {@link Store} is where they are kept.
 */
final class Look {
    /** Off: nothing opens. Custom: background, picture and text. Black: a plain black screen. */
    enum Mode { OFF, CUSTOM, BLACK }

    /** How the picture fills the 16:9 frame. */
    enum Scale { FIT, FILL, STRETCH, ORIGINAL }

    /** Where the picture sits when it doesn't fill the frame. */
    enum Align { CENTER, TOP, BOTTOM, LEFT, RIGHT }

    /** Where the welcome text sits. */
    enum Place { BOTTOM, CENTER, TOP }

    interface Store {
        String get(String key);

        void put(String key, String value);
    }

    static final int MAX_TEXT = 60;
    static final int MAX_NICKNAME = 32;
    /** Seconds the screen stays up; 0 is until Back or OK. */
    static final int[] DURATIONS = {3, 5, 10, 15, 30, 0};
    static final String[] COLOR_NAMES = {"Black", "Charcoal", "Midnight", "Forest", "Wine", "White"};
    static final int[] COLORS = {0xFF000000, 0xFF1C1C1E, 0xFF0B1A33, 0xFF0E2A1F, 0xFF2A0E16, 0xFFFFFFFF};

    Mode mode = Mode.CUSTOM;
    /** A file name in the app's pictures folder, or "" for none. */
    String image = "";
    Scale scale = Scale.FIT;
    Align align = Align.CENTER;
    String text = "Welcome";
    Place place = Place.BOTTOM;
    int color = 0;
    int seconds = 5;
    String nickname = "";

    static Look load(Store s) {
        Look l = new Look();
        l.mode = pick(Mode.values(), s.get("mode"), l.mode);
        l.image = fileName(s.get("image"));
        l.scale = pick(Scale.values(), s.get("scale"), l.scale);
        l.align = pick(Align.values(), s.get("align"), l.align);
        String t = s.get("text");
        if (t != null) l.text = clean(t, MAX_TEXT);
        l.place = pick(Place.values(), s.get("place"), l.place);
        l.color = index(s.get("color"), COLORS.length, l.color);
        int sec = number(s.get("seconds"), l.seconds);
        for (int d : DURATIONS) if (d == sec) l.seconds = sec;
        String n = s.get("nickname");
        if (n != null) l.nickname = clean(n, MAX_NICKNAME);
        return l;
    }

    void save(Store s) {
        s.put("mode", mode.name());
        s.put("image", image);
        s.put("scale", scale.name());
        s.put("align", align.name());
        s.put("text", text);
        s.put("place", place.name());
        s.put("color", Integer.toString(color));
        s.put("seconds", Integer.toString(seconds));
        s.put("nickname", nickname);
    }

    int background() {
        return COLORS[color];
    }

    /** White text on dark backgrounds, near-black on light ones. */
    int textColor() {
        int c = background();
        double lum = 0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF);
        return lum > 140 ? 0xFF111111 : 0xFFFFFFFF;
    }

    static int nextDuration(int seconds) {
        for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == seconds) return DURATIONS[(i + 1) % DURATIONS.length];
        return DURATIONS[0];
    }

    static String durationName(int seconds) {
        return seconds == 0 ? "Until Back or OK" : seconds + " s";
    }

    static <E extends Enum<E>> E next(E[] all, E e) {
        return all[(e.ordinal() + 1) % all.length];
    }

    /** One line of printable text, trimmed and cut to {@code max} characters (never inside a surrogate pair). */
    static String clean(String s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        s.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || cp == '\t') b.append(' ');
            else if (!Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT) b.appendCodePoint(cp);
        });
        String t = b.toString().trim().replaceAll(" {2,}", " ");
        if (t.codePointCount(0, t.length()) <= max) return t;
        return t.substring(0, t.offsetByCodePoints(0, max)).trim();
    }

    /** A bare file name (no folders, no hidden files), or "" when it isn't one. */
    static String fileName(String s) {
        if (s == null || s.isEmpty() || s.startsWith(".") || s.contains("/") || s.contains("\\") || s.length() > 100) return "";
        return s;
    }

    private static <E extends Enum<E>> E pick(E[] all, String name, E fallback) {
        if (name != null) for (E e : all) if (e.name().equals(name)) return e;
        return fallback;
    }

    private static int number(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int index(String s, int size, int fallback) {
        int i = number(s, fallback);
        return i >= 0 && i < size ? i : fallback;
    }
}
