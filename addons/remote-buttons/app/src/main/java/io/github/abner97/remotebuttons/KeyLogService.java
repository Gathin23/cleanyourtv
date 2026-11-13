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
 *
 * <p>It is an accessibility service only so Android keeps it running and binds it again after a reboot; it asks
 * for no key events and no window content ({@code res/xml/accessibility.xml}). The log is read on one background
 * thread, which starts when the service is bound and stops when it is destroyed.
 */
public class KeyLogService extends AccessibilityService {
    /** Logcat tag for this add-on's own lines: {@code logcat -s RemoteButtons}. */
    private static final String TAG = "RemoteButtons";
    /** The window manager's key lines only; -T 1 starts at the newest line instead of replaying the buffer. */
    private static final String[] LOGCAT_COMMAND = {"logcat", "-v", "brief", "-T", "1", "-s", "WindowManager:D"};
    /** The firmware logs some presses twice, about 150 ms apart. */
    private static final long ECHO_WINDOW_MS = 500;
    /** Wait before starting logcat again after it ends. */
    private static final long LOGCAT_RESTART_DELAY_MS = 1000;

    /** The last button handled, for the card's screen. Same process, so a field is enough. */
    static volatile String lastHandledButton = "";

    /** Set while the service is bound; the reader thread stops once it turns false. */
    private volatile boolean running;
    /** The logcat being read, so {@link #onDestroy()} can end it and unblock the reader thread. */
    private volatile Process logcatProcess;
    /** The button map; used only on the reader thread. */
    private KeysFile keysFile;
    /** The keycode of the last press seen, for {@link #isEchoOfPreviousPress(int)}. */
    private int previousKeyCode;
    /** When that press happened, in {@link SystemClock#uptimeMillis()}. */
    private long previousPressUptimeMs;

    /** Created by Android when the service is switched on; nothing runs until {@link #onServiceConnected()}. */
    public KeyLogService() {
        super();
    }

    /** Android bound the service: start reading the key log on a background thread. */
    @Override
    protected void onServiceConnected() {
        keysFile = new KeysFile(this);
        running = true;
        new Thread(this::readLogWhileRunning, "remote-buttons").start();
    }

    /** The service was turned off or the app replaced: stop the reader thread and its logcat. */
    @Override
    public void onDestroy() {
        running = false;
        Process runningLogcat = logcatProcess;
        if (runningLogcat != null) runningLogcat.destroy();
        super.onDestroy();
    }

    /**
     * The reader thread's body. logcat can end on its own (a log buffer reset, low memory), so it is started
     * again after {@link #LOGCAT_RESTART_DELAY_MS} for as long as the service is running.
     */
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

    /**
     * Starts one logcat and handles each button press it prints, until logcat ends or the service stops.
     *
     * @throws IOException when logcat cannot be started or its output cannot be read
     */
    private void readLogUntilStopped() throws IOException {
        Process logcat = new ProcessBuilder(LOGCAT_COMMAND).redirectErrorStream(true).start();
        // Publish before checking running: onDestroy() clears running and then destroys the published process,
        // so whichever side runs second sees the other's write and this logcat is never left behind.
        logcatProcess = logcat;
        try (BufferedReader logReader = new BufferedReader(new InputStreamReader(logcat.getInputStream()))) {
            if (!running) return; // stopped while logcat was starting
            logReader.readLine(); // the one line from before -T 1: never act on an old press
            String logLine;
            while (running && (logLine = logReader.readLine()) != null) {
                int keyCode = KeyMap.keyCodeOfPress(logLine);
                if (keyCode != KeyMap.NO_PRESS) onButtonPressed(keyCode);
            }
        } finally {
            logcat.destroy(); // on EOF, a read error or shutdown alike, so a retry never orphans this child
            if (logcatProcess == logcat) logcatProcess = null;
        }
    }

    /**
     * Handles one pressed button. While the card's screen waits for a button to assign, an assignable press goes
     * to it instead ({@link ButtonCapture}); otherwise the mapped target is opened. Echoes of the same press and
     * unmapped buttons are ignored, and a target whose app is missing or disabled is logged rather than opened.
     *
     * @param keyCode the Android keycode the window manager logged for the press
     */
    private void onButtonPressed(int keyCode) {
        // The echo check comes first: otherwise the second line of a press just captured for the card would
        // open the app it was assigned to a moment later.
        if (isEchoOfPreviousPress(keyCode)) return;
        if (KeyMap.isAssignable(keyCode) && ButtonCapture.offerPress(keyCode)) return;

        String target = keysFile.targetsByKeyCode().get(keyCode);
        if (target == null) return;

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

    /**
     * Tells a second log line of one press from a new press, and records this press for the next call.
     *
     * @param keyCode the keycode just pressed
     * @return true when the same button was logged less than {@link #ECHO_WINDOW_MS} ago
     */
    private boolean isEchoOfPreviousPress(int keyCode) {
        long nowUptimeMs = SystemClock.uptimeMillis();
        boolean isEcho = keyCode == previousKeyCode && nowUptimeMs - previousPressUptimeMs < ECHO_WINDOW_MS;
        previousKeyCode = keyCode;
        previousPressUptimeMs = nowUptimeMs;
        return isEcho;
    }

    /**
     * Builds the intent that opens a target. For an app, its TV (leanback) entry is preferred over a phone one.
     *
     * @param target a package name, {@link KeyMap#SETTINGS_TARGET} or {@link KeyMap#HOME_TARGET}
     * @return the intent to start, or null when the target's app is missing or disabled
     */
    private Intent intentFor(String target) {
        if (target.equals(KeyMap.SETTINGS_TARGET)) return new Intent(Settings.ACTION_SETTINGS);
        if (target.equals(KeyMap.HOME_TARGET)) {
            return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        }
        PackageManager packageManager = getPackageManager();
        Intent tvLaunchIntent = packageManager.getLeanbackLaunchIntentForPackage(target);
        return tvLaunchIntent != null ? tvLaunchIntent : packageManager.getLaunchIntentForPackage(target);
    }

    /** Unused: the service subscribes to no events it needs; it exists to stay bound. */
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    /** Unused: the service gives no spoken or other feedback that could be interrupted. */
    @Override public void onInterrupt() { }
}
