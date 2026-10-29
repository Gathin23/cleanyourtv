package io.github.maxhayim.startupbranding;

import android.content.Context;
import android.content.SharedPreferences;

/** The welcome screen's settings, kept in the app's private preferences. */
final class Prefs {
    private Prefs() {
    }

    private static Look.Store store(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE);
        return new Look.Store() {
            @Override
            public String get(String key) {
                return p.getString(key, null);
            }

            @Override
            public void put(String key, String value) {
                p.edit().putString(key, value).apply();
            }
        };
    }

    static Look load(Context c) {
        return Look.load(store(c));
    }

    static void save(Context c, Look l) {
        l.save(store(c));
    }

    /** Back to the defaults: everything the owner set is forgotten (pictures stay unless deleted). */
    static void clear(Context c) {
        c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE).edit().clear().apply();
    }
}
