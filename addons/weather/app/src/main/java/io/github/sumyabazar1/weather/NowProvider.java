package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;

/**
 * The weather for other apps on the TV, read-only. content://AUTHORITY is one row: line ("48°  ☁  Cloudy  ·  H 55°
 * L 38°", the Clean Your TV launcher's weather_line), updated (milliseconds), and its parts temp, symbol and sky; no
 * row when no city is chosen yet or the last reading is too old to show. content://AUTHORITY/days is the week ahead,
 * one row per day from today: date (yyyy-MM-dd), symbol, sky, max and min. Degrees follow the unit chosen in Weather
 * settings. There is no background service: a query older than {@link Forecast#REFRESH_MS} fetches first, so call it
 * off the main thread. Only the reading leaves the app, never the city or where it is.
 */
public final class NowProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.sumyabazar1.weather.now";
    private static final String PREFS = "reading";
    /** A reading older than this is not shown at all: an old temperature on Home is worse than none. */
    private static final long STALE_MS = 3 * 60 * 60_000L;
    private static final Object LOCK = new Object();
    /** When the last fetch failed, so a TV offline isn't asked again on every query. */
    private static long failedAt;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Context context = getContext();
        boolean week = "days".equals(uri.getLastPathSegment());
        MatrixCursor out = new MatrixCursor(week ? new String[]{"date", "symbol", "sky", "max", "min"}
                : new String[]{"line", "updated", "temp", "symbol", "sky"});
        SharedPreferences place = context.getSharedPreferences("place", Context.MODE_PRIVATE);
        if (!place.contains("name")) return out;
        SharedPreferences now = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        synchronized (LOCK) {
            long clock = System.currentTimeMillis();
            long due = week ? now.getLong("daysAt", 0) : now.getLong("at", 0);
            if (clock - due > Forecast.REFRESH_MS && clock - failedAt > Forecast.RETRY_MS) {
                try {
                    // The whole week every time: one request either way, and the week feed needs it.
                    keep(context, Forecast.fetch(Double.longBitsToDouble(place.getLong("lat", 0)),
                            Double.longBitsToDouble(place.getLong("lon", 0)), Forecast.MAX_DAYS));
                } catch (Exception e) {
                    failedAt = clock;
                }
            }
            long at = now.getLong("at", 0);
            if (clock - (week ? now.getLong("daysAt", 0) : at) > STALE_MS) return out;
            boolean f = fahrenheit(context);
            try {
                if (week) {
                    days(context, new JSONArray(now.getString("days", "[]")), f, out);
                } else {
                    int code = now.getInt("code", 0);
                    boolean day = now.getBoolean("day", true);
                    String temp = String.valueOf(Forecast.degrees(Double.longBitsToDouble(now.getLong("tempC", 0)), f));
                    String symbol = Forecast.symbol(code, day), sky = context.getString(Forecast.sky(code, day));
                    JSONObject today = todayOf(new JSONArray(now.getString("days", "[]")));
                    String line = today == null ? context.getString(R.string.home_line_now, temp, symbol, sky)
                            : context.getString(R.string.home_line, temp, symbol, sky,
                            Forecast.degrees(today.getDouble("max"), f), Forecast.degrees(today.getDouble("min"), f));
                    out.addRow(new Object[]{line, at, temp, symbol, sky});
                }
            } catch (JSONException | RuntimeException e) {
                // a broken record: nothing until the next fetch
            }
        }
        return out;
    }

    /** The unit Weather settings chose, else the build default, as the page reads it. */
    private static boolean fahrenheit(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE).getBoolean("fahrenheit",
                "f".equalsIgnoreCase(context.getString(R.string.temperature_unit).trim()));
    }

    /** The kept week as rows, from today on (a day already past is left out). */
    private static void days(Context context, JSONArray list, boolean f, MatrixCursor out) throws JSONException {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < list.length(); i++) {
            JSONObject d = list.getJSONObject(i);
            if (LocalDate.parse(d.getString("date")).isBefore(today)) continue;
            int code = d.getInt("code");
            out.addRow(new Object[]{d.getString("date"), Forecast.symbol(code, true),
                    context.getString(Forecast.sky(code, true)), Forecast.degrees(d.getDouble("max"), f),
                    Forecast.degrees(d.getDouble("min"), f)});
        }
    }

    /** Today's entry in the kept week, or null when the week doesn't have it (a reading from yesterday). */
    private static JSONObject todayOf(JSONArray list) throws JSONException {
        String today = LocalDate.now().toString();
        for (int i = 0; i < list.length(); i++) {
            if (today.equals(list.getJSONObject(i).getString("date"))) return list.getJSONObject(i);
        }
        return null;
    }

    /**
     * Remembers a fresh reading in degrees Celsius, whoever fetched it (this provider or the weather page). The week
     * is kept too, unless this reading has fewer days than the one kept (the page fetches only as many as it shows)
     * and that one is still fresh.
     */
    static void keep(Context context, Forecast.Report r) {
        SharedPreferences now = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long clock = System.currentTimeMillis();
        SharedPreferences.Editor edit = now.edit()
                .putLong("tempC", Double.doubleToRawLongBits(r.tempC)).putInt("code", r.code)
                .putBoolean("day", r.day).putLong("at", clock);
        int count = r.days.size() + (r.today == null ? 0 : 1);
        boolean keptFresh = clock - now.getLong("daysAt", 0) <= Forecast.REFRESH_MS;
        if (count >= now.getInt("daysCount", 0) || !keptFresh) {
            JSONArray days = new JSONArray();
            try {
                if (r.today != null) days.put(day(r.today));
                for (Forecast.Day d : r.days) days.put(day(d));
                edit.putString("days", days.toString()).putInt("daysCount", count).putLong("daysAt", clock);
            } catch (JSONException e) {
                // the week stays as it was
            }
        }
        edit.apply();
    }

    private static JSONObject day(Forecast.Day d) throws JSONException {
        return new JSONObject().put("date", d.date.toString()).put("code", d.code).put("max", d.maxC).put("min", d.minC);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("read-only");
    }
}
