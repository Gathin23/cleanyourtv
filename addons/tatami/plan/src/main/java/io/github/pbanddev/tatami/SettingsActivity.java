package io.github.pbanddev.tatami;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The effects list over the live floor plan, so every switch shows its result at once. The look and the swap period
 * come first; OK steps through their choices. Up and Down move one row, OK switches, Back closes. Opened from the
 * preview with OK, and from the TV's screensaver settings where the firmware offers it.
 *
 * <p>The look and the swap period can also be set without key presses, and are kept:
 * {@code am start -n io.github.pbanddev.tatami/.SettingsActivity --es look nagaya --ei swap_minutes 1}.
 * {@code look} is yojohan, nagaya or both; {@code swap_minutes} is 1 to 60 (the row itself offers 5, 10, 30 and 60).
 */
public final class SettingsActivity extends Activity {
    static final String EXTRA_LOOK = "look";
    static final String EXTRA_SWAP_MINUTES = "swap_minutes";
    private static final int TEXT = 0xFFEDE6D3;
    private static final int ACCENT = 0xFFE8D87A;

    private FloorPlanView plan;
    private TextView lookRow, swapRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepExtras(getIntent());
        FrameLayout root = new FrameLayout(this);
        plan = new FloorPlanView(this);
        root.addView(plan, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(40), dp(32), dp(32), dp(32));
        panel.setBackgroundColor(0xE6151310);

        TextView title = new TextView(this);
        title.setText(R.string.effects_title);
        title.setTextColor(ACCENT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setPadding(dp(12), 0, 0, dp(16));
        panel.addView(title);

        Effects current = Effects.load(this);
        lookRow = choiceRow(rowText(R.string.look_label, lookName(current.look)));
        lookRow.setOnClickListener(view -> {
            LookSchedule.Mode next = Effects.load(this).look.next();
            Effects.setLook(this, next);
            lookRow.setText(rowText(R.string.look_label, lookName(next)));
            plan.setEffects(Effects.load(this));
        });
        panel.addView(lookRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        swapRow = choiceRow(rowText(R.string.swap_label, minutes(current.swapMinutes)));
        swapRow.setOnClickListener(view -> {
            int next = Effects.nextSwapMinutes(Effects.load(this).swapMinutes);
            Effects.setSwapMinutes(this, next);
            swapRow.setText(rowText(R.string.swap_label, minutes(next)));
            plan.setEffects(Effects.load(this));
        });
        panel.addView(swapRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (String key : Effects.ALL) {
            CheckBox box = new CheckBox(this);
            box.setText(label(key));
            box.setTextColor(TEXT);
            box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            box.setPadding(dp(12), dp(10), dp(12), dp(10));
            box.setBackground(focusBackground());
            box.setChecked(current.isOn(key));
            box.setOnCheckedChangeListener((view, on) -> {
                Effects.set(this, key, on);
                plan.setEffects(Effects.load(this));
            });
            panel.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        root.addView(panel, new FrameLayout.LayoutParams(dp(460), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
        setContentView(root);
        lookRow.requestFocus();
    }

    /** Opened again while it is on top (it is single-top): keeps the new extras and shows them. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        keepExtras(intent);
        Effects current = Effects.load(this);
        lookRow.setText(rowText(R.string.look_label, lookName(current.look)));
        swapRow.setText(rowText(R.string.swap_label, minutes(current.swapMinutes)));
        plan.setEffects(current);
    }

    /** Keeps a look and a swap period given as extras; values that name none are left out. */
    private void keepExtras(Intent intent) {
        LookSchedule.Mode look = LookSchedule.Mode.of(intent.getStringExtra(EXTRA_LOOK));
        if (look != null) Effects.setLook(this, look);
        if (intent.hasExtra(EXTRA_SWAP_MINUTES)) Effects.setSwapMinutes(this, intent.getIntExtra(EXTRA_SWAP_MINUTES, 0));
    }

    /** A row OK steps through, styled as the switches below it. */
    private TextView choiceRow(String text) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(TEXT);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(focusBackground());
        row.setFocusable(true);
        row.setClickable(true);
        return row;
    }

    /** "Label: value". */
    private String rowText(int label, String value) {
        return getString(R.string.setting_row, getString(label), value);
    }

    private String lookName(LookSchedule.Mode look) {
        switch (look) {
            case YOJOHAN: return getString(R.string.look_yojohan);
            case NAGAYA: return getString(R.string.look_nagaya);
            default: return getString(R.string.look_both);
        }
    }

    private String minutes(int minutes) {
        return getString(R.string.swap_minutes, minutes);
    }

    @Override
    protected void onResume() {
        super.onResume();
        plan.onResume();
    }

    @Override
    protected void onPause() {
        plan.onPause();
        super.onPause();
    }

    private int label(String key) {
        switch (key) {
            case Effects.GRAIN: return R.string.effect_grain;
            case Effects.VIGNETTE: return R.string.effect_vignette;
            case Effects.TILT_SHIFT: return R.string.effect_tilt_shift;
            case Effects.DOORS: return R.string.effect_doors;
            case Effects.FIXTURES: return R.string.effect_fixtures;
            case Effects.CAMERA: return R.string.effect_camera;
            default: throw new IllegalArgumentException(key);
        }
    }

    private static StateListDrawable focusBackground() {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, new ColorDrawable(0x40E8D87A));
        background.addState(new int[] {}, new ColorDrawable(0x00000000));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
