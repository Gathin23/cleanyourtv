package com.gathin.haunted;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Night photos crossfading slowly, a few bats now and then, a ghost that floats up now and then, a flickering
 * jack-o'-lantern glow in the bottom-right corner and a small clock. One Canvas, no libraries, about 20 frames
 * a second, and only two photos in memory at a time.
 */
public class HauntedView extends View {

    private static final long FRAME_MS = 50;
    private static final long PHOTO_MS = 14_000;
    private static final long FADE_MS = 3_500;
    private static final int ORANGE = 0xFFFF7A1A;

    private final Random rng = new Random();
    private final List<String> files = new ArrayList<>();
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final float dp;

    private Bitmap current, next;
    private int index = -1;
    private long photoSince, fadeStart;

    private Bat[] bats = new Bat[3];
    private long nextBat;
    private float ghostX, ghostY, ghostSize, ghostAge = -1;
    private long nextGhost;
    private long clockMinute = -1;
    private String clockText = "";
    private java.text.DateFormat clockFormat;
    private RadialGradient glowShader;
    private float glowR;

    private boolean running;
    private long lastFrame, t0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    private static final class Bat { float x, y, v, size, bob, phase; boolean on; }

    public HauntedView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setBackgroundColor(0xFF07050B);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        text.setShadowLayer(4 * dp, 0, 0, 0xFF000000);
        clockFormat = DateFormat.getTimeFormat(c);
        try {
            String[] names = c.getAssets().list("photos");
            if (names != null) for (String n : names) if (n.endsWith(".jpg")) files.add(n);
            java.util.Collections.sort(files);
        } catch (Exception ignored) {
            // No photos: the screen stays night-black with the bats and the ghost.
        }
        for (int i = 0; i < bats.length; i++) bats[i] = new Bat();
    }

    public void start() {
        if (running) return;
        running = true;
        t0 = System.currentTimeMillis();
        nextBat = t0 + 4_000;
        nextGhost = t0 + 12_000;
        if (current == null) { index = rng.nextInt(Math.max(1, files.size())) - 1; current = load(1); photoSince = t0; }
        post(tick);
    }

    public void stop() {
        running = false;
        removeCallbacks(tick);
        current = null;
        next = null;
    }

    /** Photo number index + step, decoded as is (1280x720). */
    private Bitmap load(int step) {
        if (files.isEmpty()) return null;
        index = (index + step) % files.size();
        try (InputStream in = getContext().getAssets().open("photos/" + files.get(index))) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        glowR = Math.min(w, h) * 0.55f;
        glowShader = new RadialGradient(0, 0, glowR, new int[]{0xAAFF8A20, 0x44FF5A10, 0x00FF5A10},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        ghostAge = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.05f : Math.min(0.2f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int w = getWidth(), h = getHeight();

        drawPhotos(canvas, now, w, h);
        drawGlow(canvas, now, w, h);
        drawGhost(canvas, now, dt, w, h);
        drawBats(canvas, now, dt, w, h);
        drawClock(canvas, now, w, h);
    }

    private void drawPhotos(Canvas c, long now, int w, int h) {
        if (next == null && current != null && files.size() > 1 && now - photoSince > PHOTO_MS) {
            next = load(1);
            fadeStart = now;
        }
        if (next != null) {
            float f = Math.min(1f, (now - fadeStart) / (float) FADE_MS);
            photoPaint.setAlpha(255);
            blit(c, current, w, h);
            photoPaint.setAlpha((int) (255 * f));
            blit(c, next, w, h);
            if (f >= 1f) { current = next; next = null; photoSince = now; }
        } else {
            photoPaint.setAlpha(255);
            blit(c, current, w, h);
        }
    }

    private void blit(Canvas c, Bitmap b, int w, int h) {
        if (b == null) return;
        src.set(0, 0, b.getWidth(), b.getHeight());
        dst.set(0, 0, w, h);
        c.drawBitmap(b, src, dst, photoPaint);
    }

    /** A jack-o'-lantern flicker: a few sines at odd rates plus a little noise, in the bottom-right corner. */
    private void drawGlow(Canvas c, long now, int w, int h) {
        double t = (now - t0) / 1000.0;
        float k = (float) (0.72 + 0.12 * Math.sin(t * 7.3) + 0.08 * Math.sin(t * 13.1 + 1) + 0.04 * Math.sin(t * 2.1))
                + (rng.nextFloat() - 0.5f) * 0.06f;
        fill.setShader(glowShader);
        fill.setAlpha((int) (255 * Math.max(0.3f, Math.min(1f, k))));
        c.save();
        c.translate(w, h);
        c.drawCircle(0, 0, glowR, fill);
        c.restore();
        fill.setShader(null);
        fill.setAlpha(255);
    }

    private void drawGhost(Canvas c, long now, float dt, int w, int h) {
        if (ghostAge < 0) {
            if (now < nextGhost) return;
            ghostSize = h * 0.16f;
            ghostX = w * (0.15f + 0.5f * rng.nextFloat());
            ghostY = h * 0.85f;
            ghostAge = 0;
        }
        ghostAge += dt;
        float life = 9f;
        if (ghostAge > life) {
            ghostAge = -1;
            nextGhost = now + 25_000 + rng.nextInt(25_000);
            return;
        }
        float p = ghostAge / life;
        float a = (float) Math.sin(Math.PI * p);
        float x = ghostX + (float) Math.sin(ghostAge * 1.3) * ghostSize * 0.5f;
        float y = ghostY - p * h * 0.55f;
        float s = ghostSize;
        path.reset();
        path.moveTo(x - s * 0.5f, y + s * 0.5f);
        path.lineTo(x - s * 0.5f, y - s * 0.1f);
        path.cubicTo(x - s * 0.5f, y - s * 0.75f, x + s * 0.5f, y - s * 0.75f, x + s * 0.5f, y - s * 0.1f);
        path.lineTo(x + s * 0.5f, y + s * 0.5f);
        for (int i = 0; i < 4; i++) {
            float x1 = x + s * 0.5f - s * (i + 0.5f) / 4f, x2 = x + s * 0.5f - s * (i + 1f) / 4f;
            path.quadTo(x1, y + s * (i % 2 == 0 ? 0.3f : 0.7f), x2, y + s * 0.5f);
        }
        path.close();
        fill.setColor(Color.argb((int) (150 * a), 237, 231, 246));
        c.drawPath(path, fill);
        fill.setColor(Color.argb((int) (220 * a), 7, 5, 11));
        c.drawOval(x - s * 0.28f, y - s * 0.22f, x - s * 0.1f, y + s * 0.06f, fill);
        c.drawOval(x + s * 0.1f, y - s * 0.22f, x + s * 0.28f, y + s * 0.06f, fill);
        c.drawOval(x - s * 0.08f, y + s * 0.14f, x + s * 0.08f, y + s * 0.3f, fill);
    }

    private void drawBats(Canvas c, long now, float dt, int w, int h) {
        if (now >= nextBat) {
            for (Bat b : bats) {
                if (b.on) continue;
                boolean ltr = rng.nextBoolean();
                b.size = (22 + rng.nextInt(26)) * dp;
                b.v = (ltr ? 1 : -1) * w * (0.08f + 0.06f * rng.nextFloat());
                b.x = ltr ? -b.size : w + b.size;
                b.y = h * (0.1f + 0.45f * rng.nextFloat());
                b.bob = h * 0.03f * (0.5f + rng.nextFloat());
                b.phase = rng.nextFloat() * 6.28f;
                b.on = true;
                break;
            }
            nextBat = now + 5_000 + rng.nextInt(12_000);
        }
        fill.setColor(0xF0100A16);
        for (Bat b : bats) {
            if (!b.on) continue;
            b.x += b.v * dt;
            if (b.x < -2 * b.size || b.x > w + 2 * b.size) { b.on = false; continue; }
            float t = now / 1000f;
            float y = b.y + (float) Math.sin(t * 1.7 + b.phase) * b.bob;
            float flap = (float) Math.sin(t * 11 + b.phase);
            float s = b.size;
            float tip = -s * 0.3f * flap;
            path.reset();
            path.moveTo(b.x, y - s * 0.12f);
            path.quadTo(b.x - s * 0.5f, y - s * 0.55f + tip, b.x - s, y - s * 0.1f + tip * 1.4f);
            path.quadTo(b.x - s * 0.7f, y + s * 0.05f, b.x - s * 0.5f, y + s * 0.02f);
            path.quadTo(b.x - s * 0.3f, y + s * 0.2f, b.x, y + s * 0.2f);
            path.quadTo(b.x + s * 0.3f, y + s * 0.2f, b.x + s * 0.5f, y + s * 0.02f);
            path.quadTo(b.x + s * 0.7f, y + s * 0.05f, b.x + s, y - s * 0.1f + tip * 1.4f);
            path.quadTo(b.x + s * 0.5f, y - s * 0.55f + tip, b.x, y - s * 0.12f);
            path.close();
            c.drawPath(path, fill);
            c.drawCircle(b.x, y, s * 0.12f, fill);
        }
    }

    private void drawClock(Canvas c, long now, int w, int h) {
        long minute = now / 60_000;
        if (minute != clockMinute) {
            clockMinute = minute;
            clockText = clockFormat.format(new Date(now));
        }
        text.setColor(0xCCFFFFFF);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(30 * dp);
        c.drawText(clockText, w - 36 * dp, 56 * dp, text);
    }
}
