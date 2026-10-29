package io.github.maxhayim.quiethours;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.Calendar;

/**
 * Keeps the media volume under the cap: whenever the volume changes (the remote, an app, an ad) or the output
 * changes, it checks and brings the volume back down to the cap if needed. Also checks at each start and end of
 * the quiet hours. Runs while Quiet Hours is on, and stops itself when it is switched off.
 */
public class CapService extends Service {
    private static final String CHANNEL = "cap";
    /** Sent by the system on every volume change; not in the SDK's constants but sent on every Android version. */
    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String DEVICES_CHANGED = "android.media.STREAM_DEVICES_CHANGED_ACTION";
    private static final long MAX_WAIT_MS = 5 * 60_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioManager audio;
    /** Kept in a field: SharedPreferences holds its listeners weakly. */
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, key) -> check();
    private final Runnable tick = this::check;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            check();
        }
    };

    static void start(Context c) {
        if (!Quiet.needsService(Prefs.load(c))) return;
        try {
            c.startForegroundService(new Intent(c, CapService.class));
        } catch (RuntimeException e) {
            // Android 12+ refuses a foreground start from the background; the next open of the app starts it.
            Log.w(Prefs.TAG, "service not started: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audio = getSystemService(AudioManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_foreground)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
        Prefs.of(this).registerOnSharedPreferenceChangeListener(prefsListener);
        IntentFilter f = new IntentFilter();
        f.addAction(VOLUME_CHANGED);
        f.addAction(DEVICES_CHANGED);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(changes, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        check();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(tick);
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(prefsListener);
        unregisterReceiver(changes);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void check() {
        main.removeCallbacks(tick);
        Quiet.Settings s = Prefs.load(this);
        if (!Quiet.needsService(s)) {
            stopSelf();
            return;
        }
        int minute = minuteNow();
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (Quiet.mustLower(s, minute, volume, max, onHeadphones(audio), audio.isVolumeFixed(),
                audio.isStreamMute(AudioManager.STREAM_MUSIC))) {
            int cap = Quiet.capIndex(s.cap, max);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, cap, AudioManager.FLAG_SHOW_UI);
            Log.i(Prefs.TAG, "volume " + volume + " lowered to the cap " + cap + " of " + max);
        }
        int next = Quiet.minutesToNextChange(s, minute);
        long wait = MAX_WAIT_MS;
        if (next > 0) {
            Calendar now = Calendar.getInstance();
            wait = Math.min(wait, next * 60_000L - now.get(Calendar.SECOND) * 1000L - now.get(Calendar.MILLISECOND));
        }
        main.postDelayed(tick, Math.max(1000, wait));
    }

    /** True while headphones (Bluetooth, wired or USB) are connected, which is where Android sends media then. */
    static boolean onHeadphones(AudioManager audio) {
        for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_USB_HEADSET) return true;
            if (Build.VERSION.SDK_INT >= 31 && t == AudioDeviceInfo.TYPE_BLE_HEADSET) return true;
        }
        return false;
    }

    static int minuteNow() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }
}
