package com.lionlev.tv.fps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.IBinder;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.Locale;

public final class FpsService extends Service {
    static volatile FpsService current;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private TextView label;
    private WindowManager.LayoutParams position;
    private long last;
    private boolean added;
    private boolean received;
    private LocalSampler local;
    boolean localMode() { return local != null; }
    void postSample(double fps, String app) { handler.post(() -> { if (current == this) sample(fps, app); }); }
    void postAccessLost() { handler.post(() -> { if (current == this) stopSelf(); }); }

    boolean connected() { return received && SystemClock.elapsedRealtime() - last < 3000; }
    private final Runnable watchdog = new Runnable() {
        public void run() {
            long age = SystemClock.elapsedRealtime() - last;
            if (age > 30000) { stopSelf(); return; }
            if (age > 3000) label.setVisibility(View.GONE);
            handler.postDelayed(this, 250);
        }
    };

    public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("fps", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, "fps").setSmallIcon(R.drawable.icon)
                .setContentTitle(getString(R.string.active)).setOngoing(true).build());
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        windows = getSystemService(WindowManager.class);
        label = new TextView(this);
        label.setTextSize(14);
        label.setTextColor(0xffffffff);
        label.setTextDirection(View.TEXT_DIRECTION_LTR);
        label.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        label.setGravity(Gravity.LEFT);
        label.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x6614242c);
        background.setStroke(dp(1), 0x33ffffff);
        background.setCornerRadius(dp(6));
        label.setBackground(background);
        label.setVisibility(View.GONE);
        position = new WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        position.x = dp(12);
        position.y = dp(12);
        applyPosition();
        try { windows.addView(label, position); added = true; }
        catch (RuntimeException error) { stopSelf(); return; }
        if (LocalSampler.available(this)) local = new LocalSampler(this);
        current = this;
        last = SystemClock.elapsedRealtime();
        handler.post(watchdog);
        if (local != null) local.start();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    void applyPosition() {
        int corner = getSharedPreferences("overlay", 0).getInt("corner", 0);
        position.gravity = (corner >= 2 ? Gravity.BOTTOM : Gravity.TOP)
                | (corner % 2 == 1 ? Gravity.RIGHT : Gravity.LEFT);
        if (added) windows.updateViewLayout(label, position);
    }

    void sample(double fps, String app) {
        last = SystemClock.elapsedRealtime();
        received = true;
        // Home is never a valid measurement target. The host also resolves the default Home app.
        if (app == null || app.equals("com.gathin.tvhome") || !Double.isFinite(fps) || fps < 0 || fps > 1000) {
            label.setVisibility(View.GONE);
            return;
        }
        label.setText(String.format(Locale.US, "%.1f FPS", fps));
        label.setVisibility(View.VISIBLE);
    }

    public int onStartCommand(Intent intent, int flags, int id) { return START_NOT_STICKY; }
    public IBinder onBind(Intent intent) { return null; }
    public void onDestroy() {
        if (local != null) local.close();
        if (current == this) current = null;
        handler.removeCallbacksAndMessages(null);
        if (added) windows.removeView(label);
        super.onDestroy();
    }
}
