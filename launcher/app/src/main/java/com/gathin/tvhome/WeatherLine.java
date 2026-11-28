package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The weather line under the clock (config.xml weather_line): now, and what the next hours bring, in one sentence,
 * e.g. "22° Partly cloudy · Rain likely around 18:00 · Low 15° tonight". Data from Open-Meteo (no key, no account);
 * the only thing sent is the latitude and longitude from config.xml or the theme.
 *
 * fetch() does the network read and runs off the main thread; sentence() is plain logic over arrays, so the JVM
 * tests can check it without Android or the network.
 */
final class WeatherLine {

    /** Hours ahead the "next hours" part looks at. */
    static final int AHEAD_HOURS = 6;
    /** Precipitation probability, in percent, from which rain (or snow, or a storm) is worth announcing. */
    static final int RAIN_LIKELY_PERCENT = 50;

    private WeatherLine() {
    }

    /** The words the sentence is made of, from the launcher's string resources (or the tests). */
    static final class Words {
        final String[] conditions;   // see condition(): clear, mainly clear, partly cloudy, overcast, fog, drizzle,
                                     // rain, snow, showers, snow showers, thunderstorm
        final String rainAt, snowAt, stormAt, risingTo, fallingTo, steady, lowTonight, highToday;

        /** rainAt, snowAt and stormAt take the hour (%1$s); the temperature ones take a number of degrees (%1$d). */
        Words(String[] conditions, String rainAt, String snowAt, String stormAt, String risingTo, String fallingTo,
              String steady, String lowTonight, String highToday) {
            this.conditions = conditions;
            this.rainAt = rainAt;
            this.snowAt = snowAt;
            this.stormAt = stormAt;
            this.risingTo = risingTo;
            this.fallingTo = fallingTo;
            this.steady = steady;
            this.lowTonight = lowTonight;
            this.highToday = highToday;
        }
    }

    /** The parsed forecast: now, then one entry per hour starting with the current hour. */
    static final class Forecast {
        final double tempNow;
        final int codeNow;
        final int hourNow;          // local hour of day, 0-23, at the place
        final double[] temps;       // hourly temperature, [0] is the current hour
        final int[] rainPercent;    // hourly precipitation probability, -1 when unknown
        final int[] codes;          // hourly WMO weather code
        final double highToday;     // today's maximum, NaN when unknown

        Forecast(double tempNow, int codeNow, int hourNow, double[] temps, int[] rainPercent, int[] codes,
                 double highToday) {
            this.tempNow = tempNow;
            this.codeNow = codeNow;
            this.hourNow = hourNow;
            this.temps = temps;
            this.rainPercent = rainPercent;
            this.codes = codes;
            this.highToday = highToday;
        }
    }

    /** Index into Words.conditions for a WMO weather code (Open-Meteo's weather_code), or -1 for an unknown code. */
    static int condition(int code) {
        if (code == 0) return 0;
        if (code == 1) return 1;
        if (code == 2) return 2;
        if (code == 3) return 3;
        if (code == 45 || code == 48) return 4;
        if (code >= 51 && code <= 57) return 5;
        if ((code >= 61 && code <= 67)) return 6;
        if (code >= 71 && code <= 77) return 7;
        if (code >= 80 && code <= 82) return 8;
        if (code == 85 || code == 86) return 9;
        if (code >= 95 && code <= 99) return 10;
        return -1;
    }

    private static boolean snowy(int code) {
        return (code >= 71 && code <= 77) || code == 85 || code == 86;
    }

    private static boolean stormy(int code) {
        return code >= 95 && code <= 99;
    }

    /** "18:00" style hour, in 24-hour time as the clock on the launcher shows it in most locales. */
    static String hourLabel(int hourOfDay) {
        return String.format(Locale.ROOT, "%02d:00", ((hourOfDay % 24) + 24) % 24);
    }

    static int round(double degrees) {
        return (int) Math.round(degrees);
    }

    /** The whole line, or "" when the forecast is unusable. */
    static String sentence(Forecast f, Words w) {
        if (f == null || Double.isNaN(f.tempNow)) return "";
        StringBuilder line = new StringBuilder();
        line.append(round(f.tempNow)).append('°');
        int cond = condition(f.codeNow);
        if (cond >= 0) line.append(' ').append(w.conditions[cond]);

        String next = nextHours(f, w);
        if (!next.isEmpty()) line.append("  ·  ").append(next);

        String later = laterToday(f, w);
        if (!later.isEmpty()) line.append("  ·  ").append(later);
        return line.toString();
    }

    /** The next AHEAD_HOURS hours: the first likely rain, snow or storm, else where the temperature goes. */
    static String nextHours(Forecast f, Words w) {
        int n = Math.min(AHEAD_HOURS + 1, Math.min(f.temps.length, Math.min(f.rainPercent.length, f.codes.length)));
        if (n < 2) return "";
        for (int i = 1; i < n; i++) {
            if (f.rainPercent[i] < RAIN_LIKELY_PERCENT) continue;
            String at = hourLabel(f.hourNow + i);
            if (stormy(f.codes[i])) return String.format(w.stormAt, at);
            if (snowy(f.codes[i])) return String.format(w.snowAt, at);
            return String.format(w.rainAt, at);
        }
        double max = f.temps[0], min = f.temps[0];
        for (int i = 1; i < n; i++) {
            max = Math.max(max, f.temps[i]);
            min = Math.min(min, f.temps[i]);
        }
        // Compare with the reading now rather than with the hour's slot, so a rise is never announced as "up to" a
        // value below what is showing.
        if (max - f.tempNow >= 2.5) return String.format(w.risingTo, round(max));
        if (f.tempNow - min >= 2.5) return String.format(w.fallingTo, round(min));
        return w.steady;
    }

    /**
     * Evening and night: the low before 07:00, when it is lower than now by enough to say. Before 13:00 (the small
     * hours included, when the night's low is often just the reading now): today's high, while it is still ahead.
     * Else nothing.
     */
    static String laterToday(Forecast f, Words w) {
        int h = f.hourNow;
        if (h >= 17 || h < 5) {
            int until = (h >= 17 ? 24 - h : 0) + 7 - (h < 5 ? h : 0);   // hours from now to 07:00
            int n = Math.min(until + 1, f.temps.length);
            double min = f.tempNow;
            for (int i = 1; i < n; i++) min = Math.min(min, f.temps[i]);
            if (f.tempNow - min >= 1.5) return String.format(w.lowTonight, round(min));
        }
        if (h < 13 && !Double.isNaN(f.highToday) && f.highToday - f.tempNow >= 1.5) {
            return String.format(w.highToday, round(f.highToday));
        }
        return "";
    }

    // ---- network ----------------------------------------------------------------------------------

    /** Open-Meteo's forecast URL for a place: current conditions, 24 hourly steps, today's high. */
    static String url(String latitude, String longitude) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + latitude + "&longitude=" + longitude
                + "&current=temperature_2m,weather_code"
                + "&hourly=temperature_2m,precipitation_probability,weather_code"
                + "&daily=temperature_2m_max&forecast_days=2&forecast_hours=24&timezone=auto";
    }

    /** A latitude or longitude as config gives it: a plain decimal number in range, else null. */
    static String coordinate(String value, double limit) {
        if (value == null) return null;
        String v = value.trim();
        if (!v.matches("-?\\d{1,3}(\\.\\d{1,6})?")) return null;
        double d = Double.parseDouble(v);
        return Math.abs(d) <= limit ? v : null;
    }

    /** Reads the forecast. Off the main thread; null on any failure (offline, a timeout, an unexpected answer). */
    static Forecast fetch(String latitude, String longitude) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url(latitude, longitude)).openConnection();
            con.setConnectTimeout(10_000);
            con.setReadTimeout(10_000);
            con.setUseCaches(false);
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = con.getInputStream()) {
                return parse(new JSONObject(readCapped(in, 256 * 1024)));
            }
        } catch (IOException | JSONException | RuntimeException e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String readCapped(InputStream in, int cap) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
            if (out.size() > cap) throw new IOException("answer too large");
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Open-Meteo's answer as a Forecast, starting at the current hour. */
    static Forecast parse(JSONObject json) throws JSONException {
        JSONObject current = json.getJSONObject("current");
        String nowTime = current.getString("time");            // "2026-10-26T03:00", local to the place
        int hourNow = Integer.parseInt(nowTime.substring(11, 13));
        String hourKey = nowTime.substring(0, 13);              // "2026-10-26T03"

        JSONObject hourly = json.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        int start = 0;
        for (int i = 0; i < times.length(); i++) {
            if (times.getString(i).startsWith(hourKey)) {
                start = i;
                break;
            }
        }
        JSONArray t = hourly.getJSONArray("temperature_2m");
        JSONArray p = hourly.getJSONArray("precipitation_probability");
        JSONArray c = hourly.getJSONArray("weather_code");
        int n = Math.max(0, Math.min(times.length(), Math.min(t.length(), Math.min(p.length(), c.length()))) - start);
        double[] temps = new double[n];
        int[] rain = new int[n];
        int[] codes = new int[n];
        for (int i = 0; i < n; i++) {
            temps[i] = t.isNull(start + i) ? Double.NaN : t.getDouble(start + i);
            rain[i] = p.isNull(start + i) ? -1 : p.getInt(start + i);
            codes[i] = c.isNull(start + i) ? -1 : c.getInt(start + i);
        }
        // A missing hour would poison min/max: carry the reading before it instead.
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(temps[i])) temps[i] = i == 0 ? current.getDouble("temperature_2m") : temps[i - 1];
        }
        double high = Double.NaN;
        JSONObject daily = json.optJSONObject("daily");
        if (daily != null) {
            JSONArray max = daily.optJSONArray("temperature_2m_max");
            if (max != null && max.length() > 0 && !max.isNull(0)) high = max.getDouble(0);
        }
        return new Forecast(current.getDouble("temperature_2m"), current.optInt("weather_code", -1), hourNow,
                temps, rain, codes, high);
    }
}
