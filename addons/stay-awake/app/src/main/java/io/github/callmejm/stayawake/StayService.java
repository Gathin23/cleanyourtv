package io.github.callmejm.stayawake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

/**
 * While the owner has this on, keep the screen from sleeping during music or video. The sleep
 * timer and screensaver run again as soon as playback pauses. Turning it off drops the audio
 * callback, so a later playback event cannot take the lock again.
 */
public class StayService extends Service {
    private static final String TAG = "StayAwake";
    private static final String CHANNEL = "stay";
    private static final int NOTIFICATION_ID = 1;
    private static final long POKE_MS = 20_000L;

    private AudioManager audio;
    private AudioManager.AudioPlaybackCallback callback;
    private PowerManager.WakeLock lock;
    private Handler handler;
    private final Runnable poke = new Runnable() {
        @Override
        public void run() {
            if (lock == null || !lock.isHeld()) return;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            try {
                // Public on the device, missing from some compile stubs.
                pm.getClass().getMethod("userActivity", long.class, boolean.class)
                        .invoke(pm, SystemClock.uptimeMillis(), false);
            } catch (ReflectiveOperationException ignored) {
                // Some builds only allow this from the system. The screen wake lock still holds.
            }
            handler.postDelayed(this, POKE_MS);
        }
    };

    /** Starts the watcher. Harmless when it is off: the service stops itself. */
    static void start(Context ctx) {
        Intent intent = new Intent(ctx, StayService.class);
        try {
            ctx.startForegroundService(intent);
        } catch (RuntimeException e) {
            // Android 12+ can refuse a foreground start from the background. The next open starts it.
            Log.w(TAG, "service not started: " + e.getMessage());
        }
    }

    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, StayService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.enabled(this)) {
            releaseWatch();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        watch();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseWatch();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void watch() {
        if (callback != null) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        lock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                "stayawake:playback");
        lock.setReferenceCounted(false);
        AudioManager.AudioPlaybackCallback registered = new AudioManager.AudioPlaybackCallback() {
            @Override
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                // A callback that was already queued can arrive after Off. Ignore it.
                if (callback != this) return;
                if (mediaPlaying(configs)) hold();
                else releaseLock();
            }
        };
        callback = registered;
        audio.registerAudioPlaybackCallback(registered, handler);
        handler.post(() -> {
            if (callback != registered) return;
            if (mediaPlaying(audio.getActivePlaybackConfigurations())) hold();
        });
    }

    /** Drop the callback and the lock. A later playback event must not acquire the lock. */
    private void releaseWatch() {
        if (callback != null) {
            audio.unregisterAudioPlaybackCallback(callback);
            callback = null;
        }
        if (handler != null) handler.removeCallbacks(poke);
        releaseLock();
        lock = null;
    }

    private void hold() {
        if (lock != null && !lock.isHeld()) lock.acquire();
        handler.removeCallbacks(poke);
        handler.post(poke);
    }

    private void releaseLock() {
        if (handler != null) handler.removeCallbacks(poke);
        if (lock != null && lock.isHeld()) lock.release();
    }

    private static boolean mediaPlaying(List<AudioPlaybackConfiguration> configs) {
        if (configs == null) return false;
        for (AudioPlaybackConfiguration c : configs) {
            if (playerState(c) != 2) continue; // PLAYER_STATE_STARTED
            AudioAttributes a = c.getAudioAttributes();
            if (a != null && a.getUsage() == AudioAttributes.USAGE_MEDIA) return true;
        }
        return false;
    }

    private static int playerState(AudioPlaybackConfiguration c) {
        try {
            return (Integer) AudioPlaybackConfiguration.class.getMethod("getPlayerState").invoke(c);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }
}
