package com.mauro.tv.monitor;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.PorterDuff;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Every process on the TV: name, how long it has been running, CPU and RAM. Android does not let an app read other
 * apps' processes, so the table is read over ADB by the PC assistant and sent here; this screen only shows it.
 */
public final class DetailsActivity extends Activity {
    private static final String TAG = "TvMonitor";
    private static final String X = "com.gathin.tvhome.extra.";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private int bg = Color.BLACK, okColor = 0xFF34C759, warnColor = 0xFFFF9F0A, badColor = 0xFFFF453A;
    private final int ink = Color.WHITE, dim = Color.argb(140, 255, 255, 255);

    private final List<Object[]> rows = new ArrayList<>();      // name, uptime s, cpu %, ram kb
    private TextView summary, hint;
    private ListView list;
    private BaseAdapter adapter;
    private int sort = 1;                                        // 0 name, 1 RAM, 2 CPU, 3 uptime
    private boolean hideKernel;
    private long requestedAt;
    private String loaded = "";
    private final View[] sortButtons = new View[4];
    private ImageView kernelButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("journal", MODE_PRIVATE);
        Intent in = getIntent();
        bg = in.getIntExtra(X + "BG", bg);
        okColor = in.getIntExtra(X + "OK", okColor);
        warnColor = in.getIntExtra(X + "WARN", warnColor);
        badColor = in.getIntExtra(X + "BAD", badColor);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(96, 56, 96, 32);
        setContentView(root);

        summary = text("", 30, ink, 0f);
        summary.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        root.addView(summary);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int[] icons = {0, R.drawable.ic_ram, R.drawable.ic_speed, R.drawable.ic_clock};
        for (int i = 0; i < 4; i++) {
            final int mode = i;
            View b0 = i == 0 ? nameButton(() -> { sort = 0; refreshTable(); }) : iconButton(icons[i], () -> { sort = mode; refreshTable(); });
            sortButtons[i] = b0;
            bar.addView(b0);
        }
        kernelButton = (ImageView) iconButton(R.drawable.ic_eye, () -> { hideKernel = !hideKernel; refreshTable(); });
        bar.addView(kernelButton);
        View refresh = iconButton(R.drawable.ic_refresh, this::request);
        android.widget.FrameLayout refreshBox = new android.widget.FrameLayout(this);
        refreshBox.addView(refresh);
        ImageView badge = new ImageView(this);                 // the small monitor: refreshing needs the PC
        badge.setImageResource(R.drawable.ic_pc);
        badge.setColorFilter(warnColor, PorterDuff.Mode.SRC_IN);
        android.widget.FrameLayout.LayoutParams badgeLp = new android.widget.FrameLayout.LayoutParams(30, 30);
        badgeLp.gravity = Gravity.BOTTOM | Gravity.END;
        badgeLp.setMargins(0, 0, 8, 8);
        refreshBox.addView(badge, badgeLp);
        bar.addView(refreshBox, new LinearLayout.LayoutParams(104, 104));
        root.addView(bar);

        hint = text("", 15, dim, 0f);
        root.addView(hint);
        root.addView(header());

        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(Color.argb(30, 255, 255, 255)));
        list.setVerticalScrollBarEnabled(false);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return view.size(); }
            @Override public Object getItem(int i) { return view.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View old, ViewGroup parent) { return row(view.get(i)); }
        };
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        refreshTable();
        sortButtons[1].requestFocus();
    }

    private final List<Object[]> view = new ArrayList<>();

    @Override protected void onResume() {
        super.onResume();
        if (prefs.getString("details", "").isEmpty()) request();
        ui.post(poll);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(poll);
        super.onPause();
    }

    /** Looks for a newer table every 2 s while this screen is open, and for the "no PC answered" hint. */
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            String json = prefs.getString("details", "");
            if (!json.equals(loaded)) {
                loaded = json;
                load(json);
                requestedAt = 0;
                refreshTable();
            }
            long waited = requestedAt == 0 ? 0 : System.currentTimeMillis() - requestedAt;
            if (waited > 0 && waited <= 25000) hint.setText(getString(R.string.details_waiting));
            else if (waited > 25000) hint.setText(getString(R.string.details_no_pc));
            ui.postDelayed(this, 2000);
        }
    };

    private void request() {
        requestedAt = System.currentTimeMillis();
        android.util.Log.i(TAG, "DETAILS " + requestedAt);
        hint.setText(getString(R.string.details_waiting));
    }

    private long dataAt;

    private void load(String json) {
        rows.clear();
        dataAt = 0;
        if (json.isEmpty()) return;
        try {
            JSONObject o = new JSONObject(json);
            dataAt = o.optLong("at");
            JSONArray a = o.getJSONArray("rows");
            for (int i = 0; i < a.length(); i++) {
                JSONArray r = a.getJSONArray(i);
                rows.add(new Object[] {r.getString(0), r.getLong(1), r.getDouble(2), r.getLong(3)});
            }
        } catch (JSONException ignored) { }
    }

    private void refreshTable() {
        view.clear();
        for (Object[] r : rows) {
            if (!(hideKernel && ((String) r[0]).startsWith("["))) view.add(r);
        }
        Collections.sort(view, new Comparator<Object[]>() {
            @Override public int compare(Object[] a, Object[] b) {
                switch (sort) {
                    case 0: return ((String) a[0]).compareToIgnoreCase((String) b[0]);
                    case 2: return Double.compare((Double) b[2], (Double) a[2]);
                    case 3: return Long.compare((Long) b[1], (Long) a[1]);
                    default: return Long.compare((Long) b[3], (Long) a[3]);
                }
            }
        });
        summary.setText(rows.isEmpty() ? getString(R.string.details_none)
                : getString(R.string.details_summary, view.size()));
        if (dataAt > 0 && requestedAt == 0) {
            hint.setText(getString(R.string.details_age,
                    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(dataAt * 1000))));
        }
        for (int i = 0; i < 4; i++) mark(sortButtons[i], i == sort);
        kernelButton.setImageResource(hideKernel ? R.drawable.ic_eye_off : R.drawable.ic_eye);
        mark(kernelButton, hideKernel);
        adapter.notifyDataSetChanged();
        list.setSelection(0);
    }

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setPadding(24, 16, 24, 8);
        float[] weights = {3.2f, 1.3f, 1f, 1.2f};
        int[] icons = {0, R.drawable.ic_clock, R.drawable.ic_speed, R.drawable.ic_ram};
        for (int i = 0; i < 4; i++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setGravity(i == 0 ? Gravity.START : Gravity.END);
            cell.setPadding(0, 0, 16, 0);
            if (icons[i] != 0) {
                ImageView v = new ImageView(this);
                v.setImageResource(icons[i]);
                v.setColorFilter(dim, PorterDuff.Mode.SRC_IN);
                cell.addView(v, new LinearLayout.LayoutParams(28, 28));
            }
            h.addView(cell, new LinearLayout.LayoutParams(0, -2, weights[i]));
        }
        return h;
    }

    /** The selected sort (or the active filter) is white, the rest dimmed. */
    private void mark(View v, boolean on) {
        if (v instanceof ImageView) ((ImageView) v).setColorFilter(on ? ink : dim, PorterDuff.Mode.SRC_IN);
        else if (v instanceof TextView) ((TextView) v).setTextColor(on ? ink : dim);
    }

    private View nameButton(final Runnable run) {
        TextView v = text("Az", 22, ink, 0f);
        v.setPadding(26, 20, 26, 20);
        v.setMinWidth(104);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(focusBackground());
        v.setOnClickListener(x -> run.run());
        return v;
    }

    private View iconButton(int res, final Runnable run) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setColorFilter(ink, PorterDuff.Mode.SRC_IN);
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setPadding(26, 26, 26, 26);
        v.setLayoutParams(new LinearLayout.LayoutParams(104, 104));
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(focusBackground());
        v.setOnClickListener(x -> run.run());
        return v;
    }

    private StateListDrawable focusBackground() {
        StateListDrawable d = new StateListDrawable();
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(Color.argb(46, 255, 255, 255));
        focus.setCornerRadius(18);
        d.addState(new int[] {android.R.attr.state_focused}, focus);
        return d;
    }

    private View row(Object[] r) {
        String name = (String) r[0];
        long up = (Long) r[1], kb = (Long) r[3];
        double cpu = (Double) r[2];
        boolean kernel = name.startsWith("[");
        LinearLayout l = cells(name, age(up), cpu <= 0 ? "-" : String.format("%.1f %%", cpu),
                kb <= 0 ? "-" : (kb >= 10240 ? (kb / 1024) + " MB" : String.format("%.1f MB", kb / 1024.0)), kernel ? dim : ink, 17);
        TextView c = (TextView) l.getChildAt(2), m = (TextView) l.getChildAt(3);
        c.setTextColor(cpu >= 40 ? badColor : cpu >= 15 ? warnColor : kernel ? dim : ink);
        m.setTextColor(kb >= 200 * 1024 ? badColor : kb >= 100 * 1024 ? warnColor : kernel ? dim : ink);
        l.setPadding(24, 10, 24, 10);
        return l;
    }

    private LinearLayout cells(String a, String b, String c, String d, int color, int size) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        float[] weights = {3.2f, 1.3f, 1f, 1.2f};
        String[] values = {a, b, c, d};
        for (int i = 0; i < 4; i++) {
            TextView t = text(values[i], size, color, 0f);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(i == 0 ? Gravity.START : Gravity.END);
            t.setPadding(0, 0, 16, 0);
            l.addView(t, new LinearLayout.LayoutParams(0, -2, weights[i]));
        }
        return l;
    }

    private static String age(long s) {
        if (s >= 86400) return (s / 86400) + "d " + String.format("%02dh", (s / 3600) % 24);
        if (s >= 3600) return (s / 3600) + "h " + String.format("%02dm", (s / 60) % 60);
        if (s >= 60) return (s / 60) + "m " + String.format("%02ds", s % 60);
        return s + "s";
    }

    private TextView text(String value, int size, int color, float spacing) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setLetterSpacing(spacing);
        v.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        v.setPadding(0, 6, 0, 6);
        return v;
    }
}
