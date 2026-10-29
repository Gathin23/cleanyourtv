package io.github.maxhayim.brightness;

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
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds the dimming layer: one full-screen window over every app that never takes focus or keys, so the remote
 * keeps controlling what is on screen. Runs only while there is a layer to hold or a schedule to follow,
 * and stops itself otherwise. It redraws whenever the settings change and at each start and end of the night.
 * With the TV picture option on, it also sets the TV's own picture for the night and puts the day's back after.
 */
public class DimService extends Service {
    private static final String CHANNEL = "dimming";
    private static final int NOTIFICATION_ID = 1;
    /** Checked at least this often while a schedule runs, so a clock change or a TV standby can't leave it stale. */
    private static final long MAX_WAIT_MS = 5 * 60_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    /** The TV picture's binder calls, one at a time and off the main thread. */
    private final ExecutorService tvWork = Executors.newSingleThreadExecutor();
    private TvPicture tv;
    /** The night picture last sent to the TV, so a slider change sends only what moved. Used on tvWork only. */
    private Dimming.Picture tvSent;
    private WindowManager wm;
    private View layer;
    /** Whether the layer window keeps touches, which lets Android draw it darker than 80 %. */
    private boolean layerKeepsTouches;
    /** Kept in a field: SharedPreferences holds its listeners weakly. */
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, key) -> apply();
    private final Runnable tick = this::apply;
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            apply();
        }
    };

    /** Starts the service when the settings need it; harmless when they don't, as it stops itself at once. */
    static void start(Context c) {
        Dimming.Settings s = Prefs.load(c);
        int minute = minuteNow();
        boolean restoreTv = Prefs.tvAtNight(c) && !Dimming.tvNightNow(s, minute);
        if (!Dimming.needsService(s, minute) && !restoreTv) return;
        try {
            c.startForegroundService(new Intent(c, DimService.class));
        } catch (RuntimeException e) {
            // Android 12+ refuses a foreground start from the background; the next open of the app starts it.
            Log.w(Prefs.TAG, "service not started: " + e.getMessage());
        }
    }

    static boolean canDraw(Context c) {
        return Settings.canDrawOverlays(c);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = getSystemService(WindowManager.class);
        tv = new TvPicture(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_foreground)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        Prefs.of(this).registerOnSharedPreferenceChangeListener(prefsListener);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(clockReceiver, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        apply();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(tick);
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(prefsListener);
        unregisterReceiver(clockReceiver);
        removeLayer();
        tvWork.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Draws what the settings ask for at this minute, plans the next check, or stops when nothing is left to do. */
    private void apply() {
        main.removeCallbacks(tick);
        Dimming.Settings s = Prefs.load(this);
        int minute = minuteNow();
        Dimming.Level level = Dimming.level(s, minute);
        if (level.visible() && canDraw(this)) showLayer(Dimming.color(level));
        else removeLayer();
        boolean stop = !Dimming.needsService(s, minute);
        boolean tvNight = Dimming.tvNightNow(s, minute);
        if (tvNight || Prefs.tvAtNight(this)) {
            // The TV picture first, so a stop waits for the day's picture to be back.
            tvWork.execute(() -> {
                syncTv(s, tvNight);
                if (stop) main.post(this::stopSelf);
            });
        } else if (stop) {
            stopSelf();
        }
        if (stop) return;
        int next = Dimming.minutesToNextChange(s, minute);
        if (next > 0) {
            Calendar now = Calendar.getInstance();
            long toBoundary = next * 60_000L - now.get(Calendar.SECOND) * 1000L - now.get(Calendar.MILLISECOND);
            main.postDelayed(tick, Math.max(1000, Math.min(MAX_WAIT_MS, toBoundary)));
        }
    }

    /** Runs on tvWork. Night: remember the day's picture, then set the night one. Day: put the day's back. */
    private void syncTv(Dimming.Settings s, boolean night) {
        if (!tv.available()) return;
        boolean atNight = Prefs.tvAtNight(this);
        if (night && !atNight) {
            Dimming.Picture now = tv.read();
            if (now == null) return; // Not allowed yet: the app shows the prompt.
            Prefs.saveTvDay(this, Dimming.dayPicture(now, s.tvNight, Prefs.tvDay(this)));
            if (tv.write(s.tvNight, now)) {
                tvSent = s.tvNight;
                Prefs.setTvAtNight(this, true);
            }
        } else if (night && !s.tvNight.equals(tvSent)) {
            if (tv.write(s.tvNight, tvSent != null ? tvSent : tv.read())) tvSent = s.tvNight;
        } else if (!night && atNight) {
            Dimming.Picture day = Prefs.tvDay(this);
            if (tv.write(day != null ? day : Dimming.Picture.DAY, tv.read())) {
                tvSent = null;
                Prefs.setTvAtNight(this, false);
            }
        }
    }

    private void showLayer(int color) {
        boolean opaque = Dimming.keepsTouches(color);
        if (layer != null) {
            layer.setBackgroundColor(color);
            if (opaque != layerKeepsTouches) {
                wm.updateViewLayout(layer, params(opaque));
                layerKeepsTouches = opaque;
            }
            return;
        }
        View v = new View(this);
        v.setBackgroundColor(color);
        try {
            wm.addView(v, params(opaque));
            layer = v;
            layerKeepsTouches = opaque;
        } catch (RuntimeException e) {
            Log.w(Prefs.TAG, "layer not shown (display over other apps not allowed?): " + e.getMessage());
        }
    }

    private void removeLayer() {
        if (layer == null) return;
        try {
            wm.removeView(layer);
        } catch (RuntimeException ignored) {
            // Already gone with the window manager.
        }
        layer = null;
    }

    /**
     * Since Android 12 a window that lets touches through is drawn at most 80 % opaque, whatever its colour says.
     * Darker than that, the layer keeps touches instead: TVs have no touchscreen, and the remote's keys never come
     * here (not focusable); only an air-mouse pointer can't click through then.
     */
    private static WindowManager.LayoutParams params(boolean keepTouches) {
        int touch = keepTouches ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | touch
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        p.setTitle("Brightness");
        return p;
    }

    static int minuteNow() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }
}
