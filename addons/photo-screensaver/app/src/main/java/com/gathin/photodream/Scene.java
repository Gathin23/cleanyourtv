package com.gathin.photodream;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the screensaver and the card's slideshow show: the pictures (full screen, or as prints), and on top what
 * the options switch on: the time and date, the weather, the sky behind it all. Both hosts call start and stop.
 */
final class Scene {

    private static final long MINUTE_MS = 60_000L;
    /** How far the bottom line may wander so it does not burn in. */
    private static final int NUDGE_DP = 20, NUDGE_UP_DP = 6;
    /** Option "the time with each picture": the bottom line shows this long as each picture comes in, then fades away. */
    private static final long LINE_SHOWN_MS = 10_000L, LINE_IN_MS = 800L, LINE_OUT_MS = 1_500L;

    final FrameLayout root;
    private final Context context;
    private final Options options;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final SkyView sky;
    private final View bottomLine;
    private final View bottomFade;
    private final TextView weather;
    private final Slides slides;
    private final Prints prints;
    private ExecutorService network;
    private boolean running;
    /** The bottom line's running fade; the next one cancels it so two never fight over the line's alpha. */
    private AnimatorSet lineFade;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (sky != null) sky.update();
            nudge();
            handler.postDelayed(this, MINUTE_MS);
        }
    };
    private final Runnable refreshWeather = this::refreshWeather;
    private final Runnable hideBottomLine = () -> fadeBottomLine(0f, LINE_OUT_MS);

    Scene(Context context) {
        this.context = context;
        options = Options.load(context);
        root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);
        if (options.sky && options.prints) {
            sky = new SkyView(context);
            root.addView(sky, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            sky = null;
        }
        FrameLayout stage = new FrameLayout(context);
        root.addView(stage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View overlay = LayoutInflater.from(context).inflate(R.layout.overlay, root, false);
        root.addView(overlay);
        bottomLine = overlay.findViewById(R.id.bottomLine);
        weather = overlay.findViewById(R.id.weather);
        TextView caption = overlay.findViewById(R.id.caption);
        View empty = overlay.findViewById(R.id.empty);
        File dir = context.getExternalFilesDir(null);
        ((TextView) overlay.findViewById(R.id.emptyPath)).setText(
                dir == null ? "" : new File(dir, Media.PICTURES).getPath());

        int clockVisibility = options.clock ? View.VISIBLE : View.GONE;
        overlay.findViewById(R.id.time).setVisibility(clockVisibility);
        overlay.findViewById(R.id.ampm).setVisibility(clockVisibility);
        overlay.findViewById(R.id.date).setVisibility(clockVisibility);
        weather.setVisibility(View.GONE);
        bottomLine.setVisibility(options.clock || options.weather ? View.VISIBLE : View.GONE);
        // In full-screen mode the pictures cover everything: a dark fade keeps the bottom line readable.
        bottomFade = overlay.findViewById(R.id.bottomFade);
        bottomFade.setVisibility(!options.prints && (options.clock || options.weather) ? View.VISIBLE : View.GONE);

        if (options.prints) {
            caption.setVisibility(View.GONE);
            prints = new Prints(context, stage, options.clock || options.weather ? bottomLine : null, empty,
                    options.dates);
            slides = null;
        } else {
            slides = new Slides(context, stage, options.dates ? caption : null, empty);
            prints = null;
            if (!options.dates) caption.setVisibility(View.GONE);
            if (options.clockFades && (options.clock || options.weather)) {
                // The line comes with each picture and leaves again, so nothing stays on screen.
                bottomLine.setAlpha(0f);
                bottomFade.setAlpha(0f);
                slides.onShown(this::showBottomLine);
            }
        }
        bottomLine.bringToFront();
    }

    void start() {
        if (running) return;
        running = true;
        if (slides != null) slides.start();
        if (prints != null) prints.start();
        handler.post(tick);
        if (options.weather) {
            network = Executors.newSingleThreadExecutor();
            handler.post(refreshWeather);
        }
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (lineFade != null) lineFade.cancel();
        if (network != null) network.shutdownNow();
        network = null;
        if (slides != null) slides.stop();
        if (prints != null) prints.stop();
    }

    private void showBottomLine() {
        handler.removeCallbacks(hideBottomLine);
        fadeBottomLine(1f, LINE_IN_MS);
        handler.postDelayed(hideBottomLine, LINE_SHOWN_MS);
    }

    /**
     * ObjectAnimators, not view.animate(), so the fade never cancels nudge()'s drift on the same view. A picture can
     * come in while the line is still fading out (5 s pictures, a short clip): the old fade is cancelled first, or it
     * would outlast the new one and leave the line hidden.
     */
    private void fadeBottomLine(float alpha, long ms) {
        if (lineFade != null) lineFade.cancel();
        lineFade = new AnimatorSet();
        lineFade.playTogether(ObjectAnimator.ofFloat(bottomLine, View.ALPHA, alpha),
                ObjectAnimator.ofFloat(bottomFade, View.ALPHA, alpha));
        lineFade.setDuration(ms).start();
    }

    private void nudge() {
        if (bottomLine.getVisibility() != View.VISIBLE) return;
        float d = context.getResources().getDisplayMetrics().density;
        bottomLine.animate().translationX((random.nextFloat() * 2 - 1) * NUDGE_DP * d)
                .translationY(random.nextFloat() * -NUDGE_UP_DP * d).setDuration(1500).start();
    }

    /** The cached reading if it is fresh, else a new one; again every 30 minutes while the scene runs. */
    private void refreshWeather() {
        if (!running) return;
        handler.postDelayed(refreshWeather, Weather.REFRESH_MS);
        SharedPreferences cache = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        String cached = cache.getString("text", "");
        long age = System.currentTimeMillis() - cache.getLong("at", 0);
        if (!cached.isEmpty() && age >= 0 && age < Weather.REFRESH_MS) {
            showWeather(cached);
            return;
        }
        Weather.locate(context, location -> {
            ExecutorService n = network;
            if (location == null || n == null || !running) return;
            double lat = location.getLatitude(), lon = location.getLongitude();
            try {
                n.execute(() -> {
                    String text;
                    try {
                        text = Weather.now(lat, lon);
                    } catch (Exception e) {
                        return;   // offline: keep what shows, try again in half an hour
                    }
                    cache.edit().putString("text", text).putLong("at", System.currentTimeMillis()).apply();
                    handler.post(() -> { if (running) showWeather(text); });
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // Stopped meanwhile.
            }
        });
    }

    private void showWeather(String text) {
        weather.setText(text);
        weather.setVisibility(View.VISIBLE);
    }
}
