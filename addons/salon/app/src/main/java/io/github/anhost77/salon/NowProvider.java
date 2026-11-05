package io.github.anhost77.salon;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.format.TextStyle;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The weather for the launcher's Home panel (weather_panel in the launcher's config.xml). The launcher stays offline:
 * it only queries content://io.github.anhost77.salon.now/now, while Home is on screen. A query answers at once
 * from the last reading and, when that is older than 30 minutes, fetches a new one for the chosen city and calls
 * notifyChange. Rows: the city now first, then the next three days. Nothing private: the city name and the weather.
 */
public final class NowProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://io.github.anhost77.salon.now/now");
    /** The home photo (Réglages): a query gives its version (0 = none), openFile the JPEG. */
    static final Uri PHOTO = Uri.parse("content://io.github.anhost77.salon.now/photo");
    private static final String[] COLUMNS = {"label", "sky", "temp", "low", "feels", "code", "day"};
    private static final long STALE_MS = 30 * 60_000L;
    private static final long RETRY_MS = 5 * 60_000L;
    private final AtomicBoolean fetching = new AtomicBoolean();

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Read-only, and only the home photo: nothing else in the app's files is reachable from here. */
    @Override
    public android.os.ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        if (!"photo".equals(uri.getLastPathSegment()) || !"r".equals(mode)) throw new java.io.FileNotFoundException();
        return android.os.ParcelFileDescriptor.open(HomePhoto.file(getContext()),
                android.os.ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if ("tv".equals(uri.getLastPathSegment())) return TvTonight.query(getContext());
        if ("picks".equals(uri.getLastPathSegment())) return Picks.query(getContext());
        if ("radio".equals(uri.getLastPathSegment())) return Radios.query(getContext());
        if ("photo".equals(uri.getLastPathSegment())) {
            MatrixCursor photo = new MatrixCursor(new String[] {"version"});
            photo.addRow(new Object[] {HomePhoto.file(getContext()).isFile() ? Settings.photoVersion() : 0L});
            photo.setNotificationUri(getContext().getContentResolver(), PHOTO);
            return photo;
        }
        SharedPreferences place = getContext().getSharedPreferences("place", 0);
        SharedPreferences cache = getContext().getSharedPreferences("now", 0);
        MatrixCursor out = new MatrixCursor(COLUMNS);
        if (!place.contains("name")) return out;
        String key = place.getString("name", "") + place.getLong("lat", 0) + place.getLong("lon", 0);
        long age = System.currentTimeMillis() - cache.getLong("at", 0);
        boolean sameCity = key.equals(cache.getString("key", ""));
        if (!sameCity || age > STALE_MS) fetch(place, cache, key);
        if (!sameCity) return out;
        try {
            JSONArray rows = new JSONArray(cache.getString("rows", "[]"));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                out.addRow(new Object[] {r.getString("label"), r.getString("sky"), r.getInt("temp"), r.getInt("low"),
                        r.getInt("feels"), r.getInt("code"), r.getInt("day")});
            }
        } catch (Exception broken) {
            // A cache from an older build: the next fetch replaces it.
        }
        out.setNotificationUri(getContext().getContentResolver(), URI);
        return out;
    }

    private void fetch(SharedPreferences place, SharedPreferences cache, String key) {
        // RETRY_MS between tries for the same city, counted from the last try, failed or not: "key" is written only on
        // a success, so it cannot tell a first fetch, or one for a city just chosen, from a retry. Another city goes
        // at once. (A recent success never gets here: its reading is not stale yet.)
        if (System.currentTimeMillis() - cache.getLong("tried", 0) < RETRY_MS
                && key.equals(cache.getString("tried_key", ""))) return;
        if (!fetching.compareAndSet(false, true)) return;
        cache.edit().putLong("tried", System.currentTimeMillis()).putString("tried_key", key).apply();
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        String city = place.getString("name", "");
        new Thread(() -> {
            try {
                Forecast.Report r = Forecast.fetch(lat, lon, Forecast.MIN_DAYS);
                JSONArray rows = new JSONArray();
                rows.put(row(city, r.code, r.day, r.tempC, r.tempC, r.feelsC));
                for (int i = 0; i < Math.min(3, r.days.size()); i++) {
                    Forecast.Day d = r.days.get(i);
                    String name = d.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
                    rows.put(row(name, d.code, true, d.maxC, d.minC, d.maxC));
                }
                cache.edit().putString("key", key).putString("rows", rows.toString())
                        .putLong("at", System.currentTimeMillis()).apply();
                getContext().getContentResolver().notifyChange(URI, null);
            } catch (Exception offline) {
                // Keep the last reading; the next query after RETRY_MS tries again.
            } finally {
                fetching.set(false);
            }
        }, "weather-now").start();
    }

    private JSONObject row(String label, int code, boolean day, double hi, double lo, double feels) throws Exception {
        return new JSONObject().put("label", label).put("sky", getContext().getString(Forecast.sky(code, day)))
                .put("temp", Forecast.degrees(hi, false)).put("low", Forecast.degrees(lo, false))
                .put("feels", Forecast.degrees(feels, false)).put("code", code).put("day", day ? 1 : 0);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
