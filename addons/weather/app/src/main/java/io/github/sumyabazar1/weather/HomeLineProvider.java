package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The weather as one line under the Clean Your TV launcher's clock, through its game_day contract
 * (docs/modules.md): the launcher stays offline and only reads this. Top line "LVIV · CLEAR", headline
 * "2°  ↑5° ↓-1°". The reading is fetched here only when the launcher asks and the last one is older than
 * 30 minutes (5 after a failure): no background service, no alarms.
 */
public final class HomeLineProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.sumyabazar1.weather.home";
    static final Uri TURNS = Uri.parse("content://" + AUTHORITY + "/turns");
    private static final String[] COLUMNS =
            {"top_line", "word", "start_ms", "end_ms", "colour", "calendar", "turn_seconds", "headline"};
    private static final String CACHE = "home_line";
    /** A reading older than this is not shown at all, rather than shown wrong. */
    private static final long STALE_MS = 3 * 60 * 60_000L;
    private static final int COLOUR = 0xFF4A90D9;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean fetching = new AtomicBoolean();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        if (!"turns".equals(uri.getLastPathSegment())) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        MatrixCursor c = new MatrixCursor(COLUMNS);
        c.setNotificationUri(ctx.getContentResolver(), TURNS);
        SharedPreferences place = ctx.getSharedPreferences("place", Context.MODE_PRIVATE);
        String name = place.getString("name", "");
        if (name.isEmpty() || !place.contains("lat")) return c;   // no city chosen yet: no line
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        String key = String.format(Locale.ROOT, "%.4f,%.4f", lat, lon);

        SharedPreferences cache = ctx.getSharedPreferences(CACHE, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        boolean same = key.equals(cache.getString("key", ""));
        long at = same ? cache.getLong("at", 0) : 0;
        long tried = cache.getLong("tried", 0);
        // The TV's clock can jump back (a set that takes its time from the broadcast, or a late NTP sync): a reading
        // or a try "in the future" counts as old, or the throttle would wait for the clock to catch up.
        if (elapsedOver(now, at, Forecast.REFRESH_MS) && elapsedOver(now, tried, Forecast.RETRY_MS)) {
            fetch(ctx, lat, lon, key);
        }
        if (at == 0 || Math.abs(now - at) > STALE_MS) return c;

        double temp = Double.longBitsToDouble(cache.getLong("temp", 0));
        int code = cache.getInt("code", 3);
        boolean day = cache.getBoolean("day", true);
        double max = Double.longBitsToDouble(cache.getLong("max", 0));
        double min = Double.longBitsToDouble(cache.getLong("min", 0));
        String sky = ctx.getString(Forecast.sky(code, day));
        String top = (name + " · " + sky).toUpperCase(Locale.getDefault());
        String headline = String.format(Locale.ROOT, "%d°   ↑%d°  ↓%d°",
                Forecast.degrees(temp, false), Forecast.degrees(max, false), Forecast.degrees(min, false));
        // Start in the past and end when the reading goes stale: one steady turn that never counts down. Pinned to
        // now rather than to the reading's time, so a clock that moved back still has the turn running.
        c.addRow(new Object[] {top, "", now - 1, now + STALE_MS - Math.abs(now - at), COLOUR, "weather", 30, headline});
        return c;
    }

    /** More than {@code limit} ms since {@code then}, or {@code then} is ahead of the clock. */
    static boolean elapsedOver(long now, long then, long limit) {
        return now < then || now - then > limit;
    }

    private void fetch(Context ctx, double lat, double lon, String key) {
        if (!fetching.compareAndSet(false, true)) return;
        ctx.getSharedPreferences(CACHE, Context.MODE_PRIVATE).edit().putLong("tried", System.currentTimeMillis()).apply();
        io.execute(() -> {
            try {
                double[] r = Forecast.now(lat, lon);
                ctx.getSharedPreferences(CACHE, Context.MODE_PRIVATE).edit()
                        .putString("key", key)
                        .putLong("at", System.currentTimeMillis())
                        .putLong("temp", Double.doubleToRawLongBits(r[0]))
                        .putInt("code", (int) r[1])
                        .putBoolean("day", r[2] == 1)
                        .putLong("max", Double.doubleToRawLongBits(r[3]))
                        .putLong("min", Double.doubleToRawLongBits(r[4]))
                        .commit();
                ctx.getContentResolver().notifyChange(TURNS, null);
            } catch (Exception offline) {
                // Keep the last reading; the next query after RETRY_MS tries again.
            } finally {
                fetching.set(false);
            }
        });
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
