package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The line under the clock and date: "28°C · Partly cloudy". It is shown only when config.xml sets latitude and
 * longitude. Open-Meteo (no account, no key) is asked for those coordinates, at most every 30 minutes and only
 * while Home is on screen; the last reading is kept so Home shows it at once.
 */
final class WeatherLine {
    private static final String TAG = "TvHome.Weather";
    private static final long MAX_AGE_MS = 30 * 60_000L;
    private static final long RETRY_MS = 5 * 60_000L;
    private static final int MAX_BYTES = 8 * 1024;

    private final TextView view;
    private final String url;
    private final SharedPreferences preferences;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean busy;
    private boolean closed;

    WeatherLine(Context context, TextView view) {
        this.view = view;
        this.preferences = context.getSharedPreferences("weather_line", Context.MODE_PRIVATE);
        this.url = buildUrl(context.getString(R.string.latitude), context.getString(R.string.longitude));
    }

    /** The request URL, or null when the coordinates are missing or not plain decimal numbers. */
    static String buildUrl(String latitude, String longitude) {
        try {
            double lat = Double.parseDouble(latitude.trim());
            double lon = Double.parseDouble(longitude.trim());
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            return String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                    + "&current=temperature_2m,weather_code&timezone=auto", lat, lon);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Shows the last reading and, when it is older than 30 minutes, asks for a new one. */
    void refresh() {
        if (url == null || closed) {
            view.setVisibility(View.GONE);
            return;
        }
        show();
        long now = System.currentTimeMillis();
        long age = now - preferences.getLong("read_at", 0);
        long sinceFail = now - preferences.getLong("failed_at", 0);
        if (!busy && age > MAX_AGE_MS && sinceFail > RETRY_MS) {
            busy = true;
            io.execute(this::fetch);
        }
    }

    void close() {
        closed = true;
        io.shutdownNow();
    }

    private void show() {
        if (!preferences.contains("temp")) {
            view.setVisibility(View.GONE);
            return;
        }
        String text = String.format(Locale.US, "%d°C · %s",
                preferences.getInt("temp", 0), describe(preferences.getInt("code", -1)));
        view.setText(text);
        view.setVisibility(View.VISIBLE);
    }

    private void fetch() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != 200) throw new IllegalStateException("HTTP " + connection.getResponseCode());
            JSONObject current = new JSONObject(read(connection.getInputStream())).getJSONObject("current");
            int temp = (int) Math.round(current.getDouble("temperature_2m"));
            int code = current.getInt("weather_code");
            preferences.edit().putInt("temp", temp).putInt("code", code)
                    .putLong("read_at", System.currentTimeMillis()).remove("failed_at").apply();
        } catch (Exception e) {
            // Keep the last reading; try again in a few minutes. The URL holds the coordinates, so it is not logged.
            Log.w(TAG, "Weather refresh failed: " + e.getClass().getSimpleName());
            preferences.edit().putLong("failed_at", System.currentTimeMillis()).apply();
        } finally {
            if (connection != null) connection.disconnect();
            view.post(() -> {
                busy = false;
                if (!closed) show();
            });
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int n;
            while ((n = stream.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                if (out.size() > MAX_BYTES) throw new java.io.IOException("response too large");
            }
            return out.toString("UTF-8");
        }
    }

    /** WMO weather code to a few words. */
    static String describe(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "";
    }
}
