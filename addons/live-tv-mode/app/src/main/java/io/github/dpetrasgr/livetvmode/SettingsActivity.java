package io.github.dpetrasgr.livetvmode;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The module card on the launcher's Home and the app's own tile: whether the accessibility helper is on, the mode
 * on or off, the Home presses that show the home screen, the wake rule and the Live TV app. Each row changes on OK.
 */
public class SettingsActivity extends Activity {
    private TextView helper, enabled, presses, wake, app;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0D12);
        int pad = dp(48);
        root.setPadding(pad, pad, pad, pad);

        TextView title = text(R.string.app_name, 34, Color.WHITE);
        root.addView(title);
        TextView hint = text(R.string.hint, 16, 0xB3FFFFFF);
        hint.setPadding(0, dp(8), 0, dp(24));
        root.addView(hint);

        helper = row(root);
        helper.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                // the TV has no accessibility page; the install script switches the helper on
            }
        });
        enabled = row(root);
        enabled.setOnClickListener(v -> {
            Prefs.setEnabled(this, !Prefs.enabled(this));
            refresh();
        });
        presses = row(root);
        presses.setOnClickListener(v -> {
            Prefs.setPresses(this, Mode.nextPresses(Prefs.presses(this)));
            refresh();
        });
        wake = row(root);
        wake.setOnClickListener(v -> {
            Prefs.setWakeFromApps(this, !Prefs.wakeFromApps(this));
            refresh();
        });
        app = row(root);
        app.setOnClickListener(v -> pickLiveTv());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B0D12);
        scroll.addView(root);
        setContentView(scroll);
        enabled.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean on = Prefs.enabled(this);
        helper.setText(Prefs.helperOn(this) ? R.string.helper_on : R.string.helper_off);
        enabled.setText(getString(R.string.enabled_state, getString(on ? R.string.on : R.string.off)));
        presses.setText(getString(R.string.presses_state, String.valueOf(Prefs.presses(this))));
        wake.setText(getString(R.string.wake_state, getString(Prefs.wakeFromApps(this) ? R.string.on : R.string.off)));
        String pkg = Prefs.liveTv(this);
        app.setText(getString(R.string.app_state, pkg.isEmpty() ? getString(R.string.app_none) : label(pkg)));
        for (TextView t : new TextView[]{presses, wake, app}) t.setAlpha(on ? 1f : 0.5f);
    }

    /** Every app with a TV entry point, by name. */
    private void pickLiveTv() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size()];
        String current = Prefs.liveTv(this);
        int checked = -1;
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    Prefs.setLiveTv(this, apps.get(which)[1]);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private TextView text(int res, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
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
        bg.addState(new int[]{android.R.attr.state_focused}, shape(0x40FFFFFF));
        bg.addState(new int[]{}, shape(0x14FFFFFF));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        root.addView(row, lp);
        return row;
    }

    private GradientDrawable shape(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(16));
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
