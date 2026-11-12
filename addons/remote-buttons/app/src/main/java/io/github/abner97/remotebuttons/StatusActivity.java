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
    private static final float TEXT_SIZE_SP = 22;
    private static final float LINE_SPACING_MULTIPLIER = 1.3f;
    private static final int PADDING_DP = 48;

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP);
        statusView.setLineSpacing(0, LINE_SPACING_MULTIPLIER);
        int paddingPx = (int) (PADDING_DP * getResources().getDisplayMetrics().density);
        statusView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.BLACK);
        scrollView.addView(statusView);
        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusView.setText(statusText()); // again on every return: the owner may have just changed a setting
    }

    private CharSequence statusText() {
        StringBuilder status = new StringBuilder(getString(R.string.app_name)).append("\n\n");
        status.append(getString(isServiceOn() ? R.string.status_on : R.string.status_off)).append('\n');
        status.append(getString(hasLogAccess() ? R.string.logs_ok : R.string.logs_missing)).append("\n\n");

        KeysFile keysFile = new KeysFile(this);
        status.append(getString(keysFile.exists() ? R.string.map_file : R.string.map_default)).append('\n');
        for (Map.Entry<Integer, String> button : keysFile.targetsByKeyCode().entrySet()) {
            status.append("   ").append(button.getKey())
                    .append("  →  ").append(describeTarget(button.getValue())).append('\n');
        }

        String lastButton = KeyLogService.lastHandledButton;
        if (!lastButton.isEmpty()) status.append('\n').append(getString(R.string.last, lastButton));
        return status;
    }

    private boolean isServiceOn() {
        String enabledServices =
                Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(getPackageName() + "/");
    }

    private boolean hasLogAccess() {
        return checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED;
    }

    /** A target as the owner reads it: the app's name, or what "settings" and "home" mean. */
    private String describeTarget(String target) {
        if (target.equals(KeyMap.SETTINGS_TARGET)) return getString(R.string.target_settings);
        if (target.equals(KeyMap.HOME_TARGET)) return getString(R.string.target_home);
        PackageManager packageManager = getPackageManager();
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(target, 0);
            String appName = packageManager.getApplicationLabel(appInfo).toString();
            return appInfo.enabled ? appName : appName + getString(R.string.disabled);
        } catch (PackageManager.NameNotFoundException notInstalled) {
            return target + getString(R.string.not_installed);
        }
    }
}
