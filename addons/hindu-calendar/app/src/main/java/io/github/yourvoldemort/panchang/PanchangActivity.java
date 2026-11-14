package io.github.yourvoldemort.panchang;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

/** A standalone module card; it never writes the launcher's configuration. */
public final class PanchangActivity extends Activity {
    static final String PREFS = "panchang";
    static final String KEY_CITY = "city", KEY_REGION = "region";
    private PanchangPanel panel;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        panel = new PanchangPanel(this, false);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        panel.setCity(City.cityNamed(prefs.getString(KEY_CITY, City.forTimeZone().name)));
        panel.setRegion(Region.named(prefs.getString(KEY_REGION, Region.NORTH.name())));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, panel.dp(16), 0, 0);
        button(actions, R.string.previous).setOnClickListener(v -> panel.showDay(-1));
        Button today = button(actions, R.string.today);
        today.setOnClickListener(v -> panel.showDay(0));
        button(actions, R.string.next).setOnClickListener(v -> panel.showDay(1));
        button(actions, R.string.city).setOnClickListener(v -> chooseCity());
        button(actions, R.string.region).setOnClickListener(v -> chooseRegion());
        // Google TV has no screensaver picker for other apps' dreams: offer the button only where one exists.
        Intent dreams = new Intent(Settings.ACTION_DREAM_SETTINGS);
        if (dreams.resolveActivity(getPackageManager()) != null) {
            button(actions, R.string.screensaver).setOnClickListener(v -> {
                try { startActivity(dreams); }
                catch (ActivityNotFoundException | SecurityException e) {
                    Toast.makeText(this, R.string.screensaver_missing, Toast.LENGTH_LONG).show();
                }
            });
        }
        panel.addActions(actions);
        setContentView(panel);
        today.requestFocus();
    }

    private void chooseCity() {
        int current = City.indexOf(panel.city().name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.city)
                .setSingleChoiceItems(City.names(), current, (dialog, which) -> {
                    panel.setCity(City.ALL[which]);
                    save();
                    dialog.dismiss();
                })
                .show();
    }

    private void chooseRegion() {
        Region[] regions = Region.values();
        String[] names = new String[regions.length];
        for (int i = 0; i < regions.length; i++) names[i] = regions[i].label + " · " + regions[i].era;
        new AlertDialog.Builder(this)
                .setTitle(R.string.region)
                .setSingleChoiceItems(names, panel.region().ordinal(), (dialog, which) -> {
                    panel.setRegion(regions[which]);
                    save();
                    dialog.dismiss();
                })
                .show();
    }

    private void save() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_CITY, panel.city().name)
                .putString(KEY_REGION, panel.region().name())
                .apply();
    }

    private Button button(LinearLayout parent, int label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        // The stock button barely changes when focused; on a TV the focus has to be plain to see.
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, shape(PanchangPanel.ACCENT));
        background.addState(new int[0], shape(0x33ffffff));
        button.setBackground(background);
        button.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{PanchangPanel.BACKGROUND, PanchangPanel.TEXT}));
        button.setPadding(panel.dp(16), panel.dp(8), panel.dp(16), panel.dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(panel.dp(4), 0, panel.dp(4), 0);
        button.setLayoutParams(params);
        parent.addView(button);
        return button;
    }

    private GradientDrawable shape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(panel.dp(6));
        return shape;
    }

    /** Channel and media keys page the day from anywhere on the screen. */
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        switch (code) {
            case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_MEDIA_REWIND:
                panel.showDay(-1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_PAGE_DOWN: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                panel.showDay(1);
                return true;
            default:
                return super.onKeyDown(code, event);
        }
    }

    @Override protected void onResume() { super.onResume(); panel.start(); }
    @Override protected void onPause() { panel.stop(); super.onPause(); }
}
