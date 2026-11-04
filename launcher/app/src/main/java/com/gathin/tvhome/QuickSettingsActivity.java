package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService):
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel.
 */
public final class QuickSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }


    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        sheet = Config.load(this).quickPanelSheet;
        if (sheet) asSheet(rows);
        addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, SystemSettings.intent(this, Settings.ACTION_SETTINGS));
        addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, SystemSettings.intent(this, Settings.ACTION_DISPLAY_SETTINGS));
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        addRow(rows, R.drawable.ic_wifi, R.string.quick_network, !sheet ? 0 : cm != null && cm.getActiveNetwork() != null
                ? R.string.connected : R.string.offline, SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
        addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        // In the sheet the first tile sits in the first line, after the time and the date.
        View first = rows.getChildAt(sheet ? 2 : 0);
        (sheet ? ((ViewGroup) first).getChildAt(0) : first).requestFocus();
    }

    /** quick_panel_sheet: rows become tiles, two to a line under the time and the date. */
    private boolean sheet;

    /**
     * The panel stretched to the full height at the screen's edge, a shade on that side so the picture behind
     * stays visible, and the time and the date above the tiles.
     */
    private void asSheet(LinearLayout rows) {
        float d = getResources().getDisplayMetrics().density;
        View panel = findViewById(R.id.panel);
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
        boolean start = getResources().getBoolean(R.bool.rtl_menu_bar);
        lp.width = (int) (400 * d);
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.gravity = start ? Gravity.START : Gravity.END;
        lp.setMargins((int) (20 * d), (int) (20 * d), (int) (20 * d), (int) (20 * d));
        panel.setLayoutParams(lp);
        panel.setPadding((int) (24 * d), (int) (28 * d), (int) (24 * d), (int) (24 * d));
        findViewById(R.id.scrim).setBackground(new GradientDrawable(start ? GradientDrawable.Orientation.LEFT_RIGHT
                : GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0xB3000000, 0x00000000}));
        java.util.Date now = new java.util.Date();
        TextView time = new TextView(this);
        time.setTextSize(40);
        time.setTextColor(0xFFFFFFFF);
        time.setText(android.text.format.DateFormat.getTimeFormat(this).format(now));
        rows.addView(time);
        TextView date = new TextView(this);
        date.setTextColor(0xB3FFFFFF);
        java.util.Locale locale = getResources().getConfiguration().getLocales().get(0);
        date.setText(new java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale,
                "EEEEdMMMM"), locale).format(now));
        date.setPadding(0, 0, 0, (int) (20 * d));
        rows.addView(date);
    }

    /** Puts {@code row} in the sheet's grid: a column of icon, title and status, half the width, two per line. */
    private void addTile(LinearLayout rows, View row) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout tile = (LinearLayout) row;
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = tile.findViewById(R.id.title);
        title.setSingleLine(false);   // the row is one line; a tile has room for a long name on two
        title.setMaxLines(2);
        title.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // The line still holding one tile takes the next; the time and the date above are plain text.
        View last = rows.getChildAt(rows.getChildCount() - 1);
        LinearLayout line = last instanceof LinearLayout && ((LinearLayout) last).getChildCount() == 1
                ? (LinearLayout) last : null;
        if (line == null) {
            line = new LinearLayout(this);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lineLp.topMargin = (int) (10 * d);
            rows.addView(line, lineLp);
        }
        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, (int) (88 * d), 1f);
        if (line.getChildCount() == 1) cell.setMarginStart((int) (10 * d));
        line.addView(tile, cell);
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
        if (sheet) addTile(rows, row);
        else rows.addView(row);
    }
}
