package com.gathin.photodream;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The weather now, for the weather option: the TV's own location (coarse, rounded to about 1 km before it is
 * sent) and Open-Meteo, which needs no key. Only called while the option is on; nothing else goes out.
 */
final class Weather {
    static final long REFRESH_MS = 30 * 60_000L;
    private static final int TIMEOUT_MS = 8_000;

    private Weather() {}

    static boolean permitted(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Whether the TV's location services are on (Settings > Privacy > Location on Google TV). */
    static boolean locationOn(Context context) {
        LocationManager lm = context.getSystemService(LocationManager.class);
        if (lm == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
        return !lm.getProviders(true).isEmpty();
    }

    /** The newest location the TV knows, or a fresh one; {@code done} gets null when there is none. */
    @SuppressWarnings("deprecation")
    static void locate(Context context, Consumer<Location> done) {
        LocationManager lm = context.getSystemService(LocationManager.class);
        if (lm == null || !permitted(context) || !locationOn(context)) {
            done.accept(null);
            return;
        }
        try {
            Location best = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null) {
                done.accept(best);
                return;
            }
            if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                done.accept(null);
                return;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.getMainExecutor(),
                        done::accept);
            } else {
                // All four methods spelled out: before Android 11 they have no defaults.
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, new LocationListener() {
                    @Override public void onLocationChanged(Location location) { done.accept(location); }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                    @Override public void onProviderEnabled(String provider) { }
                    @Override public void onProviderDisabled(String provider) { }
                }, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException e) {
            done.accept(null);
        }
    }

    /** "12°  ☁" for the place, blocking: call it off the main thread. */
    static String now(double latitude, double longitude) throws IOException, JSONException {
        // Two decimals is about 1 km: enough for the weather, no more of where the TV is than that.
        String url = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.2f"
                + "&longitude=%.2f&current=temperature_2m,weather_code,is_day", latitude, longitude);
        JSONObject current = get(url).getJSONObject("current");
        int temp = (int) Math.round(current.getDouble("temperature_2m"));
        return temp + "°  " + symbol(current.getInt("weather_code"), current.optInt("is_day", 1) == 1);
    }

    /** A plain symbol for a WMO weather code, from characters every TV font has. */
    static String symbol(int code, boolean day) {
        if (code == 0 || code == 1) return day ? "\u2600" : "\u263E";   // sun, moon
        if (code == 2) return "\u26C5";                                  // sun behind cloud
        if (code == 3 || code == 45 || code == 48) return "\u2601";      // cloud (fog too)
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "\u2744";   // snow
        if (code >= 95) return "\u26A1";                                 // thunder
        return "\u2602";                                                 // drizzle, rain, showers
    }

    private static JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, n);
                if (out.size() > 64 * 1024) throw new IOException("reply too large");
            }
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }
}
