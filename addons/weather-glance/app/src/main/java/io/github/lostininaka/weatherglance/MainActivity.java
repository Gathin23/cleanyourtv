package io.github.lostininaka.weatherglance;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pick a city, and show the line Home will display under its clock. There is nothing else to configure: the
 * launcher reads the provider, this screen only writes what the provider publishes.
 */
public final class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Store store;
    private TextView line, status;
    private EditText city;
    private LinearLayout picker, results;
    private boolean resumed;

    /** Re-reads the stored line every second while this screen is open: a fetch lands in the background. */
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            showLine();
            if (resumed) main.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        store = new Store(this);
        line = findViewById(R.id.line);
        status = findViewById(R.id.status);
        city = findViewById(R.id.city);
        picker = findViewById(R.id.picker);
        results = findViewById(R.id.results);
        findViewById(R.id.search).setOnClickListener(v -> search());
        // The remote's keyboard search key (and a hardware Enter) runs the same search as the button.
        city.setOnEditorActionListener((v, actionId, event) -> {
            search();
            return true;
        });
        findViewById(R.id.change).setOnClickListener(v -> showPicker(true));
        findViewById(R.id.refresh).setOnClickListener(v -> {
            status.setText(R.string.refreshing);
            Refresh.now(this);
        });
        showPicker(!store.hasPlace());
        showLine();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (store.hasPlace()) Refresh.now(this);
        main.post(poll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        main.removeCallbacks(poll);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    /** The stored line, or what to do about not having one. */
    private void showLine() {
        String text = store.text();
        if (text.isEmpty()) {
            line.setText(store.hasPlace() ? R.string.waiting : R.string.no_city);
            status.setText(store.hasPlace() ? R.string.waiting_hint : R.string.no_city_hint);
            return;
        }
        line.setText(text);
        long age = Math.max(0L, (System.currentTimeMillis() - store.updated()) / 60_000L);
        status.setText(getString(R.string.line_hint, store.place(), age));
    }

    private void showPicker(boolean visible) {
        picker.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            city.requestFocus();
            results.removeAllViews();
        }
    }

    /** The place search, off the main thread; the answers become buttons. */
    private void search() {
        String query = city.getText().toString().trim();
        if (query.isEmpty()) {
            status.setText(R.string.need_name);
            return;
        }
        status.setText(R.string.searching);
        io.execute(() -> {
            List<OpenMeteo.Place> found;
            try {
                found = OpenMeteo.search(query);
            } catch (IOException | RuntimeException failed) {
                main.post(() -> status.setText(R.string.offline));
                return;
            }
            main.post(() -> showResults(found));
        });
    }

    private void showResults(List<OpenMeteo.Place> found) {
        results.removeAllViews();
        if (found.isEmpty()) {
            status.setText(R.string.no_match);
            return;
        }
        status.setText(R.string.pick_place);
        for (OpenMeteo.Place place : found) {
            if (Double.isNaN(place.lat) || Double.isNaN(place.lon)) continue;
            Button b = new Button(this);
            b.setText(place.label());
            b.setAllCaps(false);
            // A TV is driven by a remote, not a finger: the rows have to take focus, or the only way to pick
            // a city is a touchscreen the set does not have.
            b.setFocusable(true);
            b.setClickable(true);
            b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            b.setOnClickListener(v -> choose(place));
            results.addView(b);
        }
        // Put the remote's focus on the first place and get the keyboard out of the way: a soft keyboard on
        // screen takes every d-pad press, so the list below it could not be reached at all.
        final View first = results.getChildCount() > 0 ? results.getChildAt(0) : null;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(city.getWindowToken(), 0);
        city.clearFocus();
        if (first != null) results.post(() -> first.requestFocus());
    }

    private void choose(OpenMeteo.Place place) {
        store.savePlace(place.label(), place.lat, place.lon);
        results.removeAllViews();
        showPicker(false);
        line.setText(R.string.waiting);
        status.setText(R.string.refreshing);
        Refresh.now(this);
    }
}
