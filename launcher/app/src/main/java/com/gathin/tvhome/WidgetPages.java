package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The home widget's sources (config.xml widget_sources, changed on the TV in Launcher settings): which ones show, in
 * which order, and the one-line text of each source the launcher reads itself. Add-on sources are "app:" plus the
 * package of a live module card (docs/specs/widget-source.md). No Android types, so it runs as a plain unit test.
 */
final class WidgetPages {
    static final String ADB = "adb", MEMORY = "memory";
    static final String APP = "app:";
    /** The launcher's own sources, in the order the settings list shows the ones that are off. */
    static final List<String> BUILT_IN = Arrays.asList(ADB, MEMORY);
    static final String PREF_SOURCES = "widget_sources";
    static final String PREF_SECONDS = "widget_seconds";
    static final int[] SECONDS = {6, 10, 15, 30};
    static final long LOW_RAM_BYTES = 150L * 1024 * 1024;
    static final long LOW_STORAGE_BYTES = 1024L * 1024 * 1024;
    /** Longest text an add-on may send per field; a row with only a caption shows it cut to MAX_CAPTION. */
    static final int MAX_LINE = 80, MAX_KICKER = 32, MAX_META = 60, MAX_CAPTION = 32;

    private WidgetPages() {
    }

    /** One page: a small heading, the main line, a detail line; alert turns it red, urgent holds it on screen. */
    static final class Item {
        final String source, kicker, line, meta, kind;
        final boolean alert, urgent;

        Item(String source, String kicker, String line, String meta, boolean alert) {
            this(source, kicker, line, meta, alert, false, source);
        }

        Item(String source, String kicker, String line, String meta, boolean alert, boolean urgent, String kind) {
            this.source = source;
            this.kicker = kicker;
            this.line = line;
            this.meta = meta;
            this.alert = alert;
            this.urgent = urgent;
            this.kind = kind;
        }
    }

    static boolean isSource(String s) {
        return BUILT_IN.contains(s) || s.startsWith(APP) && Cards.isPackageName(s.substring(APP.length()));
    }

    static String app(String pkg) {
        return APP + pkg;
    }

    /** The saved comma list, or the config.xml defaults when nothing is saved; unknown and repeated names dropped. */
    static List<String> enabled(String saved, String[] defaults) {
        List<String> out = new ArrayList<>();
        String[] names = saved != null ? saved.split(",") : defaults;
        for (String n : names) {
            String s = n.trim();
            if (!s.startsWith(APP)) s = s.toLowerCase(Locale.ROOT);
            if (isSource(s) && !out.contains(s)) out.add(s);
        }
        return out;
    }

    static String save(List<String> sources) {
        return String.join(",", sources);
    }

    /** The settings list: the enabled sources in their order, then the other built-in and add-on sources. */
    static List<String> listed(List<String> enabled, List<String> apps) {
        List<String> out = new ArrayList<>(enabled);
        for (String s : BUILT_IN) if (!out.contains(s)) out.add(s);
        for (String pkg : apps) if (!out.contains(app(pkg))) out.add(app(pkg));
        return out;
    }

    /** On becomes off; off becomes on at the end of the order. */
    static List<String> toggle(List<String> enabled, String source) {
        List<String> out = new ArrayList<>(enabled);
        if (!out.remove(source) && isSource(source)) out.add(source);
        return out;
    }

    static List<String> moveFirst(List<String> enabled, String source) {
        List<String> out = new ArrayList<>(enabled);
        if (out.remove(source)) out.add(0, source);
        return out;
    }

    static int seconds(int saved) {
        for (int s : SECONDS) if (s == saved) return s;
        return SECONDS[0];
    }

    static int nextSeconds(int current) {
        for (int i = 0; i < SECONDS.length; i++) if (SECONDS[i] == current) return SECONDS[(i + 1) % SECONDS.length];
        return SECONDS[0];
    }

    /** The page after {@code current} among {@code count} pages; with none, -1. */
    static int next(int current, int count) {
        if (count <= 0) return -1;
        return current < 0 || current + 1 >= count ? 0 : current + 1;
    }

    /** The page texts the launcher shows itself, from strings.xml (MainActivity.widgetText). */
    static final class Text {
        final String security, debuggingOn, debuggingHint, debuggingWireless, memory, ram, storage, low;

        Text(String security, String debuggingOn, String debuggingHint, String debuggingWireless, String memory,
             String ram, String storage, String low) {
            this.security = security;
            this.debuggingOn = debuggingOn;
            this.debuggingHint = debuggingHint;
            this.debuggingWireless = debuggingWireless;
            this.memory = memory;
            this.ram = ram;
            this.storage = storage;
            this.low = low;
        }
    }

    /** Only while debugging is on: then a computer on this network may control the TV. */
    static Item adb(boolean debugging, boolean wirelessDebugging, Text t) {
        if (!debugging && !wirelessDebugging) return null;
        return new Item(ADB, t.security, t.debuggingOn, wirelessDebugging ? t.debuggingWireless : t.debuggingHint, true);
    }

    /** In use of the total, as "RAM 1.3 / 2.0 GB used"; red when little is left. */
    static Item memory(long freeRam, long totalRam, long freeStorage, long totalStorage, Text t) {
        boolean low = freeRam < LOW_RAM_BYTES || freeStorage < LOW_STORAGE_BYTES;
        String storage = String.format(t.storage, used(totalStorage - freeStorage, totalStorage));
        return new Item(MEMORY, t.memory, String.format(t.ram, used(totalRam - freeRam, totalRam)),
                low ? storage + " · " + t.low : storage, low);
    }

    /**
     * An add-on's row (docs/specs/widget-source.md) as a page, or null when it has nothing to show. The text is
     * cleaned and cut to the limits; {@code fallbackKicker} is the card's label.
     */
    static Item fromRow(String pkg, String caption, String line, String kicker, String meta, boolean alert,
                        boolean urgent, String kind, String fallbackKicker) {
        String main = clean(line, MAX_LINE);
        if (main.isEmpty()) main = clean(caption, MAX_CAPTION);
        if (main.isEmpty()) return null;
        String k = clean(kicker, MAX_KICKER);
        return new Item(app(pkg), k.isEmpty() ? clean(fallbackKicker, MAX_KICKER).toUpperCase(Locale.ROOT) : k, main,
                clean(meta, MAX_META), alert, urgent, clean(kind, 16).toLowerCase(Locale.ROOT));
    }

    /** Control and formatting characters out, whitespace runs to one space, at most {@code max} characters. */
    static String clean(String s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < s.length() && b.length() < max; ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                space = b.length() > 0;
                continue;
            }
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.UNASSIGNED
                    || type == Character.PRIVATE_USE || type == Character.SURROGATE) continue;
            if (b.length() + (space ? 1 : 0) + Character.charCount(cp) > max) break;
            if (space) b.append(' ');
            space = false;
            b.appendCodePoint(cp);
        }
        return b.toString();
    }

    /** "1.3 / 2.0 GB" in GB with one decimal; MB below 1 GB total. */
    static String used(long used, long total) {
        used = Math.max(0, Math.min(used, total));
        if (total >= 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f / %.1f GB", used / (1024.0 * 1024 * 1024), total / (1024.0 * 1024 * 1024));
        }
        return used / (1024 * 1024) + " / " + total / (1024 * 1024) + " MB";
    }
}
