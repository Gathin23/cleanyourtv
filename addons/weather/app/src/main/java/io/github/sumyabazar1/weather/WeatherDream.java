package io.github.sumyabazar1.weather;

import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The optional screensaver: one frosted card with the time, the day and date, the weather now and the next three
 * days for the city chosen in {@link WeatherActivity}, on a gradient in the weather's colours. Android starts it only
 * when the owner picks it under Settings, Screensaver. It reads Open-Meteo only while it is on screen (every
 * {@link Forecast#REFRESH_MS}, or {@link Forecast#RETRY_MS} after a failure), and the card drifts a little each minute
 * so no pixel holds one bright edge for hours.
 */
public final class WeatherDream extends DreamService {
    private static final int DAYS = Forecast.MIN_DAYS;
    private static final int DRIFT_DP = 28;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable minute = this::tick;
    private final Runnable refresh = this::refresh;
    private final Random random = new Random();
    private SharedPreferences place;
    private View root;
    private View card;
    private TextView time;
    private TextView date;
    private boolean dreaming;
    private int requestId;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_weather);
        place = getSharedPreferences("place", MODE_PRIVATE);
        root = findViewById(R.id.root);
        card = findViewById(R.id.card);
        time = findViewById(R.id.time);
        date = findViewById(R.id.date);
        ((TextView) findViewById(R.id.place)).setText(place.contains("name")
                ? place.getString("name", "") : getString(R.string.dream_no_city));
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreaming = true;
        tick();
        refresh();
    }

    @Override
    public void onDreamingStopped() {
        stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    public void onDestroy() {
        stop();
        io.shutdownNow();
        super.onDestroy();
    }

    private void stop() {
        dreaming = false;
        requestId++;
        main.removeCallbacks(minute);
        main.removeCallbacks(refresh);
    }

    /** The clock and the date, once a minute on the minute, and a slow drift of the card. */
    private void tick() {
        main.removeCallbacks(minute);
        if (!dreaming) return;
        Date now = new Date();
        time.setText(DateFormat.getTimeFormat(this).format(now));
        date.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())));
        float density = getResources().getDisplayMetrics().density;
        card.animate().translationX((random.nextFloat() * 2 - 1) * DRIFT_DP * density)
                .translationY((random.nextFloat() * 2 - 1) * DRIFT_DP * density)
                .setDuration(4_000).start();
        main.postDelayed(minute, 60_000 - now.getTime() % 60_000);
    }

    private void refresh() {
        main.removeCallbacks(refresh);
        if (!dreaming || !place.contains("name")) return;
        int request = ++requestId;
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        io.execute(() -> {
            Forecast.Report report;
            try {
                report = Forecast.fetch(lat, lon, DAYS);
            } catch (Exception e) {
                report = null;
            }
            Forecast.Report result = report;
            main.post(() -> {
                if (!dreaming || request != requestId) return;
                if (result != null) show(result);
                main.postDelayed(refresh, result != null ? Forecast.REFRESH_MS : Forecast.RETRY_MS);
            });
        });
    }

    private void show(Forecast.Report r) {
        SharedPreferences options = getSharedPreferences("options", MODE_PRIVATE);
        boolean fahrenheit = options.getBoolean("fahrenheit",
                "f".equalsIgnoreCase(getString(R.string.temperature_unit).trim()));
        root.setBackground(Forecast.backdrop(r.code, r.day));
        ((TextView) findViewById(R.id.temp)).setText(Forecast.degrees(r.tempC, fahrenheit) + "°");
        ((TextView) findViewById(R.id.sky)).setText(Forecast.sky(r.code, r.day));
        ((TextView) findViewById(R.id.feels)).setText(getString(R.string.feels_like, Forecast.degrees(r.feelsC, fahrenheit)));

        TextView air = findViewById(R.id.airQuality);
        if (r.aqi >= 0) {
            air.setText(getString(R.string.aqi_line, r.aqi, getString(Forecast.aqiLabel(r.aqi))));
            air.setTextColor(Forecast.aqiTextColour(r.aqi));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Forecast.aqiColour(r.aqi));
            pill.setCornerRadius(100 * getResources().getDisplayMetrics().density);
            air.setBackground(pill);
            air.setVisibility(View.VISIBLE);
        } else {
            air.setVisibility(View.GONE);
        }

        LinearLayout days = findViewById(R.id.days);
        days.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int count = Math.min(DAYS, r.days.size());
        int gap = Math.round(12 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < count; i++) {
            Forecast.Day d = r.days.get(i);
            View day = inflater.inflate(R.layout.item_day, days, false);
            ((TextView) day.findViewById(R.id.dayName)).setText(d.date.getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, Locale.getDefault()));
            ((TextView) day.findViewById(R.id.daySky)).setText(Forecast.sky(d.code, true));
            ((TextView) day.findViewById(R.id.dayTemps)).setText(getString(R.string.high_low,
                    Forecast.degrees(d.maxC, fahrenheit), Forecast.degrees(d.minC, fahrenheit)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(i < count - 1 ? gap : 0);
            day.setLayoutParams(lp);
            days.addView(day);
        }
    }
}
