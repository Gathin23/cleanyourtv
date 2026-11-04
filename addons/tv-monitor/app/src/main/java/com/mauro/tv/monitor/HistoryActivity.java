package com.mauro.tv.monitor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

/**
 * Every exchange with the PC assistant, newest first: what was asked (the readings the PC sent the AI) and what
 * came back, with what was done about it. Kept on the TV by ReportReceiver; this screen only shows it.
 */
public final class HistoryActivity extends Activity {
    private static final String X = "com.gathin.tvhome.extra.";

    private int bg = Color.BLACK, okColor = 0xFF34C759, warnColor = 0xFFFF9F0A, badColor = 0xFFFF453A;
    private final int ink = Color.WHITE, dim = Color.argb(140, 255, 255, 255);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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

        JSONArray history = new JSONArray();
        try {
            history = new JSONArray(getSharedPreferences("journal", MODE_PRIVATE).getString(ReportReceiver.HISTORY, "[]"));
        } catch (JSONException ignored) { }

        LinearLayout head = row();
        head.addView(icon(R.drawable.ic_history, ink, 40));
        TextView title = text(history.length() == 0 ? getString(R.string.history_empty) : String.valueOf(history.length()), 30, ink);
        title.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        title.setPadding(18, 0, 0, 0);
        head.addView(title);
        root.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        View first = null;
        for (int i = 0; i < history.length(); i++) {
            JSONObject o = history.optJSONObject(i);
            if (o == null) continue;
            View block = entry(o);
            list.addView(block);
            if (first == null) first = block;
        }
        if (first != null) first.requestFocus();
    }

    /** One exchange: when and how it ended, the question, the answer, what was done. Focusable so D-pad scrolls. */
    private View entry(JSONObject o) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(24, 20, 24, 20);
        b.setFocusable(true);
        b.setBackground(focusBackground());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 14, 0, 0);
        b.setLayoutParams(lp);

        String outcome = o.optString("outcome");
        boolean good = outcome.equals("kept") || outcome.equals("applied") || outcome.equals("nothing_to_do");
        boolean bad = outcome.startsWith("reverted") || outcome.equals("error");
        LinearLayout top = row();
        top.addView(icon(good ? R.drawable.ic_check : bad ? R.drawable.ic_undo : R.drawable.ic_close, good ? okColor : bad ? badColor : dim, 30));
        TextView when = text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(o.optLong("at") * 1000)), 18, dim);
        when.setPadding(14, 0, 0, 0);
        top.addView(when);
        b.addView(top);

        JSONObject q = o.optJSONObject("q");
        b.addView(text(getString(R.string.history_question), 14, dim));
        if (q == null) {
            b.addView(text("–", 17, dim));
        } else {
            LinearLayout chips = row();
            JSONArray ram = q.optJSONArray("ram");
            if (ram != null && ram.length() == 2) chips.addView(chip(R.drawable.ic_ram, ram.optInt(0) + " / " + ram.optInt(1) + " MB"));
            JSONArray disk = q.optJSONArray("disk");
            if (disk != null && disk.length() == 2) {
                chips.addView(chip(R.drawable.ic_disk, String.format("%.1f / %.1f GB", disk.optLong(0) / 1048576.0, disk.optLong(1) / 1048576.0)));
            }
            if (q.optInt("flagged") > 0) chips.addView(chip(R.drawable.ic_shield, String.valueOf(q.optInt("flagged"))));
            b.addView(chips);
            JSONArray top3 = q.optJSONArray("top");
            StringBuilder names = new StringBuilder();
            for (int i = 0; top3 != null && i < top3.length(); i++) names.append(i == 0 ? "" : ", ").append(top3.optString(i));
            if (names.length() > 0) b.addView(text(names.toString(), 15, dim));
        }

        b.addView(text(getString(R.string.history_answer), 14, dim));
        b.addView(text(o.optString("summary"), 20, ink));
        JSONArray findings = o.optJSONArray("findings");
        for (int i = 0; findings != null && i < findings.length(); i++) b.addView(text("• " + findings.optString(i), 16, dim));
        JSONArray actions = o.optJSONArray("actions");
        for (int i = 0; actions != null && i < actions.length(); i++) {
            JSONObject a = actions.optJSONObject(i);
            if (a == null) continue;
            String st = a.optString("status");
            boolean ok = st.equals("applied") || st.equals("kept");
            boolean undone = st.equals("rolled_back") || st.equals("reverted");
            LinearLayout l = row();
            l.addView(icon(ok ? R.drawable.ic_check : undone ? R.drawable.ic_undo : st.equals("proposed") ? R.drawable.ic_dot : R.drawable.ic_close,
                    ok ? okColor : undone ? badColor : dim, 26));
            String what = a.optString("package").isEmpty() ? a.optString("type") : a.optString("package");
            TextView t = text(symbol(a.optString("type")) + "  " + what, 16, ok ? okColor : undone ? badColor : dim);
            t.setPadding(12, 0, 0, 0);
            l.addView(t);
            b.addView(l);
        }
        return b;
    }

    private static String symbol(String type) {
        switch (type) {
            case "disable_package": return "⊗";
            case "force_stop": return "■";
            case "trim_caches": return "♻";
            default: return type;
        }
    }

    private View chip(int res, String value) {
        LinearLayout c = row();
        c.setPadding(0, 4, 28, 4);
        c.addView(icon(res, dim, 26));
        TextView t = text(value, 17, ink);
        t.setPadding(10, 0, 0, 0);
        c.addView(t);
        return c;
    }

    private StateListDrawable focusBackground() {
        StateListDrawable d = new StateListDrawable();
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(Color.argb(40, 255, 255, 255));
        focus.setCornerRadius(18);
        d.addState(new int[] {android.R.attr.state_focused}, focus);
        return d;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private ImageView icon(int res, int color, int size) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return v;
    }

    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        v.setPadding(0, 4, 0, 4);
        return v;
    }
}
