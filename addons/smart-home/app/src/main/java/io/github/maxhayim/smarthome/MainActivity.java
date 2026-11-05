package io.github.maxhayim.smarthome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The tile and module card open this: your Home Assistant's scenes, lights and switches, climate, sensors, doors
 * and windows, and locks, in sections. OK runs a scene or switches a light; Left and Right dim a light or move a
 * thermostat. Locks are shown, never opened. It reads Home Assistant only while this screen is open.
 */
public class MainActivity extends Activity {
    private static final long REFRESH_MS = 10_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private LinearLayout rows;
    private TextView status;
    private Config config;
    private List<Ha.Entity> entities = new ArrayList<>();
    private String problem;
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            load();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(px(48), px(32), px(48), px(24));
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF8B949E);
        status.setPadding(0, 0, 0, px(16));
        page.addView(title);
        page.addView(status);
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Config.importPushed(this);
        config = Config.load(this);
        build();
        if (config.complete()) main.post(refresh);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        net.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        Config c = config;
        net.execute(() -> {
            HaClient.Reply r = new HaClient(c.url, c.token).get("/api/states");
            List<Ha.Entity> found = null;
            String why = null;
            if (r.ok()) {
                try {
                    found = Ha.parse(r.body);
                } catch (JSONException e) {
                    why = "Home Assistant's answer couldn't be read.";
                }
            } else {
                why = Ha.problem(r.code);
            }
            List<Ha.Entity> f = found;
            String w = why;
            main.post(() -> {
                if (isFinishing()) return;
                problem = w;
                if (f != null) entities = f;
                build();
            });
        });
    }

    private void send(Ha.Call call) {
        if (call == null) return;
        Config c = config;
        net.execute(() -> {
            HaClient.Reply r = new HaClient(c.url, c.token).post(call.path, call.body);
            main.post(() -> {
                if (!r.ok()) status.setText(Ha.problem(r.code));
                main.postDelayed(this::load, 600);
            });
        });
    }

    /** Rebuilds the list; focus stays on the row it was on. */
    private void build() {
        int focused = rows.indexOfChild(rows.getFocusedChild());
        rows.removeAllViews();
        if (!config.complete()) {
            status.setText("Add your Home Assistant's address and a long-lived access token (your profile page in"
                    + " Home Assistant, under Security).");
        } else if (problem != null) {
            status.setText(problem);
        } else {
            status.setText(entities.isEmpty() ? "Reading Home Assistant…" : "Connected to " + config.url + ".");
        }
        Ha.Kind section = null;
        for (Ha.Entity e : entities) {
            if (e.kind != section) {
                section = e.kind;
                heading(Ha.section(section));
            }
            Ha.Call press = Ha.press(e);
            boolean steps = Ha.step(e, 1) != null;
            boolean bar = e.kind == Ha.Kind.TOGGLE && e.brightness >= 0;
            Row r = add(new Row(this, e.name, null, bar, press == null ? null : () -> send(Ha.press(e)),
                    steps ? d -> send(Ha.step(e, d)) : null));
            r.setValue(e.value());
            if (bar) r.setBar("on".equals(e.state) ? e.brightness / 100f : 0f);
            if (!e.available()) r.setAlpha(0.5f);
            if (e.kind == Ha.Kind.LOCK) r.setDetail("Shown only: locks aren't opened from the TV.");
        }
        heading("Connection");
        Row url = add(new Row(this, "Home Assistant address", "For example http://192.168.1.10:8123", false,
                this::editUrl, null));
        url.setValue(config.url.isEmpty() ? "Not set" : config.url);
        Row token = add(new Row(this, "Access token", "Stored on this TV only.", false, this::editToken, null));
        token.setValue(config.token.isEmpty() ? "Not set" : "Set");

        View keep = focused >= 0 && focused < rows.getChildCount() ? rows.getChildAt(focused) : null;
        if (keep != null && keep.isFocusable()) keep.requestFocus();
        else if (!config.complete()) url.requestFocus();
        else for (int i = 0; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i).isFocusable()) {
                rows.getChildAt(i).requestFocus();
                break;
            }
        }
    }

    private void editUrl() {
        edit("Home Assistant address", config.url.isEmpty() ? "http://" : config.url,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, v -> {
                    String u = Ha.normalizeUrl(v);
                    if (!u.isEmpty() && !Ha.validUrl(u)) {
                        status.setText("That doesn't look like an address: " + u);
                        return;
                    }
                    Config.saveUrl(this, u);
                    reload();
                });
    }

    private void editToken() {
        edit("Access token", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, v -> {
            if (v.trim().isEmpty()) return;
            Config.saveToken(this, v);
            reload();
        });
    }

    private void reload() {
        config = Config.load(this);
        problem = null;
        entities = new ArrayList<>();
        main.removeCallbacks(refresh);
        build();
        if (config.complete()) main.post(refresh);
    }

    private interface Done {
        void done(String value);
    }

    private void edit(String title, String start, int type, Done done) {
        EditText field = new EditText(this);
        field.setInputType(type);
        field.setSingleLine(true);
        field.setText(start);
        field.setSelection(start.length());
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(field)
                .setPositiveButton("Save", (d, w) -> done.done(field.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
        field.requestFocus();
    }

    private void heading(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(13);
        h.setTextColor(0xFF41BDF5);
        h.setPadding(px(16), px(18), 0, px(4));
        rows.addView(h);
    }

    private Row add(Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
