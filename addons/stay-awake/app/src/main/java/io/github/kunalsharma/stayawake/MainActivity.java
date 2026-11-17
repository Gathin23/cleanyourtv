package io.github.kunalsharma.stayawake;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Stay Awake: two rows, worked with the remote alone.
 *
 * <ul>
 *   <li><b>Screen timeout</b>: OK steps 15 min, 30 min, 1 h, 2 h, 4 h, 8 h, Never. Writes
 *       Settings.System.SCREEN_OFF_TIMEOUT; needs "modify system settings" (WRITE_SETTINGS).</li>
 *   <li><b>Stay awake while plugged in</b>: OK switches Settings.Global.STAY_ON_WHILE_PLUGGED_IN between 0 and 7
 *       (AC, USB, wireless). Needs WRITE_SECURE_SETTINGS, which only ADB can grant.</li>
 * </ul>
 *
 * There is no service, no receiver and no overlay: the two settings live in Android and survive a reboot. When
 * a permission is missing, the screen shows the one ADB command that grants it.
 */
public class MainActivity extends Activity {
    /** Android's own default screen timeout on a TV that never set one: 30 minutes. */
    private static final int DEFAULT_TIMEOUT_MS = 30 * 60 * 1000;
    /** BatteryManager.BATTERY_PLUGGED_AC | USB | WIRELESS. */
    private static final int PLUGGED_ALL = 7;
    private static final String WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS";

    private TextView timeoutRow;
    private TextView awakeRow;
    private TextView help;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(64), dp(40), dp(64), dp(40));

        TextView title = label(getString(R.string.app_name), 34, 0xFFFFFFFF);
        TextView hint = label(getString(R.string.hint_keys), 18, 0xFF9AA7B4);
        timeoutRow = row();
        timeoutRow.setOnClickListener(v -> cycleTimeout());
        awakeRow = row();
        awakeRow.setOnClickListener(v -> toggleAwake());
        help = label("", 18, 0xFFC9D2DB);

        column.addView(title);
        column.addView(hint, spaced(4, 24));
        column.addView(timeoutRow, spaced(0, 8));
        column.addView(awakeRow, spaced(0, 24));
        column.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF101418);
        scroll.addView(column);
        setContentView(scroll);
        timeoutRow.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The "modify system settings" screen can change the permission while we are away.
        refresh();
    }

    // ---------------------------------------------------------------------------------------------------------

    private void cycleTimeout() {
        if (!Settings.System.canWrite(this)) {
            openWriteSettings();
            refresh();
            return;
        }
        int wanted = Timeouts.next(readTimeout());
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, wanted);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.toast_refused, Toast.LENGTH_LONG).show();
        }
        if (readTimeout() != wanted) {
            // Some firmware accepts the call and keeps its own value.
            Toast.makeText(this, R.string.toast_refused, Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void toggleAwake() {
        if (!hasSecureSettings()) {
            Toast.makeText(this, R.string.toast_need_adb, Toast.LENGTH_LONG).show();
            refresh();
            return;
        }
        ContentResolver cr = getContentResolver();
        int now = Settings.Global.getInt(cr, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
        int wanted = now == 0 ? PLUGGED_ALL : 0;
        try {
            Settings.Global.putInt(cr, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, wanted);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.toast_refused, Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void openWriteSettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // Many TVs have no such screen: the ADB command on screen is the way.
            Toast.makeText(this, R.string.toast_no_screen, Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    private int readTimeout() {
        return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_TIMEOUT_MS);
    }

    private boolean hasSecureSettings() {
        return checkSelfPermission(WRITE_SECURE) == PackageManager.PERMISSION_GRANTED;
    }

    private void refresh() {
        int ms = readTimeout();
        timeoutRow.setText(getString(R.string.row_timeout, describe(ms)));

        int stay = Settings.Global.getInt(getContentResolver(), Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
        awakeRow.setText(getString(R.string.row_awake, getString(stay != 0 ? R.string.on : R.string.off)));

        String pkg = getPackageName();
        StringBuilder text = new StringBuilder();
        boolean canWrite = Settings.System.canWrite(this);
        boolean secure = hasSecureSettings();
        if (!canWrite) {
            text.append(getString(R.string.help_write)).append("\nadb shell appops set ").append(pkg)
                    .append(" WRITE_SETTINGS allow\n\n");
        }
        if (!secure) {
            text.append(getString(R.string.help_secure)).append("\nadb shell pm grant ").append(pkg)
                    .append(" android.permission.WRITE_SECURE_SETTINGS\n\n");
        }
        if (canWrite && secure) text.append(getString(R.string.help_ready)).append("\n\n");
        text.append(getString(R.string.help_vendor));
        help.setText(text);
    }

    private String describe(int ms) {
        if (Timeouts.isNever(ms)) return getString(R.string.never);
        int minutes = Timeouts.minutes(ms);
        if (minutes >= 60 && minutes % 60 == 0) return getString(R.string.hours, minutes / 60);
        return getString(R.string.minutes, minutes);
    }

    // ---------------------------------------------------------------------------------------------------------

    private TextView row() {
        TextView row = label("", 26, 0xFFFFFFFF);
        row.setFocusable(true);
        row.setClickable(true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), dp(18), dp(24), dp(18));
        row.setBackgroundResource(R.drawable.row_background);
        return row;
    }

    private TextView label(CharSequence text, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams spaced(int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        lp.bottomMargin = dp(bottomDp);
        return lp;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
