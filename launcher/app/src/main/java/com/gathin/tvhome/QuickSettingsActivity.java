package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService):
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel. Over an app ({@link #EXTRA_SIDE}, settings_key_side_sheet) it is a sheet down the right edge
 * instead, with a Sound row too, so the picture on the left stays in view.
 */
public final class QuickSettingsActivity extends Activity {
    static final String EXTRA_SIDE = "side";

    /** The side sheet only with settings_key_side_sheet on and Home not on screen; over Home the small panel. */
    static boolean opensSideSheet(boolean sideSheet, boolean homeOnScreen) {
        return sideSheet && !homeOnScreen;
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }


    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra(EXTRA_SIDE, false)) {
            createSide();
            return;
        }
        setContentView(R.layout.activity_quick_settings);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            View panel = findViewById(R.id.panel);
            android.widget.FrameLayout.LayoutParams position =
                    (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
            position.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
            int inset = position.getMarginEnd();
            position.setMarginEnd(0);
            position.setMargins(inset, position.topMargin, 0, position.bottomMargin);
            panel.setLayoutParams(position);
        }
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);
        addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, SystemSettings.intent(this, Settings.ACTION_SETTINGS));
        addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, SystemSettings.intent(this, Settings.ACTION_DISPLAY_SETTINGS));
        addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
        addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        rows.getChildAt(0).requestFocus();
    }

    private void createSide() {
        setContentView(R.layout.activity_quick_settings);
        // The small panel's glass as a sheet down the whole edge, pushed past it so only the inner corners show:
        // no layout or drawable of its own.
        LinearLayout sheet = findViewById(R.id.panel);
        float dp = getResources().getDisplayMetrics().density;
        int edge = Math.round(28 * dp), side = Math.round(16 * dp);
        android.widget.FrameLayout.LayoutParams position = new android.widget.FrameLayout.LayoutParams(
                Math.round(320 * dp), android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.Gravity.END);
        position.setMargins(0, -edge, -edge, -edge);
        sheet.setLayoutParams(position);
        sheet.setPadding(side, edge + Math.round(36 * dp), edge + side, edge);
        TextView title = new TextView(this);
        title.setText(R.string.settings);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(24);
        title.setPadding(side, 0, 0, side);
        sheet.addView(title, 0);
        LinearLayout rows = findViewById(R.id.rows);
        addSideRow(rows, R.drawable.ic_picture, R.string.quick_picture, resolved(Settings.ACTION_DISPLAY_SETTINGS));
        addSideRow(rows, R.drawable.ic_sound, R.string.quick_sound, resolved(Settings.ACTION_SOUND_SETTINGS));
        addSideRow(rows, R.drawable.ic_wifi, R.string.quick_network, resolved(Settings.ACTION_WIFI_SETTINGS));
        addSideRow(rows, R.drawable.ic_apps, R.string.quick_apps, resolved(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // SystemSettings falls back to the vendor or AOSP settings app, so this row is always there.
        addSideRow(rows, R.drawable.ic_settings, R.string.tv_settings, SystemSettings.intent(this, Settings.ACTION_SETTINGS));
        addSideRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        // Roomier rows than the small panel's: taller, with a larger icon and name.
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            row.getLayoutParams().height = Math.round(52 * dp);
            row.setPadding(side, 0, side, 0);
            ((TextView) row.findViewById(R.id.title)).setTextSize(17);
            View icon = row.findViewById(R.id.icon);
            icon.getLayoutParams().width = icon.getLayoutParams().height = Math.round(24 * dp);
        }
        rows.getChildAt(0).requestFocus();
    }

    /** The standard action, or null where nothing on this TV answers it, so its row is left out. */
    private Intent resolved(String action) {
        Intent intent = new Intent(action);
        return getPackageManager().resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) != null
                ? intent : null;
    }

    private void addSideRow(LinearLayout rows, int icon, int title, Intent target) {
        if (target != null) addRow(rows, icon, title, 0, target);
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    @SuppressWarnings("deprecation")
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        rows.addView(row);
    }
}
