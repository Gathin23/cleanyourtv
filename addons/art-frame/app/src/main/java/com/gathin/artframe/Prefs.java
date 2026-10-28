package com.gathin.artframe;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** The look and schedule settings. Every one is a switch or a short list the Settings screen cycles through. */
final class Prefs {
    static final int[] SECONDS = {0, 60, 300, 900, 3600, 21600};
    static final String[] MATTE_COLOURS = Matte.COLOURS;
    static final String[] CAPTIONS = {"off", "change", "always"};
    static final int[] DIM_LEVELS = {30, 50, 70};
    /** off, "button" (only when the art button woke the TV; TCL), or "any" wake. */
    static final String[] WAKE = {"off", "button", "any"};

    private final SharedPreferences p;

    Prefs(Context c) {
        p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    int seconds() { return p.getInt("seconds", 300); }
    boolean shuffle() { return p.getBoolean("shuffle", true); }
    boolean matte() { return p.getBoolean("matte", true); }
    String matteColour() { return p.getString("matte_colour", "white"); }
    String matteSize() { return p.getString("matte_size", "medium"); }
    String matteTexture() { return p.getString("matte_texture", "paper"); }
    String depth() { return p.getString("depth", "subtle"); }
    /** The TV's system wallpaper follows the default piece (shown behind Home by a launcher that uses it). */
    boolean homeBackground() { return p.getBoolean("home_background", true); }
    String homeBackgroundKey() { return p.getString("home_bg_key", ""); }
    boolean border() { return p.getBoolean("border", true); }
    String caption() { return p.getString("caption", "change"); }
    boolean motion() { return p.getBoolean("motion", false); }
    boolean crossfade() { return p.getBoolean("crossfade", true); }
    boolean clock() { return p.getBoolean("clock", false); }
    boolean uhd() { return p.getBoolean("uhd", true); }
    boolean schedule() { return p.getBoolean("schedule", false); }
    int artFrom() { return p.getInt("art_from", 7); }
    int artUntil() { return p.getInt("art_until", 23); }
    /** Outside art hours: "black" keeps a black screen, "sleep" lets the TV's own timers turn it off. */
    String outside() { return p.getString("outside", "sleep"); }
    boolean dim() { return p.getBoolean("dim", false); }
    int dimFrom() { return p.getInt("dim_from", 20); }
    int dimUntil() { return p.getInt("dim_until", 7); }
    int dimLevel() { return p.getInt("dim_level", 50); }
    String wake() { return p.getString("wake", "off"); }
    /** Search shows only pictures whose shape suits the screen. */
    boolean shapeFilter() { return p.getBoolean("shape_filter", true); }

    SharedPreferences.Editor edit() { return p.edit(); }

    /** Changes whenever any setting changes. */
    String signature() { return String.valueOf(new java.util.TreeMap<>(p.getAll())); }

    /** True when hour (0-23) is in [from, until), wrapping past midnight; from == until means all day. */
    static boolean inHours(int hour, int from, int until) {
        if (from == until) return true;
        return from < until ? hour >= from && hour < until : hour >= from || hour < until;
    }

    static int hourNow() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    boolean isArtTime() {
        return !schedule() || inHours(hourNow(), artFrom(), artUntil());
    }

    /** 1 for full brightness, lower in the dimming hours. */
    float dimFactor() {
        if (!dim() || !inHours(hourNow(), dimFrom(), dimUntil())) return 1f;
        return 1f - dimLevel() / 100f;
    }

    static int matteColor(String name) {
        return Matte.color(name);
    }

    static int next(int[] values, int current, int step) {
        int i = 0;
        for (int k = 0; k < values.length; k++) if (values[k] == current) i = k;
        return values[Math.floorMod(i + step, values.length)];
    }

    static String next(String[] values, String current, int step) {
        int i = 0;
        for (int k = 0; k < values.length; k++) if (values[k].equals(current)) i = k;
        return values[Math.floorMod(i + step, values.length)];
    }
}
