package io.github.lostininaka.weatherglance;

import android.content.Context;
import android.content.SharedPreferences;

/** The city the owner picked and the last line published, in this app's own SharedPreferences. */
final class Store {
    /** How long a fetched line is trusted; the launcher is told to read again after it expires. */
    static final long STALE_MS = 20 * 60_000L;

    private static final String FILE = "weather";
    private final SharedPreferences p;

    Store(Context ctx) {
        p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    boolean hasPlace() {
        return !place().isEmpty() && !Double.isNaN(lat()) && !Double.isNaN(lon());
    }

    String place() {
        return p.getString("place", "");
    }

    double lat() {
        return number("lat");
    }

    double lon() {
        return number("lon");
    }

    /** The city alone, without the region and country: what the line under the clock can afford. */
    String shortPlace() {
        String place = place();
        int comma = place.indexOf(',');
        return comma > 0 ? place.substring(0, comma).trim() : place;
    }

    String icon() {
        return p.getString("icon", "");
    }

    String text() {
        return p.getString("text", "");
    }

    long updated() {
        return p.getLong("updated", 0L);
    }

    boolean stale() {
        return System.currentTimeMillis() - updated() >= STALE_MS;
    }

    void savePlace(String place, double lat, double lon) {
        // The old line belongs to the old town: drop it rather than show it under the new name.
        p.edit().putString("place", place).putString("lat", Double.toString(lat))
                .putString("lon", Double.toString(lon)).remove("icon").remove("text").remove("updated").apply();
    }

    void saveLine(String icon, String text, long updated) {
        p.edit().putString("icon", icon).putString("text", text).putLong("updated", updated).apply();
    }

    private double number(String key) {
        try {
            return Double.parseDouble(p.getString(key, "NaN"));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
