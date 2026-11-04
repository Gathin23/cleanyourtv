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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * How the add-on works, one block per part of the screen, then how to connect an AI with every setting explained and
 * the exact request the PC sends. Static text; the D-pad moves block by block.
 */
public final class HelpActivity extends Activity {
    private static final String X = "com.gathin.tvhome.extra.";

    private int bg = Color.BLACK, okColor = 0xFF34C759, warnColor = 0xFFFF9F0A;
    private final int ink = Color.WHITE, dim = Color.argb(150, 255, 255, 255);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        bg = in.getIntExtra(X + "BG", bg);
        okColor = in.getIntExtra(X + "OK", okColor);
        warnColor = in.getIntExtra(X + "WARN", warnColor);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(96, 56, 96, 32);
        setContentView(root);

        LinearLayout head = row();
        head.addView(icon(R.drawable.ic_help, ink, 40));
        TextView title = text(getString(R.string.help_title), 30, ink, false);
        title.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        title.setPadding(18, 0, 0, 0);
        head.addView(title);
        root.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        View first = section(list, R.drawable.ic_pc, warnColor, R.string.help_legend_t, R.string.help_legend, false);
        section(list, R.drawable.ic_ok, okColor, R.string.help_status_t, R.string.help_status, false);
        section(list, R.drawable.ic_ram, ink, R.string.help_meters_t, R.string.help_meters, false);
        section(list, R.drawable.ic_shield, ink, R.string.help_flagged_t, R.string.help_flagged, false);
        section(list, R.drawable.ic_list, ink, R.string.help_suggested_t, R.string.help_suggested, false);
        section(list, R.drawable.ic_ai, ink, R.string.help_ask_t, R.string.help_ask, true);
        section(list, R.drawable.ic_clock, warnColor, R.string.help_apply_t, R.string.help_apply, true);
        section(list, R.drawable.ic_history, ink, R.string.help_history_t, R.string.help_history, false);
        section(list, R.drawable.ic_list, ink, R.string.help_details_t, R.string.help_details, true);
        section(list, R.drawable.ic_broom, ink, R.string.help_cache_t, R.string.help_cache, false);
        section(list, R.drawable.ic_power, ink, R.string.help_restart_t, R.string.help_restart, true);
        section(list, R.drawable.ic_usb, warnColor, R.string.help_usb_t, R.string.help_usb, false);

        section(list, R.drawable.ic_pc, okColor, R.string.help_ai_t, R.string.help_ai_steps, true);
        section(list, R.drawable.ic_pc, warnColor, R.string.help_pc_t, R.string.help_pc, true);
        section(list, R.drawable.ic_pc, ink, R.string.help_params_t, R.string.help_params, true);
        View code = section(list, R.drawable.ic_ai, ink, R.string.help_example_t, 0, true);
        ((LinearLayout) code).addView(mono(readExample()));
        section(list, R.drawable.ic_warn, warnColor, R.string.help_other_t, R.string.help_other, true);
        first.requestFocus();
    }

    /**
     * A focusable block: icon and title, then the text. Body 0 means the caller adds its own content. A block about
     * something that only works with the PC carries the small monitor after its title, as the buttons do.
     */
    private View section(LinearLayout list, int iconRes, int iconColor, int titleRes, int bodyRes, boolean needsPc) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(24, 18, 24, 18);
        b.setFocusable(true);
        b.setBackground(focusBackground());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 10, 0, 0);
        b.setLayoutParams(lp);
        LinearLayout t = row();
        t.addView(icon(iconRes, iconColor, 32));
        TextView name = text(getString(titleRes), 21, ink, false);
        name.setPadding(16, 0, 0, 0);
        t.addView(name);
        if (needsPc) {
            ImageView badge = icon(R.drawable.ic_pc, warnColor, 26);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(26, 26);
            bl.setMargins(16, 0, 0, 0);
            badge.setLayoutParams(bl);
            t.addView(badge);
            TextView note = text(getString(R.string.needs_pc), 15, dim, false);
            note.setPadding(8, 0, 0, 0);
            t.addView(note);
        }
        b.addView(t);
        if (bodyRes != 0) b.addView(text(getString(bodyRes), 17, dim, false));
        list.addView(b);
        return b;
    }

    private String readExample() {
        try (InputStream in = getResources().openRawResource(R.raw.help_example)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private TextView mono(String value) {
        TextView v = text(value, 14, Color.argb(230, 255, 255, 255), true);
        v.setPadding(0, 10, 0, 0);
        return v;
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

    private TextView text(String value, int size, int color, boolean monospace) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(monospace ? Typeface.MONOSPACE : Typeface.create("sans-serif-light", Typeface.NORMAL));
        v.setPadding(0, 4, 0, 4);
        return v;
    }
}
