package com.gathin.newsdream;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the screensaver and the card show: one headline at a time, its source and age above it and a line of
 * summary below, crossfading to the next after the set seconds. A QR code to the article in the bottom corner,
 * and "3 / 42" beside the progress line. A clock in the corner. Every part keeps its place from one headline to
 * the next; only the whole picture drifts a few dp, very slowly, against burn-in. Feeds are fetched on start and every 15 minutes while it shows.
 */
final class Scene {
    private static final int ACCENT = 0xFFFF8A5B;
    private static final long FADE_MS = 450;
    private static final long RETRY_MS = 2 * 60_000L;
    // The fixed layout, in dp.
    private static final int TOP = 140;
    private static final int SOURCE = 28;
    private static final int GAP = 12;
    private static final int TITLE = 156;
    private static final int SUMMARY = 96;   // three lines of 20sp
    private static final int QR = 176;
    // Against burn-in: the whole picture moves together, a few dp round a small circle, one step every two minutes,
    // sliding there over half a minute. Nothing moves when a headline changes.
    private static final int SHIFT_DP = 6;
    private static final int SHIFT_STEPS = 8;
    private static final long SHIFT_EVERY_MS = 2 * 60_000L;
    private static final long SHIFT_SLIDE_MS = 30_000L;

    final FrameLayout root;
    private final Context context;
    private final File dir;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout block;
    private final TextView source;
    private final TextView title;
    private final TextView summary;
    private final View progress;
    private final TextView counter;
    private final QrView qr;
    private final TextClock clock;

    private List<Headline> headlines = new ArrayList<>();
    private List<String> feeds;
    private long secondsMs;
    private int index = -1;
    private boolean running;
    private ExecutorService executor;

    private final FrameLayout stage;
    private int shiftStep;

    private final Runnable next = this::next;
    private final Runnable shift = this::shift;
    private final Runnable refresh = this::refresh;

    Scene(Context context) {
        this.context = context;
        this.dir = context.getFilesDir();

        root = new FrameLayout(context);
        GradientDrawable sky = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF14203A, 0xFF070B14});
        root.setBackground(sky);
        root.setKeepScreenOn(true);
        stage = new FrameLayout(context);
        root.addView(stage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        clock = new TextClock(context);
        clock.setTextColor(0xCCFFFFFF);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        clp.setMargins(dp(64), dp(48), dp(64), 0);
        stage.addView(clock, clp);

        // A fixed layout: every part has its own place and height, so nothing moves when the headline changes.
        // On a 1080p TV (540dp high): source at 140dp, the headline box under it, summary, then the count.
        block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        // The end margin leaves room for the QR code.
        blp.setMargins(dp(112), dp(TOP), dp(112 + QR + 48), 0);
        stage.addView(block, blp);

        source = text(18, ACCENT, 1);
        source.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        source.setLetterSpacing(0.08f);
        source.setAllCaps(true);
        block.addView(source, fixed(dp(SOURCE), 0));

        // Three lines at most; a long headline gets smaller type rather than more room.
        title = text(44, Color.WHITE, 3);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLineSpacing(0, 1.08f);
        title.setAutoSizeTextTypeUniformWithConfiguration(26, 44, 2, TypedValue.COMPLEX_UNIT_SP);
        block.addView(title, fixed(dp(TITLE), dp(GAP)));

        summary = text(20, 0xB3FFFFFF, 3);
        summary.setLineSpacing(0, 1.15f);
        block.addView(summary, fixed(dp(SUMMARY), dp(16)));

        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        block.addView(bottom, fixed(dp(24), dp(24)));
        // "3 / 42" first, in a box of its own width, so the line after it starts in the same place every time.
        counter = text(16, 0x99FFFFFF, 1);
        counter.setShadowLayer(0, 0, 0, 0);
        bottom.addView(counter, new LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT));
        progress = new View(context);
        progress.setBackgroundColor(ACCENT);
        progress.setAlpha(0.7f);
        progress.setPivotX(0);
        bottom.addView(progress, new LinearLayout.LayoutParams(dp(220), dp(3)));

        // Level with the top of the headline.
        qr = new QrView(context);
        qr.setVisibility(View.INVISIBLE);
        qr.setAlpha(0.92f);
        FrameLayout.LayoutParams qlp = new FrameLayout.LayoutParams(dp(QR), dp(QR), Gravity.TOP | Gravity.END);
        qlp.setMargins(0, dp(TOP + SOURCE + GAP), dp(112), 0);
        stage.addView(qr, qlp);
    }

    void start() {
        if (running) return;
        running = true;
        handler.postDelayed(shift, SHIFT_EVERY_MS);
        feeds = Config.feeds(dir);
        secondsMs = Config.seconds(dir) * 1000L;
        executor = Executors.newSingleThreadExecutor();
        if (feeds.isEmpty()) {
            status(R.string.no_feeds_title, R.string.no_feeds_body);
            return;
        }
        headlines = new ArrayList<>(News.load(dir));
        index = -1;
        if (headlines.isEmpty()) status(R.string.loading_title, 0);
        else next();
        long age = System.currentTimeMillis() - News.cacheTime(dir);
        if (headlines.isEmpty() || age >= News.REFRESH_MS || age < 0) refresh();
        else handler.postDelayed(refresh, News.REFRESH_MS - age);
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        progress.animate().cancel();
        block.animate().cancel();
        stage.animate().cancel();
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    private void shift() {
        if (!running) return;
        shiftStep = (shiftStep + 1) % SHIFT_STEPS;
        double angle = 2 * Math.PI * shiftStep / SHIFT_STEPS;
        stage.animate()
                .translationX((float) (dp(SHIFT_DP) * Math.cos(angle)))
                .translationY((float) (dp(SHIFT_DP) * Math.sin(angle)))
                .setDuration(SHIFT_SLIDE_MS).setInterpolator(new LinearInterpolator()).start();
        handler.postDelayed(shift, SHIFT_EVERY_MS);
    }

    /** One headline on (+1, Right on the remote) or back (-1, Left), now; the timer starts again. */
    void step(int delta) {
        if (!running || headlines.isEmpty()) return;
        int n = headlines.size();
        index = ((index + delta - 1) % n + n) % n;   // advance() adds the one back
        advance(false);
    }

    private void refresh() {
        if (!running || executor == null) return;
        List<String> urls = feeds;
        executor.execute(() -> {
            List<Headline> fresh = News.fetch(urls, System.currentTimeMillis());
            if (!fresh.isEmpty()) News.save(dir, fresh);
            handler.post(() -> loaded(fresh));
        });
    }

    private void loaded(List<Headline> fresh) {
        if (!running) return;
        handler.removeCallbacks(refresh);
        if (fresh.isEmpty()) {
            // Keep showing the old list; try again sooner.
            if (headlines.isEmpty()) status(R.string.offline_title, R.string.offline_body);
            handler.postDelayed(refresh, RETRY_MS);
            return;
        }
        boolean first = headlines.isEmpty();
        headlines = fresh;
        if (first) {
            index = -1;
            next();
        } else if (index >= headlines.size()) {
            index = -1;
        }
        handler.postDelayed(refresh, News.REFRESH_MS);
    }

    private void next() {
        advance(true);
    }

    /** The next headline, crossfading on the timer and at once when the remote asks. */
    private void advance(boolean fade) {
        if (!running || headlines.isEmpty()) return;
        index = (index + 1) % headlines.size();
        Headline h = headlines.get(index);
        if (!fade || title.getText().length() == 0) {
            block.animate().cancel();
            block.setAlpha(1f);
            show(h);
        } else {
            block.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
                show(h);
                block.animate().alpha(1f).setDuration(FADE_MS).start();
            }).start();
        }
        handler.removeCallbacks(next);
        handler.postDelayed(next, secondsMs);
    }

    private void show(Headline h) {
        String age = h.time > 0 ? DateUtils.getRelativeTimeSpanString(Math.min(h.time, System.currentTimeMillis()),
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString() : "";
        source.setText(age.isEmpty() ? h.source : h.source + "  ·  " + age);
        title.setText(h.title);
        summary.setText(h.summary);
        counter.setText(String.format(Locale.getDefault(), "%d / %d", index + 1, headlines.size()));
        qr.setVisibility(qr.setText(h.link) ? View.VISIBLE : View.INVISIBLE);
        progress.setVisibility(View.VISIBLE);
        progress.animate().cancel();
        progress.setScaleX(0f);
        progress.animate().scaleX(1f).setDuration(secondsMs).setInterpolator(new LinearInterpolator()).start();
    }

    private void status(int titleText, int body) {
        source.setText(R.string.dream_name);
        title.setText(titleText);
        summary.setText(body == 0 ? "" : context.getString(body));
        progress.animate().cancel();
        progress.setVisibility(View.INVISIBLE);
        counter.setText("");
        qr.setVisibility(View.INVISIBLE);
        block.setAlpha(1f);
    }

    private TextView text(int sp, int color, int maxLines) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setMaxLines(maxLines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        t.setShadowLayer(dp(6), 0, dp(1), 0x66000000);
        return t;
    }

    private static LinearLayout.LayoutParams fixed(int height, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.topMargin = top;
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
