package io.github.abner97.remotebuttons;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * Opens an app for a remote button without filtering keys. Some firmware (Hisense, TCL Google TV) stops a held
 * volume key from repeating once any accessibility service filters keys; this service filters nothing. It reads
 * the window manager's key log instead (READ_LOGS, granted over ADB) and acts on the presses in keys.txt.
 */
public class KeyLogService extends AccessibilityService {
    static final String TAG = "RemoteButtons";
    /** The last button handled, for the card's screen. Same process, so a field is enough. */
    static volatile String lastHandled = "";

    private volatile boolean running;
    private volatile Process proc;
    private Map<Integer, String> map;
    private long mapStamp = -1;
    private int lastKey;
    private long lastAt;

    /** The owner's own button file; without it the Hisense defaults apply. */
    static File keysFile(Context c) {
        return new File(c.getExternalFilesDir(null), "keys.txt");
    }

    static Map<Integer, String> load(Context c) {
        File f = keysFile(c);
        try {
            if (f.isFile()) return KeyMap.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "could not read " + f, e);
        }
        return KeyMap.parse(KeyMap.DEFAULTS);
    }

    @Override
    protected void onServiceConnected() {
        running = true;
        new Thread(this::readLog, "remote-buttons").start();
    }

    private void readLog() {
        while (running) {
            try {
                proc = new ProcessBuilder("logcat", "-v", "brief", "-T", "1", "-s", "WindowManager:D")
                        .redirectErrorStream(true).start();
                BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                in.readLine(); // -T 1 starts with the newest old line: never act on a press from before
                String line;
                while (running && (line = in.readLine()) != null) {
                    int key = KeyMap.pressed(line);
                    if (key >= 0) onPress(key);
                }
            } catch (Exception e) {
                Log.w(TAG, "log reader stopped", e);
            }
            if (running) SystemClock.sleep(1000); // logcat ended: start it again
        }
    }

    private void onPress(int keyCode) {
        // keys.txt is read again when it changes, so a new one needs no restart.
        long stamp = keysFile(this).lastModified();
        if (map == null || stamp != mapStamp) {
            map = load(this);
            mapStamp = stamp;
        }
        String target = map.get(keyCode);
        if (target == null) return;
        // The firmware logs some presses twice, about 150 ms apart.
        long now = SystemClock.uptimeMillis();
        if (keyCode == lastKey && now - lastAt < 500) return;
        lastKey = keyCode;
        lastAt = now;

        Intent i;
        if (target.equals(KeyMap.SETTINGS)) {
            i = new Intent(Settings.ACTION_SETTINGS);
        } else if (target.equals(KeyMap.HOME)) {
            i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        } else {
            i = getPackageManager().getLeanbackLaunchIntentForPackage(target);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(target);
            if (i == null) {
                Log.w(TAG, keyCode + ": " + target + " is not installed or is disabled");
                return;
            }
        }
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            lastHandled = keyCode + " → " + target;
            Log.i(TAG, "key " + keyCode + " -> " + target);
        } catch (Exception e) {
            Log.w(TAG, "could not open " + target, e);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        running = false;
        Process p = proc;
        if (p != null) p.destroy();
        super.onDestroy();
    }
}
