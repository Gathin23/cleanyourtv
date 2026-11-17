package io.github.luyzfernando08.wakeapp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The module card on the launcher's Home (and the app's own tile): the chosen app as a large card (OK picks
 * another), then switch the feature on or off, how long to wait after the screen turns on, and a button to try it
 * now. Each row changes on OK.
 */
public class SettingsActivity extends Activity {
    private LinearLayout hero;
    private TextView enabled, link, delay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.page(this);
        Ui.heading(root, R.string.app_name, R.string.hint);

        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 16);
        hero.setPadding(pad, pad, pad, pad);
        hero.setFocusable(true);
        hero.setClickable(true);
        hero.setBackground(Ui.card(this, 24, false));
        hero.setOnClickListener(v -> startActivity(new Intent(this, PickerActivity.class)));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(Ui.dp(this, 560),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        heroLp.bottomMargin = Ui.dp(this, 12);
        root.addView(hero, heroLp);

        enabled = Ui.row(root);
        enabled.setOnClickListener(v -> {
            Prefs.setEnabled(this, !Prefs.enabled(this));
            refresh();
        });
        link = Ui.row(root);
        link.setOnClickListener(v -> {
            Prefs.setLink(this, "");
            refresh();
        });
        delay = Ui.row(root);
        delay.setOnClickListener(v -> {
            int now = Prefs.delay(this), next = Prefs.DELAYS[0];
            for (int i = 0; i < Prefs.DELAYS.length - 1; i++) {
                if (Prefs.DELAYS[i] == now) next = Prefs.DELAYS[i + 1];
            }
            Prefs.setDelay(this, next);
            refresh();
        });
        TextView test = Ui.row(root);
        test.setText(R.string.test);
        test.setOnClickListener(v -> {
            if (!Launch.open(this)) hero.requestFocus();
        });

        setContentView(root);
        hero.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        String pkg = Prefs.app(this);
        hero.removeAllViews();
        hero.addView(Ui.art(this, pkg, 192, 108));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(this, 20), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(appName(pkg));
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        name.setSingleLine(true);
        text.addView(name);
        TextView sub = new TextView(this);
        sub.setText(pkg.isEmpty() ? R.string.choose : R.string.change);
        sub.setTextColor(Ui.ACCENT);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        text.addView(sub);
        hero.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        enabled.setText(getString(R.string.enabled_state,
                getString(Prefs.enabled(this) ? R.string.on : R.string.off)));
        String address = Prefs.link(this);
        link.setVisibility(address.isEmpty() ? View.GONE : View.VISIBLE);
        link.setText(getString(R.string.link_state, address));
        delay.setText(getString(R.string.delay_state, Prefs.delay(this)));
    }

    private String appName(String pkg) {
        if (pkg.isEmpty()) return getString(R.string.none);
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return getString(R.string.missing, pkg);
        }
    }
}
