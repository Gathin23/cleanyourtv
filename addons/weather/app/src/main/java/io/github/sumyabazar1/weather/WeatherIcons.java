package io.github.sumyabazar1.weather;

import android.content.Context;
import android.graphics.Color;
import android.util.Base64;
import android.view.View;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** One passive, offline view for the bundled SVGs, including the current conditions animation. */
final class WeatherIcons extends WebView {
    private final Map<String, String> images = new HashMap<>();
    private final int[] location = new int[2];
    private String lastMarkup = "";

    WeatherIcons(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        getSettings().setBlockNetworkLoads(true);
        getSettings().setAllowFileAccess(false);
        getSettings().setAllowContentAccess(false);
    }

    static String symbol(int code, boolean day) {
        switch (code) {
            case -1: return null;
            case 0: return day ? "clear-day" : "clear-night";
            case 1: return day ? "cloudy-1-day" : "cloudy-1-night";
            case 2: return day ? "cloudy-2-day" : "cloudy-2-night";
            case 45: case 48: return "fog";
            case 51: case 53: case 61: case 80: return "rainy-1";
            case 55: case 63: case 81: return "rainy-2";
            case 65: case 82: return "rainy-3";
            case 56: case 57: case 66: case 67: return "rain-and-sleet-mix";
            case 71: case 77: case 85: return "snowy-1";
            case 73: return "snowy-2";
            case 75: case 86: return "snowy-3";
            case 95: return "thunderstorms";
            case 96: case 97: case 99: return "severe-thunderstorm";
            default: return "cloudy";
        }
    }

    void render(String current, View summary, TemperatureChart hourly, TemperatureChart daily) {
        if (getWidth() == 0 || getHeight() == 0 || summary.getWidth() == 0) return;
        getLocationInWindow(location);
        int left = location[0];
        int top = location[1];

        StringBuilder markup = new StringBuilder();
        summary.getLocationInWindow(location);
        append(markup, current, true, location[0] - left + summary.getWidth() / 2f,
                location[1] - top + summary.getHeight() / 2f, summary.getWidth(), summary.getHeight());
        hourly.getLocationInWindow(location);
        hourly.appendIcons(this, markup, location[0] - left, location[1] - top);
        daily.getLocationInWindow(location);
        daily.appendIcons(this, markup, location[0] - left, location[1] - top);

        String contents = markup.toString();
        if (contents.equals(lastMarkup)) return;
        lastMarkup = contents;
        String html = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; "
                + "img-src data:; style-src 'unsafe-inline'\">"
                + "<style>html,body{margin:0;overflow:hidden}img{position:absolute;object-fit:contain;"
                + "transform:translate(-50%,-50%)}</style>" + contents;
        loadData(Base64.encodeToString(html.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), "text/html", "base64");
    }

    void append(StringBuilder markup, String icon, boolean animated, float x, float y, float width, float height) {
        if (icon == null) return;
        String path = "weather-icons/" + (animated ? "animated/" : "static/") + icon + ".svg";
        String image = images.computeIfAbsent(path, this::readImage);
        if (image.isEmpty()) return;
        markup.append("<img alt='' style='left:").append(100 * x / getWidth())
                .append("%;top:").append(100 * y / getHeight())
                .append("%;width:").append(100 * width / getWidth())
                .append("%;height:").append(100 * height / getHeight())
                .append("%' src='data:image/svg+xml;base64,").append(image).append("'>");
    }

    private String readImage(String path) {
        try (InputStream stream = getContext().getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int size; (size = stream.read(buffer)) != -1; ) out.write(buffer, 0, size);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (IOException e) {
            return "";
        }
    }
}
