package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The Halloween skin's greeting for the first Home of the day: a transparent full-screen overlay where a colony of
 * bats rises from the bottom of the screen and scatters out over the top, beating their wings, then the callback
 * runs on the main thread. The bat is a picture (Microsoft's Fluent Emoji 3D, MIT), squeezed in time with each
 * wing beat and mirrored for half the colony. Like {@link PetalShowerView} it takes no focus, clicks or
 * accessibility and allocates nothing per frame.
 */
public final class BatFlightView extends View {

    private static final double TAU = 2 * Math.PI;

    private final float density;
    private final Bitmap bat;
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();

    private int count;
    private float[] x0 = new float[0], drift = new float[0], delay = new float[0], life = new float[0],
            size = new float[0], beatHz = new float[0], phase = new float[0], weave = new float[0];
    private boolean[] mirror = new boolean[0];

    private boolean playing;
    private long start, duration;
    private Runnable onDone;
    private final Runnable finish = this::finish;

    public BatFlightView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        bat = BitmapFactory.decodeResource(context.getResources(), R.drawable.halloween_bat);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    /**
     * Sends {@code bats} bats up over {@code durationMs}, then runs {@code done} on the main thread. Playing again
     * before the last flight ends finishes that one first (its callback runs).
     */
    public void play(int bats, long durationMs, Runnable done) {
        if (playing) {
            removeCallbacks(finish);
            finish();
        }
        int n = Math.max(0, bats);
        ensureCapacity(n);
        count = n;
        duration = Math.max(1, durationMs);
        onDone = done;
        for (int i = 0; i < n; i++) {
            x0[i] = (i + rnd.nextFloat()) / n;               // spread across the width
            drift[i] = (rnd.nextFloat() - 0.5f) * 0.5f;      // fraction of the width moved sideways
            delay[i] = rnd.nextFloat() * 0.35f;              // fractions of the duration
            life[i] = 0.55f + rnd.nextFloat() * (1f - delay[i] - 0.55f);
            size[i] = 26f + rnd.nextFloat() * 30f;           // dp
            beatHz[i] = 5f + rnd.nextFloat() * 3f;
            phase[i] = rnd.nextFloat() * (float) TAU;
            weave[i] = 8f + rnd.nextFloat() * 18f;           // dp of side-to-side weave
            mirror[i] = drift[i] < 0f;                       // face the way it is heading
        }
        playing = true;
        start = SystemClock.uptimeMillis();
        postDelayed(finish, duration);
        postInvalidateOnAnimation();
    }

    private void finish() {
        if (!playing) return;
        playing = false;
        invalidate();
        Runnable done = onDone;
        onDone = null;
        if (done != null) done.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (playing) {
            removeCallbacks(finish);
            finish();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!playing) return;
        long now = SystemClock.uptimeMillis();
        float elapsed = (now - start) / (float) duration;
        float secs = (now - start) / 1000f;
        float w = getWidth(), h = getHeight();
        for (int i = 0; i < count; i++) {
            float p = (elapsed - delay[i]) / life[i];
            if (p <= 0f || p >= 1f) continue;
            float s = size[i] * density;
            float rise = p * (0.6f + 0.4f * p);              // takes off, then speeds up
            float y = h + s - (h + 2.5f * s) * rise;
            float x = (x0[i] + drift[i] * p) * w
                    + weave[i] * density * (float) Math.sin(TAU * 0.8 * secs + phase[i]);
            float beat = (float) Math.sin(TAU * beatHz[i] * secs + phase[i]);
            float sy = 0.62f + 0.38f * Math.abs(beat);       // wings folding and opening
            int a = p < 0.12f ? (int) (255 * p / 0.12f) : p > 0.8f ? (int) (255 * (1f - (p - 0.8f) / 0.2f)) : 255;
            paint.setAlpha(a);
            c.save();
            c.translate(x, y);
            c.scale(mirror[i] ? -1f : 1f, sy);
            dst.set(-s / 2f, -s / 2f, s / 2f, s / 2f);
            c.drawBitmap(bat, null, dst, paint);
            c.restore();
        }
        postInvalidateOnAnimation();
    }

    private void ensureCapacity(int n) {
        if (x0.length >= n) return;
        x0 = new float[n];
        drift = new float[n];
        delay = new float[n];
        life = new float[n];
        size = new float[n];
        beatHz = new float[n];
        phase = new float[n];
        weave = new float[n];
        mirror = new boolean[n];
    }
}
