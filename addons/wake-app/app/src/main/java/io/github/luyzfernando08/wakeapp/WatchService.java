package io.github.luyzfernando08.wakeapp;

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
import android.os.SystemClock;

/**
 * Opens the chosen app whenever the screen turns on (the box waking from standby). SCREEN_ON is only delivered
 * to a receiver registered at run time, so this foreground service keeps one registered; it does nothing else.
 *
 * The install script talks to it with extras, since a TV has no keyboard worth typing a link on:
 * {@code --es app <package>}, {@code --es link <uri>} and {@code --ez test true}.
 */
public class WatchService extends Service {
    static final String EXTRA_BOOT = "boot";
    private static final String CHANNEL = "watch";
    /** A screen that flickers on and off must not open the app twice. */
    private static final long MIN_GAP_MS = 5000;
    /** After a boot the home screen is still coming up; opening the app first would be covered by it. */
    private static final long BOOT_EXTRA_MS = 8000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastOpen;

    private final Runnable open = new Runnable() {
        @Override
        public void run() {
            // The switch or the app may have changed while this waited out its delay.
            if (!Prefs.enabled(WatchService.this) || Prefs.app(WatchService.this).isEmpty()) return;
            long now = SystemClock.elapsedRealtime();
            if (lastOpen != 0 && now - lastOpen < MIN_GAP_MS) return;
            // Only an open that happened starts the cooldown: a missing app must not block the next wake.
            if (Launch.open(WatchService.this)) lastOpen = now;
        }
    };

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Screen off cancels a pending open: the TV was switched off again before the delay ran out.
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) handler.removeCallbacks(open);
            else schedule(0);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.watching),
                NotificationManager.IMPORTANCE_MIN));
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.watching))
                .build();
        startForeground(1, notification);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.hasExtra("app")) Prefs.setApp(this, intent.getStringExtra("app"));
            if (intent.hasExtra("link")) Prefs.setLink(this, intent.getStringExtra("link"));
            if (intent.getBooleanExtra("test", false)) Launch.open(this);
            else if (intent.getBooleanExtra(EXTRA_BOOT, false)) schedule(BOOT_EXTRA_MS);
        }
        return START_STICKY;
    }

    private void schedule(long extraMs) {
        if (!Prefs.enabled(this) || Prefs.app(this).isEmpty()) return;
        handler.removeCallbacks(open);
        handler.postDelayed(open, Prefs.delay(this) * 1000L + extraMs);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(open);
        unregisterReceiver(screen);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
