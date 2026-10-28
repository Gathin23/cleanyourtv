package com.lionlev.tv.jewishcalendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** A standalone module card; it never writes the launcher's configuration. */
public final class CalendarActivity extends Activity {
    private CalendarPanel panel;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        panel = new CalendarPanel(this, false);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button settings = new Button(this);
        settings.setText(R.string.settings);
        settings.setOnClickListener(v -> configure());
        actions.addView(settings);
        Button screensaver = new Button(this);
        screensaver.setText(R.string.screensaver);
        screensaver.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); }
            catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.screensaver_missing, Toast.LENGTH_LONG).show();
            }
        });
        actions.addView(screensaver);
        panel.addView(actions);
        setContentView(panel);
        settings.requestFocus();
    }

    @Override protected void onResume() { super.onResume(); panel.start(); }
    @Override protected void onPause() { panel.stop(); super.onPause(); }

    private void configure() {
        SharedPreferences prefs = getSharedPreferences("calendar", Context.MODE_PRIVATE);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        fields.setPadding(panel.dp(24), panel.dp(8), panel.dp(24), panel.dp(8));
        TextView description = new TextView(this);
        description.setText(R.string.location_note);
        fields.addView(description);
        EditText latitude = coordinate(fields, R.string.latitude, prefs.getString("latitude", ""));
        EditText longitude = coordinate(fields, R.string.longitude, prefs.getString("longitude", ""));
        TextView regionLabel = new TextView(this);
        regionLabel.setText(R.string.region);
        fields.addView(regionLabel);
        Spinner region = new Spinner(this);
        region.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.auto_region), getString(R.string.israel), getString(R.string.diaspora)}));
        String current = prefs.getString("region", "auto");
        region.setSelection("israel".equals(current) ? 1 : "diaspora".equals(current) ? 2 : 0);
        fields.addView(region);
        CheckBox greetings = new CheckBox(this);
        greetings.setText(R.string.greetings);
        greetings.setChecked(prefs.getBoolean("greetings", true));
        fields.addView(greetings);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.settings).setView(scroll)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String lat = latitude.getText().toString().trim(), lon = longitude.getText().toString().trim();
            if (!validLocationInput(lat, lon)) {
                latitude.setError(getString(R.string.invalid_location));
                latitude.requestFocus();
                return;
            }
            prefs.edit().putString("latitude", lat).putString("longitude", lon)
                    .putString("region", new String[]{"auto", "israel", "diaspora"}[region.getSelectedItemPosition()])
                    .putBoolean("greetings", greetings.isChecked()).apply();
            dialog.dismiss();
            panel.start();
        }));
        dialog.show();
    }

    static boolean validLocationInput(String latitude, String longitude) {
        if (latitude.isEmpty() && longitude.isEmpty()) return true;
        try { return JewishDate.validLocation(Double.parseDouble(latitude), Double.parseDouble(longitude)); }
        catch (NumberFormatException e) { return false; }
    }

    private EditText coordinate(LinearLayout parent, int label, String value) {
        TextView title = new TextView(this);
        title.setText(label);
        parent.addView(title);
        EditText field = new EditText(this);
        field.setId(View.generateViewId());
        field.setContentDescription(getString(label));
        field.setTextDirection(View.TEXT_DIRECTION_LTR);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        field.setSingleLine(true);
        field.setText(value);
        parent.addView(field);
        title.setLabelFor(field.getId());
        return field;
    }
}
