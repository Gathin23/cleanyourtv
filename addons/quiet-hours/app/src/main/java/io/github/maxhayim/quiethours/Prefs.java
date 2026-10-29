package io.github.maxhayim.quiethours;

import android.content.Context;
import android.content.SharedPreferences;

/** The settings in the app's own storage. Writing them is all it takes: the service listens and checks again. */
final class Prefs {
    static final String TAG = "QuietHours";

    private Prefs() {
    }

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences("quiet", Context.MODE_PRIVATE);
    }

    static Quiet.Settings load(Context c) {
        SharedPreferences p = of(c);
        Quiet.Settings s = new Quiet.Settings();
        try {
            s.mode = Quiet.Mode.valueOf(p.getString("mode", s.mode.name()));
        } catch (IllegalArgumentException e) {
            s.mode = Quiet.Mode.SCHEDULED;
        }
        s.cap = p.getInt("cap", s.cap);
        s.start = p.getInt("start", s.start);
        s.end = p.getInt("end", s.end);
        s.headphonesFree = p.getBoolean("headphones_free", s.headphonesFree);
        return s.clamped();
    }

    static void save(Context c, Quiet.Settings s) {
        s.clamped();
        of(c).edit()
                .putString("mode", s.mode.name())
                .putInt("cap", s.cap)
                .putInt("start", s.start)
                .putInt("end", s.end)
                .putBoolean("headphones_free", s.headphonesFree)
                .apply();
    }
}
