package com.gathin.hindipanchang;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen TV Activity for Hindi Panchang (विक्रम संवत).
 * Handles:
 * - Back navigation from Detail Views back to the Centered Calendar Home View
 * - Offline City Picker Dialog (India Cities, US Cities, Other World Cities, Search, Manual Lat/Lon)
 * - Remote shortcut keys (CH+/CH- for month navigation, MENU/INFO for city picker).
 */
public final class PanchangActivity extends Activity {
    private PanchangPanel panel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        panel = new PanchangPanel(this, false);
        panel.setHostCallbacks(this::showCityPickerMenu);
        setContentView(panel);
    }

    @Override
    protected void onStart() {
        super.onStart();
        panel.start();
    }

    @Override
    protected void onStop() {
        panel.stop();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (panel != null && panel.handleBackPress()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (panel != null && panel.handleBackPress()) {
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            panel.shiftMonth(1);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            panel.shiftMonth(-1);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            showCityPickerMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showCityPickerMenu() {
        CharSequence[] categories = new CharSequence[]{
                "🇮🇳 भारत के शहर (India — " + LocationStore.INDIA_PRESETS.length + " Cities)",
                "🇺🇸 अमेरिका के शहर (United States — " + LocationStore.US_PRESETS.length + " Cities)",
                "🌍 अन्य अंतर्राष्ट्रीय शहर (Other World Cities)",
                "🔍 शहर खोजें (Search All Bundled Cities...)",
                "🧭 अन्य स्थान: अक्षांश / देशांतर दर्ज करें (Custom City Label + Lat/Lon...)"
        };

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("शहर / स्थान चुनें (Select City for Accurate Sunrise & Muhurat)")
                .setItems(categories, (d, which) -> {
                    if (which == 0) {
                        showPlaceList("🇮🇳 भारत के शहर (India)", LocationStore.INDIA_PRESETS);
                    } else if (which == 1) {
                        showPlaceList("🇺🇸 अमेरिका के शहर (United States)", LocationStore.US_PRESETS);
                    } else if (which == 2) {
                        showPlaceList("🌍 अन्य अंतर्राष्ट्रीय शहर (World)", LocationStore.OTHER_PRESETS);
                    } else if (which == 3) {
                        showSearchCityInput();
                    } else {
                        showManualCoordinatesInput();
                    }
                })
                .show();
    }

    private void showPlaceList(String title, LocationStore.Place[] places) {
        CharSequence[] items = new CharSequence[places.length];
        for (int i = 0; i < places.length; i++) {
            items[i] = "📍 " + places[i].name + " — " + places[i].detail;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setItems(items, (d, which) -> {
                    LocationStore.Place chosen = places[which];
                    LocationStore.save(this, chosen);
                    panel.updatePlace(chosen);
                    Toast.makeText(this, "स्थान सेट किया गया: " + chosen.displayLabel(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("वापस (Back)", (d, w) -> showCityPickerMenu())
                .show();
    }

    private void showSearchCityInput() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("शहर या राज्य लिखें (e.g. Varanasi, Chicago, Texas, London)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🔍 शहर खोजें (Filter Bundled Cities)")
                .setView(box)
                .setPositiveButton("खोजें (Filter)", (d, w) -> runOfflineCitySearch(input.getText().toString()))
                .setNegativeButton("रद्द करें (Cancel)", null)
                .create();

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            dialog.dismiss();
            runOfflineCitySearch(input.getText().toString());
            return true;
        });
        dialog.show();
        input.requestFocus();
    }

    private void runOfflineCitySearch(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return;
        List<LocationStore.Place> results = LocationStore.searchOffline(q);
        if (results.isEmpty()) {
            Toast.makeText(this, "सूची में नहीं मिला: " + q + " — अक्षांश/देशांतर का उपयोग करें", Toast.LENGTH_LONG).show();
            return;
        }
        CharSequence[] rows = new CharSequence[results.size()];
        for (int i = 0; i < rows.length; i++) {
            LocationStore.Place p = results.get(i);
            rows[i] = p.displayLabel() + " (" + p.timezone + ")";
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("शहर चुनें (Select Match)")
                .setItems(rows, (d, which) -> {
                    LocationStore.Place p = results.get(which);
                    LocationStore.save(this, p);
                    panel.updatePlace(p);
                    Toast.makeText(this, "स्थान सेट किया गया: " + p.displayLabel(), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showManualCoordinatesInput() {
        LocationStore.Place current = LocationStore.load(this);

        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("शहर / स्थान का नाम (Custom City Label, e.g. My City)");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        EditText latInput = new EditText(this);
        latInput.setSingleLine(true);
        latInput.setHint("अक्षांश / Latitude (-90 to 90, e.g. 25.3176)");
        latInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        latInput.setText(String.format(Locale.US, "%.4f", current.latitude));

        EditText lonInput = new EditText(this);
        lonInput.setSingleLine(true);
        lonInput.setHint("देशांतर / Longitude (-180 to 180, e.g. 82.9739)");
        lonInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        lonInput.setText(String.format(Locale.US, "%.4f", current.longitude));

        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        box.addView(nameInput, lp);
        box.addView(latInput, lp);
        box.addView(lonInput, lp);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🧭 अन्य स्थान (Custom City Label + Lat/Lon)")
                .setView(box)
                .setPositiveButton("सहेजें (Save)", (d, w) -> {
                    double lat;
                    double lon;
                    try {
                        lat = Double.parseDouble(latInput.getText().toString().trim());
                        lon = Double.parseDouble(lonInput.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "अमान्य संख्या — कृपया सही अक्षांश/देशांतर लिखें", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (Double.isNaN(lat) || Double.isNaN(lon)
                            || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                        Toast.makeText(this, "अक्षांश (-90..90) और देशांतर (-180..180) के भीतर होना चाहिए", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String customName = nameInput.getText().toString().trim();
                    if (customName.isEmpty()) {
                        customName = String.format(Locale.US, "%.2f°, %.2f°", lat, lon);
                    }
                    String tz = ZoneId.systemDefault().getId();
                    LocationStore.Place custom = new LocationStore.Place(
                            customName,
                            String.format(Locale.US, "%.4f, %.4f", lat, lon),
                            lat,
                            lon,
                            tz
                    );
                    LocationStore.save(this, custom);
                    panel.updatePlace(custom);
                    Toast.makeText(this, "स्थान सेट किया गया: " + custom.displayLabel(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("रद्द करें (Cancel)", null)
                .show();
        nameInput.requestFocus();
    }
}
