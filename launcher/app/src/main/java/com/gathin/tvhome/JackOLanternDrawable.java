package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * A carved jack-o'-lantern for the network pill while the Halloween skin is on: its face glows while the TV is
 * online and goes dark when the connection drops. The candle's brightness follows the signal level (1 to 4) and
 * flickers a little while started; Ethernet burns steady. It is two pictures of the same pumpkin, dark and lit
 * (from Microsoft's Fluent Emoji 3D, MIT), cross-faded, so it reads like the other skin's photo-real icons rather
 * than a drawing. Frames run at about 15 fps only while the candle burns or fades; nothing is allocated per frame.
 */
public final class JackOLanternDrawable extends Drawable implements FestiveLamp {

    private static final float BOX_DP = 24f;
    private static final long FRAME_MS = 66, FADE_MS = 350;
    /** How brightly the face glows at each level; index 0 is dark. */
    private static final float[] LEVEL_GLOW = {0f, 0.55f, 0.72f, 0.88f, 1f};

    private final float density;
    private final Bitmap dark, lit;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final float[] noise = new float[64];
    private final long epoch = SystemClock.uptimeMillis();

    private int level = 4;
    private boolean primed, running, steady, reduced;
    private float glowFrom = 1f, glowTo = 1f;
    private long fadeStart;
    private int alpha = 255;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
            if (wantsFrames()) scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS);
        }
    };

    public JackOLanternDrawable(Resources res, float density) {
        this.density = density;
        dark = BitmapFactory.decodeResource(res, R.drawable.halloween_pumpkin_dark);
        lit = BitmapFactory.decodeResource(res, R.drawable.halloween_pumpkin_lit);
        Random rnd = new Random(0xC731L);
        for (int i = 0; i < noise.length; i++) noise[i] = rnd.nextFloat();
    }

    @Override
    public void setFlame(int newLevel) {
        int lvl = Math.max(0, Math.min(4, newLevel));
        long now = SystemClock.uptimeMillis();
        if (!primed || reduced) {
            primed = true;
            level = lvl;
            glowFrom = glowTo = LEVEL_GLOW[lvl];
            kick();
            return;
        }
        if (lvl == level) return;
        glowFrom = glow(now);
        glowTo = LEVEL_GLOW[lvl];
        fadeStart = now;
        level = lvl;
        kick();
    }

    @Override
    public void setSteady(boolean steady) {
        if (this.steady == steady) return;
        this.steady = steady;
        invalidateSelf();
    }

    @Override
    public void setReducedMotion(boolean reducedMotion) {
        if (reduced == reducedMotion) return;
        reduced = reducedMotion;
        if (reduced) glowFrom = glowTo;
        kick();
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        kick();
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        glowFrom = glowTo;
        kick();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (changed) kick();
        return changed;
    }

    private boolean fading(long now) {
        return glowFrom != glowTo && now - fadeStart < FADE_MS;
    }

    private boolean wantsFrames() {
        return running && isVisible() && !reduced && (level > 0 || fading(SystemClock.uptimeMillis()));
    }

    private void kick() {
        unscheduleSelf(tick);
        if (wantsFrames()) scheduleSelf(tick, SystemClock.uptimeMillis() + FRAME_MS);
        invalidateSelf();
    }

    /** The face's brightness now, easing from the last level to the new one. */
    private float glow(long now) {
        float p = Math.min(1f, Math.max(0f, (now - fadeStart) / (float) FADE_MS));
        float e = 1f - (1f - p) * (1f - p);
        return glowFrom + (glowTo - glowFrom) * e;
    }

    /** A candle's flicker: smooth noise around 1, a slow breath when steady, nothing when still. */
    private float flicker(long now) {
        if (!running || reduced || level == 0) return 1f;
        float t = (now - epoch) / 1000f;
        if (steady) return 0.97f + 0.03f * (float) Math.sin(t * 1.6f);
        float x = t * 7f;
        int i = (int) x;
        float f = x - i;
        float s = f * f * (3f - 2f * f);
        float n = noise[i & 63] + (noise[(i + 1) & 63] - noise[i & 63]) * s;
        return 0.84f + 0.16f * n;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        paint.setAlpha(alpha);
        canvas.drawBitmap(dark, null, getBounds(), paint);
        float g = glow(now) * flicker(now);
        if (g <= 0.01f) return;
        paint.setAlpha(Math.round(alpha * Math.min(1f, g)));
        canvas.drawBitmap(lit, null, getBounds(), paint);
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(BOX_DP * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(BOX_DP * density);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) return;
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        paint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
