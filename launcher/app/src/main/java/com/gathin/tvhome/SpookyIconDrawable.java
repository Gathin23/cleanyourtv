package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A Halloween pill icon from a picture (Microsoft's Fluent Emoji 3D, MIT): a bat for Inputs that flaps its wings
 * twice when focused, or a ghost for Settings that drifts up and settles. The motion is a short scale or
 * offset of the same picture, played once per {@link #nudge()}; reduced motion leaves it still.
 */
public final class SpookyIconDrawable extends Drawable {

    static final int BAT = 0, GHOST = 1;

    private static final float BOX_DP = 24f;
    private static final long BAT_MS = 520, GHOST_MS = 900;

    private final int kind;
    private final float density;
    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();
    private final ValueAnimator animator;
    private float t = -1f;
    private boolean reduced;
    private int alpha = 255;

    public SpookyIconDrawable(Resources res, float density, int kind) {
        this.kind = kind;
        this.density = density;
        bitmap = BitmapFactory.decodeResource(res,
                kind == BAT ? R.drawable.halloween_bat : R.drawable.halloween_ghost);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(kind == BAT ? BAT_MS : GHOST_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                t = -1f;
                invalidateSelf();
            }
        });
    }

    /** The focus motion: two wing beats for the bat, a float up and back for the ghost. */
    public void nudge() {
        if (reduced) return;
        animator.cancel();
        animator.start();
    }

    /** With reduced motion {@link #nudge()} does nothing and a motion in progress stops at rest. */
    public void setReducedMotion(boolean reducedMotion) {
        reduced = reducedMotion;
        if (reduced && animator.isRunning()) animator.cancel();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float sx = 1f, sy = 1f, dy = 0f, rot = 0f;
        if (t >= 0f) {
            if (kind == BAT) {
                // two beats: the wings fold (the picture squeezes vertically) and open again
                float beat = (float) Math.sin(t * 2 * Math.PI * 2);
                sy = 1f - 0.28f * Math.abs(beat);
                sx = 1f + 0.06f * Math.abs(beat);
                dy = -1.5f * density * (float) Math.sin(t * Math.PI);
            } else {
                // drifts up about 3 dp, tilting slightly, then sinks back
                float up = (float) Math.sin(t * Math.PI);
                dy = -3f * density * up;
                rot = 6f * (float) Math.sin(t * 2 * Math.PI) * (1f - t);
            }
        }
        int save = canvas.save();
        canvas.translate(b.exactCenterX(), b.exactCenterY() + dy);
        canvas.rotate(rot);
        canvas.scale(sx, sy);
        int hw = b.width() / 2, hh = b.height() / 2;
        dst.set(-hw, -hh, b.width() - hw, b.height() - hh);
        paint.setAlpha(alpha);
        canvas.drawBitmap(bitmap, null, dst, paint);
        canvas.restoreToCount(save);
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
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
