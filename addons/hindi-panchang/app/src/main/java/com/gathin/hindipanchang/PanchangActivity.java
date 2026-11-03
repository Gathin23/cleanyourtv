package com.gathin.hindipanchang;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen TV Activity for Hindi Panchang (विक्रम संवत).
 * Handles:
 * - Back navigation from Detail Views back to the Centered Calendar Home View
 * - City Picker Dialog (Auto-Detect IP location, Online Geocoding Search, and Offline Presets)
 * - Remote shortcut keys (CH+/CH- for month navigation, MENU/INFO for city picker).
 */
public final class PanchangActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private PanchangPanel panel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        panel = new PanchangPanel(this, false);
        panel.setHostCallbacks(this::showCityPickerMenu);
        setContentView(panel);

        // If the user hasn't picked a city yet, silently auto-detect their location in the background
        if (!LocationStore.hasSavedPlace(this)) {
            autoDetectLocation(false);
        }
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
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
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
        LocationStore.Place[] presets = LocationStore.PRESETS;
        CharSequence[] items = new CharSequence[presets.length + 2];
        items[0] = "📡 वर्तमान स्थान स्वतः खोजें (Auto-Detect Current Location)";
        items[1] = "🔍 शहर का नाम टाइप करके खोजें (Search Any City Online...)";
        for (int i = 0; i < presets.length; i++) {
            items[i + 2] = "📍 " + presets[i].name + " — " + presets[i].detail;
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("शहर / स्थान चुनें (Select City for Accurate Sunrise & Muhurat)")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        autoDetectLocation(true);
                    } else if (which == 1) {
                        showSearchCityInput();
                    } else {
                        LocationStore.Place chosen = presets[which - 2];
                        LocationStore.save(this, chosen);
                        panel.updatePlace(chosen);
                        Toast.makeText(this, "स्थान सेट किया गया: " + chosen.displayLabel(), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showSearchCityInput() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("शहर का नाम लिखें (e.g. Varanasi, Jaipur, Chicago, London)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🔍 शहर खोजें (Search City)")
                .setView(box)
                .setPositiveButton("खोजें (Search)", (d, w) -> runCitySearch(input.getText().toString()))
                .setNegativeButton("रद्द करें (Cancel)", null)
                .create();

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            dialog.dismiss();
            runCitySearch(input.getText().toString());
            return true;
        });
        dialog.show();
        input.requestFocus();
    }

    private void runCitySearch(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return;
        Toast.makeText(this, "खोज रहे हैं: " + q + "...", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            List<LocationStore.Place> found;
            try {
                found = LocationStore.searchOnline(q);
            } catch (Exception e) {
                found = null;
            }
            List<LocationStore.Place> results = found;
            main.post(() -> {
                if (isDestroyed()) return;
                if (results == null) {
                    Toast.makeText(this, "इंटरनेट उपलब्ध नहीं है — कृपया सूची से शहर चुनें (Offline)", Toast.LENGTH_LONG).show();
                } else if (results.isEmpty()) {
                    Toast.makeText(this, "कोई शहर नहीं मिला: " + q, Toast.LENGTH_LONG).show();
                } else {
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
            });
        });
    }

    private void autoDetectLocation(boolean showToast) {
        if (showToast) {
            Toast.makeText(this, "वर्तमान स्थान खोजा जा रहा है (Detecting location)...", Toast.LENGTH_SHORT).show();
        }
        io.execute(() -> {
            LocationStore.Place detected;
            try {
                detected = LocationStore.autoDetectOnline();
            } catch (Exception e) {
                detected = null;
            }
            LocationStore.Place result = detected;
            main.post(() -> {
                if (isDestroyed()) return;
                if (result != null) {
                    LocationStore.save(this, result);
                    panel.updatePlace(result);
                    if (showToast) {
                        Toast.makeText(this, "स्थान मिला: " + result.displayLabel(), Toast.LENGTH_SHORT).show();
                    }
                } else if (showToast) {
                    Toast.makeText(this, "स्वतः स्थान नहीं मिल सका — कृपया शहर चुनें", Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
