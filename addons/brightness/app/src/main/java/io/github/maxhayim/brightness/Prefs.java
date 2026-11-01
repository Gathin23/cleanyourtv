package io.github.maxhayim.brightness;

import android.content.Context;
import android.content.SharedPreferences;

/** The settings in the app's own storage. Writing them is all it takes: the service listens and redraws. */
final class Prefs {
    static final String FILE = "brightness";
    static final String TAG = "Brightness";

    private Prefs() {
    }

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Dimming.Settings load(Context c) {
        SharedPreferences p = of(c);
        Dimming.Settings s = new Dimming.Settings();
        s.on = p.getBoolean("on", s.on);
        s.brightness = p.getInt("brightness", s.brightness);
        s.nightBrightness = p.getInt("night_brightness", s.nightBrightness);
        s.warmth = p.getInt("warmth", s.warmth);
        s.nightStart = p.getInt("night_start", s.nightStart);
        s.nightEnd = p.getInt("night_end", s.nightEnd);
        s.tvPicture = p.getBoolean("tv_picture", s.tvPicture);
        s.tvNight = picture(p, "tv_night_", Dimming.Picture.NIGHT);
        try {
            s.night = Dimming.Night.valueOf(p.getString("night", s.night.name()));
        } catch (IllegalArgumentException e) {
            s.night = Dimming.Night.SCHEDULED;
        }
        return s.clamped();
    }

    static void save(Context c, Dimming.Settings s) {
        s.clamped();
        of(c).edit()
                .putBoolean("on", s.on)
                .putInt("brightness", s.brightness)
                .putInt("night_brightness", s.nightBrightness)
                .putInt("warmth", s.warmth)
                .putInt("night_start", s.nightStart)
                .putInt("night_end", s.nightEnd)
                .putString("night", s.night.name())
                .putBoolean("tv_picture", s.tvPicture)
                .apply();
        savePicture(c, "tv_night_", s.tvNight);
    }

    /** The night mode the one-press toggle replaced, so its next press can put it back; null when there is none. */
    static Dimming.Night nightBeforeToggle(Context c) {
        String v = of(c).getString("night_before_toggle", null);
        if (v == null) return null;
        try {
            return Dimming.Night.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static void setNightBeforeToggle(Context c, Dimming.Night replaced) {
        SharedPreferences.Editor e = of(c).edit();
        if (replaced == null) e.remove("night_before_toggle");
        else e.putString("night_before_toggle", replaced.name());
        e.apply();
    }

    /** The day picture remembered when night last began, or null. */
    static Dimming.Picture tvDay(Context c) {
        SharedPreferences p = of(c);
        return p.contains("tv_day_backlight") ? picture(p, "tv_day_", Dimming.Picture.DAY) : null;
    }

    static void saveTvDay(Context c, Dimming.Picture day) {
        savePicture(c, "tv_day_", day);
    }

    /** "night" while the TV shows the night picture this app set, so the morning knows to put the day's back. */
    static boolean tvAtNight(Context c) {
        return of(c).getBoolean("tv_at_night", false);
    }

    static void setTvAtNight(Context c, boolean atNight) {
        of(c).edit().putBoolean("tv_at_night", atNight).apply();
    }

    private static Dimming.Picture picture(SharedPreferences p, String prefix, Dimming.Picture d) {
        return new Dimming.Picture(p.getInt(prefix + "backlight", d.backlight), p.getInt(prefix + "contrast", d.contrast),
                p.getInt(prefix + "brightness", d.brightness), p.getBoolean(prefix + "dynamic", d.dynamicBacklight)).clamped();
    }

    private static void savePicture(Context c, String prefix, Dimming.Picture v) {
        of(c).edit()
                .putInt(prefix + "backlight", v.backlight)
                .putInt(prefix + "contrast", v.contrast)
                .putInt(prefix + "brightness", v.brightness)
                .putBoolean(prefix + "dynamic", v.dynamicBacklight)
                .apply();
    }

    /** Normal screen at once; the other settings stay for next time. */
    static void switchOff(Context c) {
        of(c).edit().putBoolean("on", false).apply();
    }
}
