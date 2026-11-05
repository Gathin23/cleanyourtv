package io.github.sumyabazar1.weather;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Open-Meteo, which needs no key: the place search, the weather now and for the next days, and the US air
 * quality index. The only thing sent is the place name typed in the search, then that place's latitude and
 * longitude. Every call blocks, so WeatherActivity makes them off the UI thread.
 */
final class Forecast {
    static final long REFRESH_MS = 30 * 60_000L;
    static final long RETRY_MS = 5 * 60_000L;
    private static final int TIMEOUT_MS = 8_000;
    /** Days after today the forecast row can show (options.xml, forecast_days). */
    static final int MIN_DAYS = 3, MAX_DAYS = 7;

    private Forecast() { }

    /** A place from the search: its name, region and country for the list, and where it is. */
    static final class Place {
        final String name;
        final String detail;
        final double latitude;
        final double longitude;

        Place(String name, String detail, double latitude, double longitude) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * The weather now: degrees Celsius as read (air and feels-like; {@link #degrees} rounds them for the page), the
     * WMO weather code, day or night, the relative humidity in % and the wind speed in km/h.
     */
    static final class Report {
        final double tempC;
        final double feelsC;
        /** The WMO code, rain turned into snow where the air is cold enough for it (see {@link #snowIfCold}). */
        final int code;
        final boolean day;
        /** The next days, tomorrow first. */
        final List<Day> days;
        /** US AQI, or -1 when the air quality read failed. */
        final int aqi;
        final int humidity;
        final int windKmh;

        Report(double tempC, double feelsC, int code, boolean day, List<Day> days, int aqi, int humidity, int windKmh) {
            this.tempC = tempC;
            this.feelsC = feelsC;
            this.code = code;
            this.day = day;
            this.days = days;
            this.aqi = aqi;
            this.humidity = humidity;
            this.windKmh = windKmh;
        }
    }

    /** One forecast day: its date, the WMO code and the high and low in degrees Celsius as read. */
    static final class Day {
        final LocalDate date;
        final int code;
        final double maxC;
        final double minC;

        Day(LocalDate date, int code, double maxC, double minC) {
            this.date = date;
            this.code = code;
            this.maxC = maxC;
            this.minC = minC;
        }
    }

    /** Up to five places matching what was typed, best match first. Empty when nothing matches. */
    static List<Place> search(String name) throws IOException, JSONException {
        JSONObject reply = get("https://geocoding-api.open-meteo.com/v1/search?count=5&format=json&name="
                + URLEncoder.encode(name.trim(), "UTF-8") + "&language=" + Locale.getDefault().getLanguage());
        List<Place> out = new ArrayList<>();
        JSONArray results = reply.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            StringBuilder detail = new StringBuilder(r.optString("admin1"));
            String country = r.optString("country");
            if (!country.isEmpty()) detail.append(detail.length() > 0 ? ", " : "").append(country);
            out.add(new Place(r.getString("name"), detail.toString(), r.getDouble("latitude"), r.getDouble("longitude")));
        }
        return out;
    }

    /** Days after today to show: the option, kept between {@link #MIN_DAYS} and {@link #MAX_DAYS}. */
    static int forecastDays(int wanted) {
        return Math.max(MIN_DAYS, Math.min(MAX_DAYS, wanted));
    }

    /**
     * The weather and air quality at a place, with this many days after today. The air quality is a separate
     * service: if it fails, aqi is -1.
     */
    static Report fetch(double latitude, double longitude, int days) throws IOException, JSONException {
        String where = String.format(Locale.ROOT, "latitude=%.4f&longitude=%.4f", latitude, longitude);
        // DWD's ICON model: Open-Meteo's default blend called light snow over Ulaanbaatar "overcast" while ICON,
        // and Google, had snow falling.
        JSONObject reply = get("https://api.open-meteo.com/v1/forecast?" + where + "&models=icon_seamless"
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,is_day,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days="
                + (forecastDays(days) + 1));
        JSONObject current = reply.getJSONObject("current");
        double temp = current.getDouble("temperature_2m");
        double humidity = current.getDouble("relative_humidity_2m");
        int code = snowIfCold(current.getInt("weather_code"), temp, humidity);
        return new Report(temp, current.getDouble("apparent_temperature"),
                code, current.getInt("is_day") == 1,
                days(reply.getJSONObject("daily")), aqiOrUnknown(where),
                (int) Math.round(humidity), (int) Math.round(current.optDouble("wind_speed_10m", 0)));
    }

    /**
     * The models type precipitation by the air temperature, and around freezing they call rain what reaches the
     * ground as snow (ICON and GFS: "light rain" at 1 °C in a Ulaanbaatar blizzard). The wet-bulb temperature,
     * which counts how dry the air is, decides better: at 1 °C or below, rain and drizzle become snow of the same
     * strength. Freezing rain and drizzle stay, as they are what falls.
     */
    static int snowIfCold(int code, double tempC, double humidity) {
        if (wetBulb(tempC, humidity) > 1.0) return code;
        switch (code) {
            case 51: case 53: case 61: case 80: return 71;
            case 55: case 63: case 81: return 73;
            case 65: case 82: return 75;
            default: return code;
        }
    }

    /** Stull (2011), from the air temperature in °C and the relative humidity in %; within 1 °C at sea level. */
    static double wetBulb(double t, double rh) {
        return t * Math.atan(0.151977 * Math.sqrt(rh + 8.313659)) + Math.atan(t + rh) - Math.atan(rh - 1.676331)
                + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035;
    }

    /** Tomorrow and the days after it; today (index 0) is the current reading. */
    private static List<Day> days(JSONObject daily) throws JSONException {
        JSONArray dates = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray max = daily.getJSONArray("temperature_2m_max");
        JSONArray min = daily.getJSONArray("temperature_2m_min");
        List<Day> out = new ArrayList<>();
        for (int i = 1; i < dates.length(); i++) {
            // With forecast_days 7 the last day can come back without values: stop there instead of failing the page.
            if (codes.isNull(i) || max.isNull(i) || min.isNull(i)) break;
            // A day whose high stays near freezing gets the same rain-to-snow correction, with the high and
            // saturated air, which is the warm end of the day's precipitation.
            out.add(new Day(LocalDate.parse(dates.getString(i)), snowIfCold(codes.getInt(i), max.getDouble(i), 100),
                    max.getDouble(i), min.getDouble(i)));
        }
        return out;
    }

    private static int aqiOrUnknown(String where) {
        try {
            JSONObject current = get("https://air-quality-api.open-meteo.com/v1/air-quality?" + where
                    + "&current=us_aqi").getJSONObject("current");
            return current.isNull("us_aqi") ? -1 : current.getInt("us_aqi");
        } catch (IOException | JSONException e) {
            return -1;
        }
    }

    private static JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    /** Whole degrees for the page: Celsius as read, or Fahrenheit converted before rounding (options.xml temperature_unit). */
    static int degrees(double celsius, boolean fahrenheit) {
        return (int) Math.round(fahrenheit ? celsius * 9 / 5 + 32 : celsius);
    }

    /** Whole miles per hour from km/h. */
    static int mph(int kmh) {
        return (int) Math.round(kmh / 1.609344);
    }

    // ---- words and colours -------------------------------------------------------------------------

    /** The words for a WMO weather code, as fine as the code goes ("Light snow", not "Snow"). */
    static int sky(int code, boolean day) {
        switch (code) {
            case 0: return day ? R.string.sky_sunny : R.string.sky_clear;
            case 1: return day ? R.string.sky_mostly_sunny : R.string.sky_mostly_clear;
            case 2: return R.string.sky_partly_cloudy;
            case 3: return R.string.sky_cloudy;
            case 45: case 48: return R.string.sky_fog;
            case 51: case 53: case 55: return R.string.sky_drizzle;
            case 56: case 57: return R.string.sky_freezing_drizzle;
            case 61: return R.string.sky_light_rain;
            case 63: return R.string.sky_rain;
            case 65: return R.string.sky_heavy_rain;
            case 66: case 67: return R.string.sky_freezing_rain;
            case 71: return R.string.sky_light_snow;
            case 73: return R.string.sky_snow;
            case 75: return R.string.sky_heavy_snow;
            case 77: return R.string.sky_snow_grains;
            case 80: case 81: case 82: return R.string.sky_showers;
            case 85: case 86: return R.string.sky_snow_showers;
            default: return code >= 95 ? R.string.sky_thunderstorm : R.string.sky_cloudy;
        }
    }

    /** US AQI bands: upper bound, words, pill colour. */
    private static final int[] AQI_MAX = {50, 100, 150, 200, 300, Integer.MAX_VALUE};
    private static final int[] AQI_LABEL = {R.string.aqi_good, R.string.aqi_moderate, R.string.aqi_sensitive,
            R.string.aqi_unhealthy, R.string.aqi_very_unhealthy, R.string.aqi_hazardous};
    private static final int[] AQI_COLOUR = {0xE643A047, 0xE6FBC02D, 0xE6FB8C00, 0xE6E53935, 0xE68E24AA, 0xE67E0023};

    static int aqiBand(int aqi) {
        int band = 0;
        while (aqi > AQI_MAX[band]) band++;
        return band;
    }

    static int aqiLabel(int aqi) {
        return AQI_LABEL[aqiBand(aqi)];
    }

    static int aqiColour(int aqi) {
        return AQI_COLOUR[aqiBand(aqi)];
    }

    /** Dark text on the yellow band, white on the others. */
    static int aqiTextColour(int aqi) {
        return aqiBand(aqi) == 1 ? 0xFF1A1A1A : 0xFFFFFFFF;
    }

    /** A full-screen gradient in the weather's colours, dark at the bottom. */
    static Drawable backdrop(int code, boolean day) {
        int[] colours;
        if (code <= 1) {
            colours = day ? new int[]{0xFF2A6AA8, 0xFF0A1626} : new int[]{0xFF0D1B3E, 0xFF020308};
        } else if (code == 45 || code == 48) {
            colours = new int[]{0xFF5A6068, 0xFF101113};
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            colours = new int[]{0xFF6F8497, 0xFF101820};
        } else if (code >= 95) {
            colours = new int[]{0xFF2D2140, 0xFF07050C};
        } else if (code >= 51) {
            colours = new int[]{0xFF1E2C3D, 0xFF06090E};
        } else {
            colours = new int[]{0xFF3B4654, 0xFF0C0F14};
        }
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colours);
    }
}
