package com.gathin.artframe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * "Wake into art": opens art mode when the TV comes out of standby. SCREEN_ON is only delivered to a receiver
 * registered at run time, so this small foreground service keeps one registered while the switch is on, and does
 * nothing else. On TCL TVs it can wake into art only when the art button woke the TV, as TCL's own art app does:
 * the firmware records the wake key in sys.tcl.boot.reason (IR-EXHIBIT for the art button, IR-POWER for power).
 * Opening a screen from the background needs "display over other apps", which build-and-install.sh grants.
 */
public class WakeService extends Service {
    static final String WAKE_PROP = "sys.tcl.boot.reason";
    private static final String CHANNEL = "wake";
    private final Handler main = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TCL writes the wake reason as the screen comes on; give it a moment.
            main.postDelayed(() -> maybeOpen(context), 400);
        }
    };

    /** Starts or stops the service to match the setting. */
    static void sync(Context c) {
        Intent i = new Intent(c, WakeService.class);
        if ("off".equals(new Prefs(c).wake())) {
            c.stopService(i);
        } else {
            try {
                c.startForegroundService(i);
            } catch (RuntimeException e) {
                Log.w(Library.TAG, "wake watcher not started: " + e);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.wake_channel),
                NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle(getString(R.string.wake_channel))
                .build();
        startForeground(1, n);
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if ("off".equals(new Prefs(this).wake())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    static void maybeOpen(Context c) {
        String mode = new Prefs(c).wake();
        if ("off".equals(mode)) return;
        String reason = prop(WAKE_PROP);
        boolean artButton = reason.toUpperCase(java.util.Locale.ROOT).contains("EXHIBIT");
        Log.i(Library.TAG, "screen on, wake reason '" + reason + "', mode " + mode);
        if (!"any".equals(mode) && !artButton) return;
        if (!Settings.canDrawOverlays(c)) {
            Log.w(Library.TAG, "cannot open art from the background: 'display over other apps' is not allowed");
            return;
        }
        c.startActivity(new Intent(c, FrameActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    /** A system property through the getprop tool; "" when it is missing or unreadable. */
    static String prop(String name) {
        try {
            Process p = new ProcessBuilder("getprop", name).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                p.waitFor();
                return line == null ? "" : line.trim();
            }
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenOn);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
