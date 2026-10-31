package com.gathin.hindipanchang;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages the user's selected city, coordinates, timezone, and language preference.
 * Supports:
 * 1. Offline preset cities (India & world) so the calendar always works without internet.
 * 2. Live city search via Open-Meteo's free no-key Geocoding API (same as the Weather add-on).
 * 3. Automatic IP-based location detection via ipwho.is.
 */
final class LocationStore {
    private static final String PREFS = "panchang_prefs";
    private static final int TIMEOUT_MS = 6_000;

    static final int LANG_BILINGUAL = 0; // हिन्दी + English
    static final int LANG_HINDI = 1;     // हिन्दी (संस्कृत)
    static final int LANG_ENGLISH = 2;   // English

    static final class Place {
        final String name;
        final String detail;
        final double latitude;
        final double longitude;
        final String timezone;

        Place(String name, String detail, double latitude, double longitude, String timezone) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timezone = timezone;
        }

        ZoneId zoneId() {
            try {
                if (timezone != null && !timezone.isEmpty()) return ZoneId.of(timezone);
            } catch (Exception ignored) { }
            return ZoneId.systemDefault();
        }

        String displayLabel() {
            return detail == null || detail.isEmpty() ? name : name + ", " + detail;
        }
    }

    static final Place[] PRESETS = new Place[]{
        new Place("New Delhi", "Delhi, India", 28.6139, 77.2090, "Asia/Kolkata"),
        new Place("Varanasi (काशी)", "Uttar Pradesh, India", 25.3176, 82.9739, "Asia/Kolkata"),
        new Place("Ujjain (उज्जैन)", "Madhya Pradesh, India", 23.1765, 75.7885, "Asia/Kolkata"),
        new Place("Ayodhya (अयोध्या)", "Uttar Pradesh, India", 26.7922, 82.1998, "Asia/Kolkata"),
        new Place("Prayagraj (प्रयागराज)", "Uttar Pradesh, India", 25.4358, 81.8463, "Asia/Kolkata"),
        new Place("Haridwar (हरिद्वार)", "Uttarakhand, India", 29.9457, 78.1642, "Asia/Kolkata"),
        new Place("Mumbai", "Maharashtra, India", 19.0760, 72.8777, "Asia/Kolkata"),
        new Place("Jaipur", "Rajasthan, India", 26.9124, 75.7873, "Asia/Kolkata"),
        new Place("Ahmedabad", "Gujarat, India", 23.0225, 72.5714, "Asia/Kolkata"),
        new Place("Lucknow", "Uttar Pradesh, India", 26.8467, 80.9462, "Asia/Kolkata"),
        new Place("Patna", "Bihar, India", 25.5941, 85.1376, "Asia/Kolkata"),
        new Place("Kolkata", "West Bengal, India", 22.5726, 88.3639, "Asia/Kolkata"),
        new Place("Bengaluru", "Karnataka, India", 12.9716, 77.5946, "Asia/Kolkata"),
        new Place("Hyderabad", "Telangana, India", 17.3850, 78.4867, "Asia/Kolkata"),
        new Place("Chennai", "Tamil Nadu, India", 13.0827, 80.2707, "Asia/Kolkata"),
        new Place("Pune", "Maharashtra, India", 18.5204, 73.8567, "Asia/Kolkata"),
        new Place("Bhopal", "Madhya Pradesh, India", 23.2599, 77.4126, "Asia/Kolkata"),
        new Place("Indore", "Madhya Pradesh, India", 22.7196, 75.8577, "Asia/Kolkata"),
        new Place("Chandigarh", "India", 30.7333, 76.7794, "Asia/Kolkata"),
        new Place("Chicago", "Illinois, USA", 41.8781, -87.6298, "America/Chicago"),
        new Place("Dallas", "Texas, USA", 32.7767, -96.7970, "America/Chicago"),
        new Place("New York", "New York, USA", 40.7128, -74.0060, "America/New_York"),
        new Place("San Francisco", "California, USA", 37.7749, -122.4194, "America/Los_Angeles"),
        new Place("London", "United Kingdom", 51.5074, -0.1278, "Europe/London"),
        new Place("Toronto", "Ontario, Canada", 43.6532, -79.3832, "America/Toronto")
    };

    private LocationStore() { }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean hasSavedPlace(Context ctx) {
        return prefs(ctx).contains("name");
    }

    static Place load(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        if (sp.contains("name")) {
            String name = sp.getString("name", "New Delhi");
            String detail = sp.getString("detail", "India");
            double lat = Double.longBitsToDouble(sp.getLong("lat", Double.doubleToRawLongBits(28.6139)));
            double lon = Double.longBitsToDouble(sp.getLong("lon", Double.doubleToRawLongBits(77.2090)));
            String tz = sp.getString("tz", ZoneId.systemDefault().getId());
            return new Place(name, detail, lat, lon, tz);
        }
        // Smart default based on device timezone before auto-detect completes
        String sysTz = ZoneId.systemDefault().getId();
        if (sysTz.contains("Chicago") || sysTz.contains("Central")) {
            return new Place("Central Time (Auto)", "USA", 38.0, -90.0, sysTz);
        } else if (sysTz.contains("New_York") || sysTz.contains("Eastern")) {
            return new Place("Eastern Time (Auto)", "USA", 40.7128, -74.0060, sysTz);
        } else if (sysTz.contains("Los_Angeles") || sysTz.contains("Pacific")) {
            return new Place("Pacific Time (Auto)", "USA", 37.7749, -122.4194, sysTz);
        }
        return PRESETS[0]; // New Delhi default
    }

    static void save(Context ctx, Place p) {
        prefs(ctx).edit()
                .putString("name", p.name)
                .putString("detail", p.detail)
                .putLong("lat", Double.doubleToRawLongBits(p.latitude))
                .putLong("lon", Double.doubleToRawLongBits(p.longitude))
                .putString("tz", p.timezone)
                .apply();
    }

    static int getLang(Context ctx) {
        return prefs(ctx).getInt("lang", LANG_BILINGUAL);
    }

    static int cycleLang(Context ctx) {
        int next = (getLang(ctx) + 1) % 3;
        prefs(ctx).edit().putInt("lang", next).apply();
        return next;
    }

    /** Searches Open-Meteo geocoding API for up to 6 matching cities. */
    static List<Place> searchOnline(String query) throws IOException, JSONException {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=6&format=json&name="
                + URLEncoder.encode(query.trim(), "UTF-8") + "&language=en";
        JSONObject reply = getJson(url);
        List<Place> out = new ArrayList<>();
        JSONArray results = reply.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            StringBuilder detail = new StringBuilder(r.optString("admin1"));
            String country = r.optString("country");
            if (!country.isEmpty()) detail.append(detail.length() > 0 ? ", " : "").append(country);
            String tz = r.optString("timezone", ZoneId.systemDefault().getId());
            out.add(new Place(
                    r.getString("name"),
                    detail.toString(),
                    r.getDouble("latitude"),
                    r.getDouble("longitude"),
                    tz
            ));
        }
        return out;
    }

    /** Auto-detects current city, coordinates, and timezone from IP (ipwho.is). */
    static Place autoDetectOnline() throws IOException, JSONException {
        JSONObject r = getJson("https://ipwho.is/");
        if (!r.optBoolean("success", true)) return null;
        String city = r.optString("city", "");
        if (city.isEmpty()) return null;
        String region = r.optString("region", "");
        String country = r.optString("country", "");
        StringBuilder detail = new StringBuilder(region);
        if (!country.isEmpty()) detail.append(detail.length() > 0 ? ", " : "").append(country);
        double lat = r.getDouble("latitude");
        double lon = r.getDouble("longitude");
        String tz = ZoneId.systemDefault().getId();
        JSONObject tzObj = r.optJSONObject("timezone");
        if (tzObj != null && !tzObj.optString("id").isEmpty()) {
            tz = tzObj.optString("id");
        }
        return new Place(city, detail.toString(), lat, lon, tz);
    }

    private static JSONObject getJson(String urlStr) throws IOException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            conn.disconnect();
        }
    }
}

