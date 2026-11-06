package io.github.knochango.remotebuttons;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Opens the app a remote button was given, without filtering keys, so held keys (volume) keep their auto-repeat.
 *
 * The window manager on some builds logs every key it handles at debug level (KeyLog). This service reads that log
 * (READ_LOGS, granted over ADB) and acts on the key-down. It is an accessibility service only so that Android binds
 * it again after every boot; it asks for no key filtering and ignores every event. Nothing is consumed: a button that
 * still does something of its own (Netflix with Netflix installed) does both.
 */
public class KeyLogService extends AccessibilityService {
    private static final String TAG = "RemoteButtons";
    /** The log can print the same key-down twice, about 150 ms apart. */
    private static final long SAME_PRESS_MS = 600;

    /** The settings screen waiting for the next button, or null. Called on the main thread. */
    interface Learner {
        void learned(int keyCode);
    }

    static volatile boolean bound;
    static volatile int lastKey;
    static volatile Learner learner;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile Process logcat;
    private int previousKey;
    private long previousAt;

    @Override
    protected void onServiceConnected() {
        bound = true;
        running = true;
        new Thread(this::readLoop, "key-log").start();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        bound = false;
        running = false;
        Process p = logcat;
        if (p != null) p.destroy();
        super.onDestroy();
    }

    private void readLoop() {
        while (running) {
            try {
                // -T 1 starts at the newest line, so a restarted reader does not replay old presses; that one line
                // is skipped below.
                logcat = new ProcessBuilder("logcat", "-v", "brief", "-T", "1", "-s", "WindowManager:D")
                        .redirectErrorStream(true).start();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(logcat.getInputStream()))) {
                    in.readLine();
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        int key = KeyLog.keyDown(line);
                        if (key != 0) onKeyDown(key);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "logcat reader stopped", e);
            }
            if (running) SystemClock.sleep(2000);
        }
    }

    private void onKeyDown(int keyCode) {
        long now = SystemClock.uptimeMillis();
        if (keyCode == previousKey && now - previousAt < SAME_PRESS_MS) return;
        previousKey = keyCode;
        previousAt = now;
        lastKey = keyCode;
        Learner waiting = learner;
        if (waiting != null && !KeyLog.NAVIGATION.contains(keyCode)) {
            learner = null;
            main.post(() -> waiting.learned(keyCode));
            return;
        }
        String pkg = Prefs.app(this, keyCode);
        if (!pkg.isEmpty()) main.post(() -> Launch.open(this, pkg));
    }
}
