package io.github.luyzfernando08.tvstatic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.SystemClock;

/**
 * Plays the static whenever the screen turns on (the box waking from standby). SCREEN_ON is only delivered to a
 * receiver registered at run time, so this foreground service keeps one registered; it does nothing else.
 */
public class WatchService extends Service {
    static final String EXTRA_PLAY = "play";
    private static final String CHANNEL = "watch";
    /** A screen that flickers on and off must not replay the effect over itself. */
    private static final long MIN_GAP_MS = 5000;

    private long lastPlay;

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            play();
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
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(EXTRA_PLAY, false)) play();
        return START_STICKY;
    }

    private void play() {
        if (!Prefs.enabled(this)) return;
        long now = SystemClock.elapsedRealtime();
        if (lastPlay != 0 && now - lastPlay < MIN_GAP_MS) return;
        lastPlay = now;
        startActivity(new Intent(this, StaticActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
