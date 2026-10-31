package io.github.lostininaka.weatherglance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Open-Meteo over plain HTTP: current conditions for one point, and the place search. No key, no account,
 * no attribution string to display. Every call blocks, so the caller runs it off the main thread.
 */
final class OpenMeteo {
    private static final String CURRENT = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto";
    private static final String SEARCH = "https://geocoding-api.open-meteo.com/v1/search"
            + "?count=6&language=en&format=json&name=%s";

    private OpenMeteo() {}

    /** Temperature and weather code right now; null when the answer has no current reading. */
    static Now current(double lat, double lon) throws IOException {
        JSONObject answer;
        try {
            answer = new JSONObject(get(String.format(Locale.US, CURRENT, lat, lon)));
        } catch (org.json.JSONException e) {
            return null;
        }
        JSONObject cur = answer.optJSONObject("current");
        if (cur == null || !cur.has("temperature_2m")) return null;
        return new Now((int) Math.round(cur.optDouble("temperature_2m", Double.NaN)),
                cur.optInt("weather_code", -1));
    }

    /** Places matching a name, best first; empty when nothing matches. */
    static List<Place> search(String query) throws IOException {
        List<Place> out = new ArrayList<>();
        JSONObject answer;
        try {
            answer = new JSONObject(get(String.format(Locale.US, SEARCH, URLEncoder.encode(query, "UTF-8"))));
        } catch (org.json.JSONException e) {
            return out;
        }
        JSONArray results = answer.optJSONArray("results");
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;
            out.add(new Place(r.optString("name", ""), r.optString("admin1", ""),
                    r.optString("country", ""), r.optDouble("latitude", Double.NaN),
                    r.optDouble("longitude", Double.NaN)));
        }
        return out;
    }

    private static String get(String address) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        try {
            c.setConnectTimeout(8_000);
            c.setReadTimeout(10_000);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** A reading as the provider stores it. */
    static final class Now {
        final int celsius;
        final int code;

        Now(int celsius, int code) {
            this.celsius = celsius;
            this.code = code;
        }
    }

    /** One place from the search: what the owner picks and what the provider remembers. */
    static final class Place {
        final String name, region, country;
        final double lat, lon;

        Place(String name, String region, String country, double lat, double lon) {
            this.name = name;
            this.region = region;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
        }

        /** "Takizawa, Iwate, Japan" - the full name, minus anything the answer left empty. */
        String label() {
            StringBuilder b = new StringBuilder(name);
            for (String part : new String[] {region, country}) {
                if (part != null && !part.isEmpty() && !part.equals(name)) b.append(", ").append(part);
            }
            return b.toString();
        }
    }
}
