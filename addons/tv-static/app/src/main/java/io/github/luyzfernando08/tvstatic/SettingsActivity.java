package io.github.luyzfernando08.tvstatic;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The module card on the launcher's Home (and the app's own tile): switch the effect on or off, with or
 * without sound, how long it runs, and a button to try it now. Each row changes its value on OK.
 */
public class SettingsActivity extends Activity {
    private TextView enabled, style, sound, seconds;
    private static final int[] STYLE_NAMES = {R.string.style_snow, R.string.style_bars, R.string.style_no_signal,
            R.string.style_random};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF0B0D12);
        int pad = dp(48);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        root.addView(title);
        TextView hint = new TextView(this);
        hint.setText(R.string.hint);
        hint.setTextColor(0xB3FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        hint.setPadding(0, dp(8), 0, dp(28));
        root.addView(hint);

        enabled = row(root);
        enabled.setOnClickListener(v -> {
            Prefs.setEnabled(this, !Prefs.enabled(this));
            refresh();
        });
        style = row(root);
        style.setOnClickListener(v -> {
            Prefs.setStyle(this, (Prefs.style(this) + 1) % Prefs.STYLES);
            refresh();
        });
        sound = row(root);
        sound.setOnClickListener(v -> {
            Prefs.setSound(this, !Prefs.sound(this));
            refresh();
        });
        seconds = row(root);
        seconds.setOnClickListener(v -> {
            int now = Prefs.seconds(this), next = Prefs.SECONDS[0];
            for (int i = 0; i < Prefs.SECONDS.length - 1; i++) {
                if (Prefs.SECONDS[i] == now) next = Prefs.SECONDS[i + 1];
            }
            Prefs.setSeconds(this, next);
            refresh();
        });
        TextView test = row(root);
        test.setText(R.string.test);
        test.setOnClickListener(v -> startActivity(new Intent(this, StaticActivity.class)));

        setContentView(root);
        refresh();
        enabled.requestFocus();
    }

    private void refresh() {
        enabled.setText(getString(R.string.enabled_state,
                getString(Prefs.enabled(this) ? R.string.on : R.string.off)));
        style.setText(getString(R.string.style_state, getString(STYLE_NAMES[Prefs.style(this)])));
        sound.setText(getString(R.string.sound_state,
                getString(Prefs.sound(this) ? R.string.with_sound : R.string.without_sound)));
        seconds.setText(getString(R.string.seconds_state, Prefs.seconds(this)));
    }

    private TextView row(LinearLayout root) {
        TextView row = new TextView(this);
        row.setTextColor(Color.WHITE);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), dp(14), dp(24), dp(14));
        row.setFocusable(true);
        row.setClickable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, pill(0x40FFFFFF));
        bg.addState(new int[]{}, pill(0x14FFFFFF));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(560), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        root.addView(row, lp);
        return row;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(28));
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
