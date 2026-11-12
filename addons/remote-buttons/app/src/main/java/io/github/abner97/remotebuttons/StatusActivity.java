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

/**
 * The card's screen: is it on, can it read the key log, and which button opens what.
 *
 * <p>Opened by the launcher's module card (DOCK_CARD) and by the app's own tile. Read-only: it changes nothing,
 * so fixing a problem it reports means running install.sh again or using the TV's Accessibility settings.
 */
public class StatusActivity extends Activity {
    /** Large enough to read from the sofa. */
    private static final float TEXT_SIZE_SP = 22;
    /** Extra space between lines, so the button list stays readable at a distance. */
    private static final float LINE_SPACING_MULTIPLIER = 1.3f;
    /** Margin around the text, inside the TV's overscan-safe area. */
    private static final int PADDING_DP = 48;

    /** The one view on screen; its text is rebuilt on every resume. */
    private TextView statusView;

    /** Created by Android when the card or the app's tile is opened; the screen is built in {@link #onCreate}. */
    public StatusActivity() {
        super();
    }

    /** Builds the screen: a scrolling white-on-black text view, filled in {@link #onResume()}. */
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

    /** Refreshes the text on every return, since the owner may have just turned the service on in Settings. */
    @Override
    protected void onResume() {
        super.onResume();
        statusView.setText(statusText());
    }

    /**
     * The full status: service on or off, log access, where the map comes from, each button with what it opens,
     * and the last button handled in this process.
     *
     * @return the text for {@link #statusView}
     */
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

    /**
     * Whether {@link KeyLogService} is switched on in the TV's accessibility settings.
     *
     * @return true when this app's service is in {@code enabled_accessibility_services}
     */
    private boolean isServiceOn() {
        String enabledServices =
                Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(getPackageName() + "/");
    }

    /**
     * Whether READ_LOGS has been granted. Without it logcat shows this app only its own lines, so no button
     * press is ever seen.
     *
     * @return true once {@code pm grant ... READ_LOGS} has run (install.sh does it)
     */
    private boolean hasLogAccess() {
        return checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * A target as the owner reads it.
     *
     * @param target a package name, {@link KeyMap#SETTINGS_TARGET} or {@link KeyMap#HOME_TARGET}
     * @return the app's name (marked when disabled or not installed), or what the named target opens
     */
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
