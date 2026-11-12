package io.github.abner97.remotebuttons;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Map;

/** The card's screen: is it on, can it read the key log, and which button opens what. */
public class StatusActivity extends Activity {
    private TextView text;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        text = new TextView(this);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        text.setLineSpacing(0, 1.3f);
        int pad = (int) (48 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(text);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String services = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
        boolean on = services != null && services.contains(getPackageName() + "/");
        boolean logs = checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED;

        StringBuilder s = new StringBuilder(getString(R.string.app_name)).append("\n\n");
        s.append(getString(on ? R.string.status_on : R.string.status_off)).append('\n');
        s.append(getString(logs ? R.string.logs_ok : R.string.logs_missing)).append("\n\n");
        s.append(getString(KeyLogService.keysFile(this).isFile() ? R.string.map_file : R.string.map_default))
                .append('\n');
        for (Map.Entry<Integer, String> e : KeyLogService.load(this).entrySet()) {
            s.append("   ").append(e.getKey()).append("  →  ").append(describe(e.getValue())).append('\n');
        }
        String last = KeyLogService.lastHandled;
        if (!last.isEmpty()) s.append('\n').append(getString(R.string.last, last));
        text.setText(s);
    }

    private String describe(String target) {
        if (target.equals(KeyMap.SETTINGS)) return getString(R.string.target_settings);
        if (target.equals(KeyMap.HOME)) return getString(R.string.target_home);
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(target, 0);
            return getPackageManager().getApplicationLabel(ai) + (ai.enabled ? "" : getString(R.string.disabled));
        } catch (PackageManager.NameNotFoundException e) {
            return target + getString(R.string.not_installed);
        }
    }
}
