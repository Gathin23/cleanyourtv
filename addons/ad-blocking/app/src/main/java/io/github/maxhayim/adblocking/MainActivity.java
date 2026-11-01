package io.github.maxhayim.adblocking;

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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The tile and module card open this: today's queries and blocked share from your Pi-hole or AdGuard Home, and
 * one OK to pause blocking for five minutes when a site won't work. It reads the server only while this screen
 * is open.
 */
public class MainActivity extends Activity {
    private static final long REFRESH_MS = 30_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private LinearLayout rows;
    private TextView status;
    private Config config;
    private Server.Result last;
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            fetch(null);
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
        reload();
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

    /** Reads the numbers; pause non-null pauses (true) or resumes first. */
    private void fetch(Boolean pause) {
        Config c = config;
        if (!c.complete()) return;
        net.execute(() -> {
            Server s = new Server(c);
            Server.Result r = pause == null ? s.stats() : s.pause(pause);
            main.post(() -> {
                if (isFinishing()) return;
                last = r;
                build();
            });
        });
    }

    private void reload() {
        config = Config.load(this);
        last = null;
        main.removeCallbacks(refresh);
        build();
        if (config.complete()) main.post(refresh);
    }

    /** Rebuilds the list; focus stays on the row it was on. */
    private void build() {
        int focused = rows.indexOfChild(rows.getFocusedChild());
        rows.removeAllViews();
        boolean adguard = config.kind == Blocker.Kind.ADGUARD;
        String server = adguard ? "AdGuard Home" : "Pi-hole";
        Row first = null;
        if (!config.complete()) {
            status.setText("Add your " + server + "'s address" + (adguard ? " and login." : " (and password, if it has one)."));
        } else if (last == null) {
            status.setText("Reading " + server + "…");
        } else if (last.problem != null) {
            status.setText(last.problem);
        } else {
            Blocker.Stats st = last.stats;
            status.setText(server + (st.enabled ? ": blocking is on." : ": blocking is paused."));
            first = add(new Row(this, "Blocking", st.enabled ? "OK pauses it for 5 minutes, for a site that won't work."
                    : "OK turns it back on now.", false, () -> fetch(st.enabled), null));
            first.setValue(st.enabled ? "On" : "Paused");
            add(new Row(this, "Queries today", null, false, null, null)).setValue(Blocker.count(st.queries));
            add(new Row(this, "Blocked today", null, false, null, null)).setValue(Blocker.count(st.blocked));
            Row share = add(new Row(this, "Blocked share", null, true, null, null));
            share.setValue(Blocker.pct(st.percent));
            share.setBar((float) Math.min(1, st.percent / 100));
        }
        heading("Connection");
        Row kind = add(new Row(this, "Blocker", null, false, () -> {
            Config.save(this, "kind", adguard ? "PIHOLE" : "ADGUARD");
            reload();
        }, null));
        kind.setValue(server);
        Row url = add(new Row(this, "Address", adguard ? "For example http://192.168.1.2:3000" : "For example http://192.168.1.2",
                false, () -> edit("Address", config.url.isEmpty() ? "http://" : config.url,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, v -> {
                    String u = Blocker.normalizeUrl(v);
                    if (!u.isEmpty() && !Blocker.validUrl(u)) {
                        status.setText("That doesn't look like an address: " + u);
                        return;
                    }
                    Config.save(this, "url", u);
                    reload();
                }), null));
        url.setValue(config.url.isEmpty() ? "Not set" : config.url);
        if (adguard) {
            Row user = add(new Row(this, "Username", null, false, () -> edit("Username", config.user,
                    InputType.TYPE_CLASS_TEXT, v -> {
                        Config.save(this, "user", v);
                        reload();
                    }), null));
            user.setValue(config.user.isEmpty() ? "Not set" : config.user);
        }
        Row pw = add(new Row(this, adguard ? "Password" : "Password or API token",
                adguard ? "Stored on this TV only." : "Pi-hole 6: the web or app password. Pi-hole 5: the API token. Stored on this TV only.",
                false, () -> edit("Password", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, v -> {
                    Config.save(this, "password", v);
                    reload();
                }), null));
        pw.setValue(config.password.isEmpty() ? "None" : "Set");

        View keep = focused >= 0 && focused < rows.getChildCount() ? rows.getChildAt(focused) : null;
        if (keep != null && keep.isFocusable()) keep.requestFocus();
        else if (first != null) first.requestFocus();
        else url.requestFocus();
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
        h.setTextColor(0xFFE84855);
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
