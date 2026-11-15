package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Locale;

/**
 * One line of weather for the Clean Your TV launcher's header_weather key ("25° Clear · AQI 158"), so the launcher
 * stays offline. It fetches only when asked and its reading is older than {@link Forecast#REFRESH_MS} (or
 * {@link Forecast#RETRY_MS} after a failure): no service, no alarms. The launcher asks off its main thread, so the
 * fetch runs before the answer. Read-only and exported, so it serves only the line, never the place name.
 */
public final class GlanceProvider extends ContentProvider {
    /** A reading older than this is not shown at all, rather than shown wrong. */
    private static final long STALE_MS = 3 * 60 * 60_000L;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public synchronized Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        MatrixCursor c = new MatrixCursor(new String[] {"line"});
        Context ctx = getContext();
        if (ctx == null) return c;
        SharedPreferences place = ctx.getSharedPreferences("place", Context.MODE_PRIVATE);
        if (!place.contains("lat")) return c;   // no place chosen yet: no line
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        String key = String.format(Locale.ROOT, "%.4f,%.4f", lat, lon);

        SharedPreferences cache = ctx.getSharedPreferences("glance", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!key.equals(cache.getString("key", "")) || elapsedOver(now, cache.getLong("at", 0), Forecast.REFRESH_MS)) {
            if (elapsedOver(now, cache.getLong("tried", 0), Forecast.RETRY_MS)) {
                cache.edit().putLong("tried", now).apply();
                try {
                    Forecast.Report r = Forecast.fetch(lat, lon, Forecast.MIN_DAYS);
                    cache.edit().putString("key", key).putLong("at", now)
                            .putLong("temp", Double.doubleToRawLongBits(r.tempC))
                            .putInt("code", r.code).putBoolean("day", r.day).putInt("aqi", r.aqi).apply();
                } catch (Exception offline) {
                    // Keep the last reading; a query after RETRY_MS tries again.
                }
            }
        }
        if (!key.equals(cache.getString("key", "")) || elapsedOver(now, cache.getLong("at", 0), STALE_MS)) return c;

        boolean fahrenheit = ctx.getSharedPreferences("options", Context.MODE_PRIVATE).getBoolean("fahrenheit",
                "f".equalsIgnoreCase(ctx.getString(R.string.temperature_unit).trim()));
        c.addRow(new Object[] {line(Forecast.degrees(Double.longBitsToDouble(cache.getLong("temp", 0)), fahrenheit),
                ctx.getString(Forecast.sky(cache.getInt("code", 3), cache.getBoolean("day", true))), cache.getInt("aqi", -1))});
        return c;
    }

    /** "25° Clear · AQI 158", without the AQI when it is unknown (below 0). */
    static String line(int degrees, String sky, int aqi) {
        return degrees + "° " + sky + (aqi < 0 ? "" : " · AQI " + aqi);
    }

    /** More than {@code limit} ms since {@code then}, or {@code then} is ahead of a clock that moved back. */
    static boolean elapsedOver(long now, long then, long limit) {
        return now < then || now - then > limit;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
