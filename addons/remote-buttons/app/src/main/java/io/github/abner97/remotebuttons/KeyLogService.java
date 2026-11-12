package io.github.abner97.remotebuttons;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Opens an app for a remote button without filtering keys. Some firmware (Hisense, TCL Google TV) stops a held
 * volume key from repeating once any accessibility service filters keys; this service filters nothing. It reads
 * the window manager's key log instead (READ_LOGS, granted over ADB) and acts on the presses in the button map.
 */
public class KeyLogService extends AccessibilityService {
    private static final String TAG = "RemoteButtons";
    /** The window manager's key lines only; -T 1 starts at the newest line instead of replaying the buffer. */
    private static final String[] LOGCAT_COMMAND = {"logcat", "-v", "brief", "-T", "1", "-s", "WindowManager:D"};
    /** The firmware logs some presses twice, about 150 ms apart. */
    private static final long ECHO_WINDOW_MS = 500;
    /** Wait before starting logcat again after it ends. */
    private static final long LOGCAT_RESTART_DELAY_MS = 1000;

    /** The last button handled, for the card's screen. Same process, so a field is enough. */
    static volatile String lastHandledButton = "";

    private volatile boolean running;
    private volatile Process logcatProcess;
    private KeysFile keysFile;
    private int previousKeyCode;
    private long previousPressUptimeMs;

    @Override
    protected void onServiceConnected() {
        keysFile = new KeysFile(this);
        running = true;
        new Thread(this::readLogWhileRunning, "remote-buttons").start();
    }

    @Override
    public void onDestroy() {
        running = false;
        Process runningLogcat = logcatProcess;
        if (runningLogcat != null) runningLogcat.destroy();
        super.onDestroy();
    }

    private void readLogWhileRunning() {
        while (running) {
            try {
                readLogUntilStopped();
            } catch (IOException logcatError) {
                Log.w(TAG, "log reader stopped", logcatError);
            }
            if (running) SystemClock.sleep(LOGCAT_RESTART_DELAY_MS);
        }
    }

    private void readLogUntilStopped() throws IOException {
        logcatProcess = new ProcessBuilder(LOGCAT_COMMAND).redirectErrorStream(true).start();
        BufferedReader logReader = new BufferedReader(new InputStreamReader(logcatProcess.getInputStream()));
        logReader.readLine(); // the one line from before -T 1: never act on an old press
        String logLine;
        while (running && (logLine = logReader.readLine()) != null) {
            int keyCode = KeyMap.keyCodeOfPress(logLine);
            if (keyCode != KeyMap.NO_PRESS) onButtonPressed(keyCode);
        }
    }

    private void onButtonPressed(int keyCode) {
        String target = keysFile.targetsByKeyCode().get(keyCode);
        if (target == null || isEchoOfPreviousPress(keyCode)) return;

        Intent openTarget = intentFor(target);
        if (openTarget == null) {
            Log.w(TAG, keyCode + ": " + target + " is not installed or is disabled");
            return;
        }
        try {
            startActivity(openTarget.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            lastHandledButton = keyCode + " → " + target;
            Log.i(TAG, "key " + keyCode + " -> " + target);
        } catch (RuntimeException startError) {
            Log.w(TAG, "could not open " + target, startError);
        }
    }

    /** True for a second log line of the same press. */
    private boolean isEchoOfPreviousPress(int keyCode) {
        long nowUptimeMs = SystemClock.uptimeMillis();
        boolean isEcho = keyCode == previousKeyCode && nowUptimeMs - previousPressUptimeMs < ECHO_WINDOW_MS;
        previousKeyCode = keyCode;
        previousPressUptimeMs = nowUptimeMs;
        return isEcho;
    }

    /** The intent that opens a target, or null when its app is missing or disabled. */
    private Intent intentFor(String target) {
        if (target.equals(KeyMap.SETTINGS_TARGET)) return new Intent(Settings.ACTION_SETTINGS);
        if (target.equals(KeyMap.HOME_TARGET)) {
            return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        }
        PackageManager packageManager = getPackageManager();
        Intent tvLaunchIntent = packageManager.getLeanbackLaunchIntentForPackage(target);
        return tvLaunchIntent != null ? tvLaunchIntent : packageManager.getLaunchIntentForPackage(target);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
