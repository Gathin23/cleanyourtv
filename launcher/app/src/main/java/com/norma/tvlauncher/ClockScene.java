package com.norma.tvlauncher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

/**
 * Controller for the TV Screensaver.
 * Supports two user-selectable modes (configured via the launcher Settings menu):
 * 1. "solat" (Countdown Solat): OLED-black screen with green circular timer, live countdown,
 *    and daily prayer schedule for Kota Bekasi.
 * 2. "clock" (Clocks): Drifting clean typography clock & date over a shifting ambient glow,
 *    or photo slideshow if images exist in /sdcard/Screensaver/.
 * Both modes include subtle anti-burn-in drift every 60 seconds.
 */
final class ClockScene {

    private static final String TAG = "ClockScene";
    private static final long TICK_MS = 1000L;
    private static final long DRIFT_MS = 60_000L;
    private static final long SLIDE_MS = 10_000L;
    private static final long MOVE_MS = 60_000L;
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View root;
    private final Context context;
    private final PrayerTimeManager prayerManager;

    // Prayer views
    private final View dreamTopLeft;
    private final View dreamTopRight;
    private final View prayerContainer;
    private final PrayerCircleTimerView circleTimer;
    private final TextView tvNextName;
    private final TextView tvCountdown;

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

    // Clock views
    private final View content;
    private final View ambient;
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;

    // Photo slide views
    private FrameLayout slideA;
    private ImageView ambientA;
    private ImageView photoA;

    private FrameLayout slideB;
    private ImageView ambientB;
    private ImageView photoB;

    private boolean showingA = false;
    private final List<File> photoFiles = new ArrayList<>();
    private int currentPhotoIndex = 0;
    private Bitmap currentBitmapA;
    private Bitmap currentBitmapB;

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
            driftPrayerContainer();
            handler.postDelayed(this, DRIFT_MS);
        }
    };

    private final Runnable tickSlide = new Runnable() {
        @Override
        public void run() {
            nextSlide();
            handler.postDelayed(this, SLIDE_MS);
        }
    };

    private final Runnable tickClock = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    ClockScene(View root) {
        this.root = root;
        this.context = root.getContext();
        this.prayerManager = PrayerTimeManager.getInstance(context);

        // Prayer Views
        dreamTopLeft = root.findViewById(R.id.dreamTopLeft);
        dreamTopRight = root.findViewById(R.id.dreamTopRight);
        prayerContainer = root.findViewById(R.id.prayerScreensaverContainer);
        circleTimer = root.findViewById(R.id.prayerCircleTimer);
        tvNextName = root.findViewById(R.id.tvSolatNextName);
        tvCountdown = root.findViewById(R.id.tvSolatCountdown);

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

        // Slide Views
        slideA = root.findViewById(R.id.dreamSlideA);
        if (slideA != null) {
            ambientA = root.findViewById(R.id.dreamAmbientA);
            photoA = root.findViewById(R.id.dreamPhotoA);
        }

        slideB = root.findViewById(R.id.dreamSlideB);
        if (slideB != null) {
            ambientB = root.findViewById(R.id.dreamAmbientB);
            photoB = root.findViewById(R.id.dreamPhotoB);
        }

        // Clock Views
        content = root.findViewById(R.id.dreamContent);
        ambient = root.findViewById(R.id.dreamAmbient);
        if (ambient != null) {
            glow = new GradientDrawable();
            glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            ambient.setBackground(glow);
            ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                    glow.setGradientRadius((r - l) * 0.55f));
            colorIndex = random.nextInt(PALETTE.length);
            setGlow(PALETTE[colorIndex]);
        } else {
            glow = null;
        }
    }

    void start() {
        SharedPreferences sp = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String screensaverType = sp.getString("screensaver_type", "solat");
        boolean isSolat = "solat".equals(screensaverType);
        String tzId = sp.getString("weather_timezone", "Asia/Jakarta");
        syncTimeZones(root, tzId);

        stopCallbacks();

        if (isSolat && prayerContainer != null) {
            // Show Solat UI, hide Clock & Slideshow UI
            if (dreamTopLeft != null) dreamTopLeft.setVisibility(View.VISIBLE);
            if (dreamTopRight != null) dreamTopRight.setVisibility(View.VISIBLE);
            prayerContainer.setVisibility(View.VISIBLE);

            if (content != null) content.setVisibility(View.GONE);
            if (ambient != null) ambient.setVisibility(View.GONE);
            if (slideA != null) slideA.setVisibility(View.GONE);
            if (slideB != null) slideB.setVisibility(View.GONE);

            prayerManager.fetchPrayerTimesAsync(context, this::updatePrayerUi);
            updatePrayerUi();
            handler.postDelayed(tickSecond, TICK_MS);
            handler.postDelayed(tickDrift, DRIFT_MS);
        } else {
            // Hide Solat UI
            if (dreamTopLeft != null) dreamTopLeft.setVisibility(View.GONE);
            if (dreamTopRight != null) dreamTopRight.setVisibility(View.GONE);
            if (prayerContainer != null) prayerContainer.setVisibility(View.GONE);

            scanPhotos();
            if (!photoFiles.isEmpty()) {
                if (content != null) content.setVisibility(View.GONE);
                if (ambient != null) ambient.setVisibility(View.GONE);
                nextSlide();
                handler.postDelayed(tickSlide, SLIDE_MS);
            } else {
                if (slideA != null) slideA.setVisibility(View.GONE);
                if (slideB != null) slideB.setVisibility(View.GONE);
                if (ambient != null) ambient.setVisibility(View.VISIBLE);
                if (content != null) {
                    content.setVisibility(View.VISIBLE);
                    content.setAlpha(0f);
                    content.post(() -> {
                        placeClock();
                        content.animate().alpha(1f).setDuration(1200).start();
                    });
                }
                handler.postDelayed(tickClock, MOVE_MS);
            }
        }
    }

    void stop() {
        stopCallbacks();
        recycleBitmaps();
    }

    private void stopCallbacks() {
        handler.removeCallbacks(tickSecond);
        handler.removeCallbacks(tickDrift);
        handler.removeCallbacks(tickSlide);
        handler.removeCallbacks(tickClock);
    }

    private void syncTimeZones(View view, String tzId) {
        if (view == null || tzId == null) return;
        if (view instanceof android.widget.TextClock) {
            ((android.widget.TextClock) view).setTimeZone(tzId);
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                syncTimeZones(vg.getChildAt(i), tzId);
            }
        }
    }

    // ---- Prayer UI -------------------------------------------------------------

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
    private void driftPrayerContainer() {
        if (prayerContainer == null) return;
        float density = root.getResources().getDisplayMetrics().density;
        float maxOffset = 12f * density;
        float targetX = (random.nextFloat() * 2f - 1f) * maxOffset;
        float targetY = (random.nextFloat() * 2f - 1f) * maxOffset;

        prayerContainer.animate()
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(1200L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ---- Clock UI --------------------------------------------------------------

    private void moveClock() {
        if (content == null) return;
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    private void placeClock() {
        if (content == null || !(content.getParent() instanceof View)) return;
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0 && glow != null) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        if (glow == null) return;
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        if (glow == null) return;
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }

    // ---- Photos Slideshow ------------------------------------------------------

    private void scanPhotos() {
        photoFiles.clear();
        File[] candidateDirs = new File[]{
                new File(Environment.getExternalStorageDirectory(), "Screensaver"),
                new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screensaver"),
                new File("/sdcard/Screensaver")
        };

        for (File dir : candidateDirs) {
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName().toLowerCase();
                        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) {
                            if (!photoFiles.contains(f)) {
                                photoFiles.add(f);
                            }
                        }
                    }
                }
            }
        }

        if (!photoFiles.isEmpty()) {
            Collections.shuffle(photoFiles);
            currentPhotoIndex = 0;
            Log.d(TAG, "Found " + photoFiles.size() + " screensaver photos.");
        }
    }

    private void nextSlide() {
        if (photoFiles.isEmpty()) return;
        if (slideA == null || slideB == null) return;

        File file = photoFiles.get(currentPhotoIndex);
        currentPhotoIndex = (currentPhotoIndex + 1) % photoFiles.size();

        Bitmap bitmap = decodeSampledBitmap(file.getAbsolutePath(), 1920, 1080);
        if (bitmap == null) return;

        boolean isPortrait = bitmap.getHeight() > bitmap.getWidth();

        final FrameLayout incomingSlide = showingA ? slideB : slideA;
        final FrameLayout outgoingSlide = showingA ? slideA : slideB;
        final ImageView incomingAmbient = showingA ? ambientB : ambientA;
        final ImageView incomingPhoto = showingA ? photoB : photoA;

        // Set up incoming layer
        if (isPortrait) {
            incomingAmbient.setVisibility(View.VISIBLE);
            incomingAmbient.setImageBitmap(bitmap);
        } else {
            incomingAmbient.setVisibility(View.GONE);
        }

        incomingPhoto.setImageBitmap(bitmap);

        if (showingA) {
            if (currentBitmapB != null && currentBitmapB != bitmap) {
                currentBitmapB.recycle();
            }
            currentBitmapB = bitmap;
        } else {
            if (currentBitmapA != null && currentBitmapA != bitmap) {
                currentBitmapA.recycle();
            }
            currentBitmapA = bitmap;
        }

        incomingSlide.setAlpha(0f);
        incomingSlide.setVisibility(View.VISIBLE);

        incomingSlide.animate().cancel();
        outgoingSlide.animate().cancel();

        incomingSlide.animate()
                .alpha(1f)
                .setDuration(900)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        outgoingSlide.animate()
                .alpha(0f)
                .setDuration(900)
                .withEndAction(() -> outgoingSlide.setVisibility(View.GONE))
                .start();

        showingA = !showingA;
    }

    private Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            int height = options.outHeight;
            int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }

            options.inSampleSize = Math.max(1, inSampleSize);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, options);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to decode " + path, t);
            return null;
        }
    }

    private void recycleBitmaps() {
        if (currentBitmapA != null && !currentBitmapA.isRecycled()) {
            currentBitmapA.recycle();
            currentBitmapA = null;
        }
        if (currentBitmapB != null && !currentBitmapB.isRecycled()) {
            currentBitmapB.recycle();
            currentBitmapB = null;
        }
    }
}
