package io.github.sumyabazar1.weather;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The weather for one place, full screen: now, the air quality and the next days (options.xml). It reads Open-Meteo only
 * while it is on screen (every {@link Forecast#REFRESH_MS}, or {@link Forecast#RETRY_MS} after a failure) and
 * does nothing in the background. The place is chosen once with a search and kept in the app's preferences.
 */
public final class WeatherActivity extends Activity {
    private static final String PREFS = "place";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::refresh;
    private SharedPreferences place;
    private SharedPreferences options;
    private View root;
    private View content;
    private TextView message;
    private boolean shown;
    private boolean resumed;
    private int requestId;
    private int searchId; // City searches are independent of forecast refreshes.
    private Forecast.Report lastReport;
    private Date updatedAt;
    /** Saved choices, falling back to options.xml until the owner changes them. */
    private int forecastDays;
    private boolean showHumidity;
    private boolean showWind;
    /** Degrees Fahrenheit (and mph), and the other unit on the "Feels like" line. */
    private boolean fahrenheit;
    private boolean bothUnits;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_weather);
        place = getSharedPreferences(PREFS, MODE_PRIVATE);
        options = getSharedPreferences("options", MODE_PRIVATE);
        readOptions();
        root = findViewById(R.id.root);
        content = findViewById(R.id.content);
        message = findViewById(R.id.message);
        View changeCity = findViewById(R.id.changeCity);
        changeCity.setOnClickListener(v -> pickCity());
        findViewById(R.id.weatherSettings).setOnClickListener(v -> settings());
        changeCity.requestFocus();
        if (!place.contains("name")) {
            message.setText(R.string.pick_first);
            pickCity();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (place.contains("name")) refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        requestId++;
        searchId++;
        main.removeCallbacks(tick);   // nothing runs while the page is not on screen
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void refresh() {
        main.removeCallbacks(tick);
        if (!resumed || !place.contains("name")) return;
        int request = ++requestId;
        int requestedDays = forecastDays;
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        io.execute(() -> {
            Forecast.Report report;
            try {
                report = Forecast.fetch(lat, lon, requestedDays);
            } catch (Exception e) {
                report = null;
            }
            Forecast.Report result = report;
            main.post(() -> {
                if (isDestroyed() || !resumed || request != requestId) return;
                if (result != null) {
                    lastReport = result;
                    updatedAt = new Date();
                    show(result);
                }
                else if (!shown) message.setText(R.string.offline);
                main.postDelayed(tick, result != null ? Forecast.REFRESH_MS : Forecast.RETRY_MS);
            });
        });
    }

    private void show(Forecast.Report r) {
        shown = true;
        root.setBackground(Forecast.backdrop(r.code, r.day));
        ((TextView) findViewById(R.id.place)).setText(place.getString("name", ""));
        ((TextView) findViewById(R.id.temp)).setText(Forecast.degrees(r.tempC, fahrenheit) + "°");
        ((TextView) findViewById(R.id.sky)).setText(Forecast.sky(r.code, r.day));
        StringBuilder feels = new StringBuilder();
        if (bothUnits) {
            feels.append(getString(fahrenheit ? R.string.other_unit_c : R.string.other_unit_f,
                    Forecast.degrees(r.tempC, !fahrenheit))).append(" · ");
        }
        feels.append(getString(R.string.feels_like, Forecast.degrees(r.feelsC, fahrenheit)));
        if (showHumidity) feels.append(" · ").append(getString(R.string.humidity, r.humidity));
        if (showWind) {
            feels.append(" · ").append(fahrenheit ? getString(R.string.wind_mph, Forecast.mph(r.windKmh))
                    : getString(R.string.wind, r.windKmh));
        }
        ((TextView) findViewById(R.id.feels)).setText(feels);

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
        LayoutInflater inflater = getLayoutInflater();
        // More than three days: the cards share the row, with short day names, so a week fits on screen.
        int count = Math.min(forecastDays, r.days.size());
        boolean compact = count > Forecast.MIN_DAYS;
        days.getLayoutParams().width = compact ? LinearLayout.LayoutParams.MATCH_PARENT
                : LinearLayout.LayoutParams.WRAP_CONTENT;
        for (int i = 0; i < count; i++) {
            Forecast.Day d = r.days.get(i);
            View card = inflater.inflate(R.layout.item_day, days, false);
            ((TextView) card.findViewById(R.id.dayName)).setText(d.date.getDayOfWeek()
                    .getDisplayName(compact ? TextStyle.SHORT : TextStyle.FULL, Locale.getDefault()));
            ((TextView) card.findViewById(R.id.daySky)).setText(Forecast.sky(d.code, true));
            TextView temps = card.findViewById(R.id.dayTemps);
            temps.setText(getString(R.string.high_low, Forecast.degrees(d.maxC, fahrenheit),
                    Forecast.degrees(d.minC, fahrenheit)));
            if (compact) {
                int pad = Math.round(14 * getResources().getDisplayMetrics().density);
                card.setPadding(pad, pad, pad, pad);
                TextView sky = card.findViewById(R.id.daySky);
                sky.setTextSize(15);
                sky.setSingleLine(false);   // "Partly cloudy" on two lines rather than cut; every card two lines tall
                sky.setLines(2);
                temps.setTextSize(21);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginEnd(i < count - 1 ? Math.round(12 * getResources().getDisplayMetrics().density) : 0);
                card.setLayoutParams(lp);
            }
            days.addView(card);
        }

        ((TextView) findViewById(R.id.updated))
                .setText(getString(R.string.updated, DateFormat.getTimeFormat(this).format(updatedAt)));
        message.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
    }

    private void readOptions() {
        forecastDays = Forecast.forecastDays(options.getInt("forecast_days",
                getResources().getInteger(R.integer.forecast_days)));
        showHumidity = options.getBoolean("show_humidity", getResources().getBoolean(R.bool.show_humidity));
        showWind = options.getBoolean("show_wind", getResources().getBoolean(R.bool.show_wind));
        fahrenheit = options.getBoolean("fahrenheit", "f".equalsIgnoreCase(getString(R.string.temperature_unit).trim()));
        bothUnits = options.getBoolean("show_both_units", getResources().getBoolean(R.bool.show_both_units));
    }

    private String optionRow(int label, boolean enabled) {
        return getString(R.string.setting_value, getString(label), getString(enabled ? R.string.option_on : R.string.option_off));
    }

    private String[] optionRows() {
        return new String[] {
                getString(R.string.setting_value, getString(R.string.setting_units),
                        getString(fahrenheit ? R.string.unit_f : R.string.unit_c)),
                optionRow(R.string.setting_both_units, bothUnits),
                getString(R.string.setting_value, getString(R.string.setting_days),
                        dayCount(forecastDays)),
                optionRow(R.string.setting_humidity, showHumidity),
                optionRow(R.string.setting_wind, showWind),
                getString(R.string.setting_reset)
        };
    }

    private String dayCount(int days) {
        return getResources().getQuantityString(R.plurals.day_count, days, days);
    }

    /** Native lists keep D-pad focus, checked values and Back handling without a new dependency. */
    private void settings() {
        int initialDays = forecastDays;
        // The array overload uses a fixed-size list; saved changes must be able to replace its rows.
        ArrayAdapter<String> rows = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        rows.addAll(optionRows());
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.weather_settings)
                .setAdapter(rows, null)
                .setNegativeButton(R.string.settings_done, null)
                .create();
        Runnable changed = () -> {
            readOptions();
            rows.setNotifyOnChange(false);
            rows.clear();
            rows.addAll(optionRows());
            rows.notifyDataSetChanged();
            if (lastReport != null) show(lastReport); // Display choices do not make another network request.
        };
        dialog.setOnDismissListener(d -> {
            if (forecastDays != initialDays) refresh(); // One fetch after all choices, including Back.
        });
        dialog.show();
        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(R.string.setting_units)
                        .setSingleChoiceItems(new String[] {getString(R.string.unit_c), getString(R.string.unit_f)},
                                fahrenheit ? 1 : 0, (picker, which) -> {
                                    options.edit().putBoolean("fahrenheit", which == 1).apply();
                                    changed.run();
                                    picker.dismiss();
                                })
                        .setNegativeButton(android.R.string.cancel, null).show();
            } else if (position == 2) {
                String[] days = new String[Forecast.MAX_DAYS - Forecast.MIN_DAYS + 1];
                for (int i = 0; i < days.length; i++) days[i] = dayCount(i + Forecast.MIN_DAYS);
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(R.string.setting_days)
                        .setSingleChoiceItems(days, forecastDays - Forecast.MIN_DAYS, (picker, which) -> {
                            options.edit().putInt("forecast_days", which + Forecast.MIN_DAYS).apply();
                            changed.run();
                            picker.dismiss();
                        })
                        .setNegativeButton(android.R.string.cancel, null).show();
            } else {
                if (position == 1) options.edit().putBoolean("show_both_units", !bothUnits).apply();
                if (position == 3) options.edit().putBoolean("show_humidity", !showHumidity).apply();
                if (position == 4) options.edit().putBoolean("show_wind", !showWind).apply();
                if (position == 5) options.edit().clear().apply(); // Never clears the chosen city.
                changed.run();
            }
        });
        dialog.getListView().requestFocus();
        dialog.getListView().setSelection(0);
    }

    /** Type a city, pick it from the matches, keep it. Back leaves the current place as it was. */
    private void pickCity() {
        searchId++;
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint(R.string.pick_hint);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(name, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_title)
                .setView(box)
                .setPositiveButton(R.string.pick_search, (d, w) -> search(name.getText().toString()))
                .create();
        name.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            dialog.dismiss();
            search(name.getText().toString());
            return true;
        });
        dialog.show();
        name.requestFocus();
    }

    private void search(String typed) {
        if (!resumed) return;
        int search = ++searchId;
        if (typed.trim().isEmpty()) {
            pickCity();
            return;
        }
        io.execute(() -> {
            List<Forecast.Place> found;
            try {
                found = Forecast.search(typed);
            } catch (Exception e) {
                found = null;
            }
            List<Forecast.Place> places = found;
            main.post(() -> {
                if (isDestroyed() || !resumed || search != searchId) return;
                if (places == null) {
                    Toast.makeText(this, R.string.offline, Toast.LENGTH_LONG).show();
                } else if (places.isEmpty()) {
                    Toast.makeText(this, getString(R.string.pick_none, typed.trim()), Toast.LENGTH_LONG).show();
                    pickCity();
                } else {
                    choose(places);
                }
            });
        });
    }

    private void choose(List<Forecast.Place> places) {
        CharSequence[] rows = new CharSequence[places.size()];
        for (int i = 0; i < rows.length; i++) {
            Forecast.Place p = places.get(i);
            rows[i] = p.detail.isEmpty() ? p.name : p.name + " · " + p.detail;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_title)
                .setItems(rows, (d, which) -> {
                    Forecast.Place p = places.get(which);
                    place.edit()
                            .putString("name", p.name)
                            .putLong("lat", Double.doubleToRawLongBits(p.latitude))
                            .putLong("lon", Double.doubleToRawLongBits(p.longitude))
                            .apply();
                    lastReport = null;
                    shown = false;
                    content.setVisibility(View.INVISIBLE);
                    message.setVisibility(View.VISIBLE);
                    message.setText(R.string.loading);
                    refresh();
                })
                .show();
    }
}
