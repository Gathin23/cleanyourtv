package com.gathin.photodream;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The options, one switch each, for the remote's D-pad. Reached from the TV's screensaver settings (the
 * screensaver's own settings entry) and by holding OK or pressing Menu in the card's slideshow. All off: the plain slideshow.
 */
public class SettingsActivity extends Activity {

    private static final int ASK_LOCATION = 1;

    private SharedPreferences preferences;
    private Switch weatherSwitch;
    private Switch skySwitch;
    private TextView weatherStatus;
    private Button locationButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = Options.preferences(this);
        int pad = dp(48);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad * 2, pad, pad * 2, pad);

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setPadding(0, 0, 0, dp(16));
        list.addView(title);

        list.addView(toggle(Options.CLOCK, R.string.option_clock, null));
        list.addView(toggle(Options.DATES, R.string.option_dates, null));
        list.addView(toggle(Options.PRINTS, R.string.option_prints, on -> skySwitch.setEnabled(on)));
        skySwitch = toggle(Options.SKY, R.string.option_sky, null);
        skySwitch.setEnabled(preferences.getBoolean(Options.PRINTS, false));
        list.addView(skySwitch);
        weatherSwitch = toggle(Options.WEATHER, R.string.option_weather, this::weatherChanged);
        list.addView(weatherSwitch);

        weatherStatus = new TextView(this);
        weatherStatus.setPadding(dp(8), 0, 0, dp(8));
        list.addView(weatherStatus);
        locationButton = new Button(this);
        locationButton.setText(R.string.open_location_settings);
        locationButton.setOnClickListener(v -> openLocationSettings());
        list.addView(locationButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button preview = new Button(this);
        preview.setText(R.string.preview);
        preview.setOnClickListener(v -> startActivity(new Intent(this, SlideshowActivity.class)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        list.addView(preview, lp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        setContentView(scroll);
        list.getChildAt(1).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWeatherStatus();   // back from the TV's location settings
    }

    private interface Changed {
        void on(boolean value);
    }

    private Switch toggle(String key, int label, Changed changed) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        s.setPadding(dp(8), dp(12), dp(8), dp(12));
        s.setChecked(preferences.getBoolean(key, false));
        s.setOnCheckedChangeListener((button, value) -> {
            preferences.edit().putBoolean(key, value).apply();
            if (changed != null) changed.on(value);
        });
        return s;
    }

    private void weatherChanged(boolean on) {
        if (on && !Weather.permitted(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ASK_LOCATION);
        }
        updateWeatherStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != ASK_LOCATION) return;
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            weatherSwitch.setChecked(false);   // no location, no weather
        }
        updateWeatherStatus();
    }

    private void updateWeatherStatus() {
        boolean on = preferences.getBoolean(Options.WEATHER, false);
        boolean locationOff = on && Weather.permitted(this) && !Weather.locationOn(this);
        int status = !on ? R.string.weather_info
                : !Weather.permitted(this) ? R.string.weather_no_permission
                : locationOff ? R.string.weather_location_off
                : R.string.weather_ok;
        weatherStatus.setText(status);
        locationButton.setVisibility(locationOff ? View.VISIBLE : View.GONE);
    }

    private void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
