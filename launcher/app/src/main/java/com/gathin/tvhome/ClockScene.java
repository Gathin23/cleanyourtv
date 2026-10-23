package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;

/**
 * Controller for the Islamic Prayer Reminder screensaver.
 * Displays a big OLED-black screen with a circular green timer, live countdown,
 * next prayer status, and daily prayer schedule for Kota Bekasi.
 * Includes subtle anti-burn-in drift every 60 seconds.
 */
final class ClockScene {

    private static final long TICK_MS = 1000L;
    private static final long DRIFT_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View root;
    private final Context context;
    private final PrayerTimeManager prayerManager;

    // Views
    private final View container;
    private final PrayerCircleTimerView circleTimer;
    private final TextView tvNextName;
    private final TextView tvCountdown;
    private final TextView tvTargetTime;
    private final TextView tvInterval;

    // Bottom schedule pills
    private final LinearLayout pillSubuh;
    private final LinearLayout pillDzuhur;
    private final LinearLayout pillAshar;
    private final LinearLayout pillMaghrib;
    private final LinearLayout pillIsya;

    private final TextView lblSubuh;
    private final TextView lblDzuhur;
    private final TextView lblAshar;
    private final TextView lblMaghrib;
    private final TextView lblIsya;

    private final TextView timeSubuh;
    private final TextView timeDzuhur;
    private final TextView timeAshar;
    private final TextView timeMaghrib;
    private final TextView timeIsya;

    private final Runnable tickSecond = new Runnable() {
        @Override
        public void run() {
            updatePrayerUi();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private final Runnable tickDrift = new Runnable() {
        @Override
        public void run() {
            driftContainer();
            handler.postDelayed(this, DRIFT_MS);
        }
    };

    ClockScene(View root) {
        this.root = root;
        this.context = root.getContext();
        this.prayerManager = PrayerTimeManager.getInstance(context);

        container = root.findViewById(R.id.prayerScreensaverContainer);
        circleTimer = root.findViewById(R.id.prayerCircleTimer);
        tvNextName = root.findViewById(R.id.tvSolatNextName);
        tvCountdown = root.findViewById(R.id.tvSolatCountdown);
        tvTargetTime = root.findViewById(R.id.tvSolatTargetTime);
        tvInterval = root.findViewById(R.id.tvSolatInterval);

        pillSubuh = root.findViewById(R.id.pillSubuh);
        pillDzuhur = root.findViewById(R.id.pillDzuhur);
        pillAshar = root.findViewById(R.id.pillAshar);
        pillMaghrib = root.findViewById(R.id.pillMaghrib);
        pillIsya = root.findViewById(R.id.pillIsya);

        lblSubuh = root.findViewById(R.id.lblSubuh);
        lblDzuhur = root.findViewById(R.id.lblDzuhur);
        lblAshar = root.findViewById(R.id.lblAshar);
        lblMaghrib = root.findViewById(R.id.lblMaghrib);
        lblIsya = root.findViewById(R.id.lblIsya);

        timeSubuh = root.findViewById(R.id.timeSubuh);
        timeDzuhur = root.findViewById(R.id.timeDzuhur);
        timeAshar = root.findViewById(R.id.timeAshar);
        timeMaghrib = root.findViewById(R.id.timeMaghrib);
        timeIsya = root.findViewById(R.id.timeIsya);
    }

    void start() {
        prayerManager.fetchPrayerTimesAsync(context, this::updatePrayerUi);
        handler.removeCallbacks(tickSecond);
        handler.removeCallbacks(tickDrift);

        updatePrayerUi();
        handler.postDelayed(tickSecond, TICK_MS);
        handler.postDelayed(tickDrift, DRIFT_MS);
    }

    void stop() {
        handler.removeCallbacks(tickSecond);
        handler.removeCallbacks(tickDrift);
    }

    private void updatePrayerUi() {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        PrayerTimeManager.PrayerInterval interval = prayerManager.getPrayerInterval(now);

        if (circleTimer != null) {
            circleTimer.setProgress(interval.progress);
        }

        if (tvNextName != null) {
            tvNextName.setText(interval.nextPrayerName.toUpperCase());
        }

        if (tvCountdown != null) {
            tvCountdown.setText(interval.countdownStr);
        }

        if (tvTargetTime != null) {
            tvTargetTime.setText("Pukul " + interval.nextPrayerTimeStr + " WIB");
        }

        if (tvInterval != null) {
            tvInterval.setText(interval.previousPrayerName + " → " + interval.nextPrayerName);
        }

        // Update times in schedule bar
        if (timeSubuh != null) timeSubuh.setText(interval.subuh);
        if (timeDzuhur != null) timeDzuhur.setText(interval.dzuhur);
        if (timeAshar != null) timeAshar.setText(interval.ashar);
        if (timeMaghrib != null) timeMaghrib.setText(interval.maghrib);
        if (timeIsya != null) timeIsya.setText(interval.isya);

        // Highlight next upcoming prayer
        String next = interval.nextPrayerName.toLowerCase();
        highlightPill(pillSubuh, lblSubuh, timeSubuh, next.contains("subuh"));
        highlightPill(pillDzuhur, lblDzuhur, timeDzuhur, next.contains("zuhur") || next.contains("dzuhur"));
        highlightPill(pillAshar, lblAshar, timeAshar, next.contains("ashar"));
        highlightPill(pillMaghrib, lblMaghrib, timeMaghrib, next.contains("maghrib"));
        highlightPill(pillIsya, lblIsya, timeIsya, next.contains("isya"));
    }

    private void highlightPill(LinearLayout pill, TextView label, TextView time, boolean active) {
        if (pill == null || label == null || time == null) return;
        if (active) {
            pill.setBackgroundResource(R.drawable.pill_prayer_active);
            label.setTextColor(Color.WHITE);
            time.setTextColor(Color.WHITE);
        } else {
            pill.setBackgroundResource(R.drawable.pill_prayer_inactive);
            label.setTextColor(Color.parseColor("#94A3B8"));
            time.setTextColor(Color.WHITE);
        }
    }

    /**
     * Shifts center container subtly (+/- 12dp) every minute to prevent OLED burn-in.
     */
    private void driftContainer() {
        if (container == null) return;
        float density = root.getResources().getDisplayMetrics().density;
        float maxOffset = 12f * density;
        float targetX = (random.nextFloat() * 2f - 1f) * maxOffset;
        float targetY = (random.nextFloat() * 2f - 1f) * maxOffset;

        container.animate()
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(1200L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}
