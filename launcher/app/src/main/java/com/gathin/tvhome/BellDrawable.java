package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A brass temple bell (ghanta): a loop and knob on top, a domed body with two engraved bands and a flared lip,
 * and the clapper hanging below. {@link #ring()} swings it about the loop, a damped swing of about 16 degrees
 * that dies away within a second, with the clapper trailing the body. Each ring starts the other way from the
 * last one, and ringing again mid-swing carries on from where the bell is. The highlight stays put as the bell
 * swings, as if lit from the top left. Drawn in a 24 x 24 unit box scaled into the bounds; the swing is sized so
 * the bell stays inside it.
 */
public final class BellDrawable extends Drawable {

    private static final float BOX = 24f;
    /** The pivot: the top of the loop. */
    private static final float PX = 12f, PY = 1.3f;
    private static final float AMPLITUDE = 16f;
    private static final float OMEGA = (float) (2 * Math.PI * 3.4), TAU = 0.3f;
    private static final long RING_MS = 1000;
    /** The clapper follows the body this many seconds late, and never more than CLAPPER_MAX degrees apart. */
    private static final float LAG = 0.055f, CLAPPER_MAX = 7f;

    private final float density;
    private final Path body = new Path(), lip = new Path(), lipTop = new Path();
    private final Path band1 = new Path(), band2 = new Path(), sheen = new Path();
    private final RectF crown = new RectF(10.3f, 3.1f, 13.7f, 5.5f);

    private final Paint bodyPaint, lipPaint, crownPaint, outline, groove, bandDark, bandLight, sheenPaint,
            loopDark, loopBrass, rodPaint, ballPaint, ballEdge;
    private final Paint[] paints;
    private final ValueAnimator animator;

    private float angle, clapperAngle;
    private float startAngle, amplitude;
    private int direction = -1;
    private boolean reduced;
    private int alpha = 255;

    public BellDrawable(float density) {
        this.density = density;
        buildPaths();

        int[] brass = {0xFF6B430E, 0xFFB98424, 0xFFF0CF72, 0xFFD6A13A, 0xFF9C6A1A, 0xFF5C380A};
        float[] stops = {0f, 0.16f, 0.33f, 0.52f, 0.8f, 1f};
        bodyPaint = fill();
        bodyPaint.setShader(new LinearGradient(5f, 0f, 19f, 0f, brass, stops, Shader.TileMode.CLAMP));
        lipPaint = fill();
        lipPaint.setShader(new LinearGradient(4.8f, 0f, 19.2f, 0f,
                new int[]{0xFF7A4E12, 0xFFD9A43A, 0xFFFBE08A, 0xFFE3B04A, 0xFFA87420, 0xFF60400E},
                stops, Shader.TileMode.CLAMP));
        crownPaint = fill();
        crownPaint.setShader(new LinearGradient(crown.left, 0f, crown.right, 0f, brass, stops,
                Shader.TileMode.CLAMP));
        outline = stroke(0xFF452A05, 0.55f);
        groove = stroke(0xFF5A3608, 0.35f);
        bandDark = stroke(0x995A3608, 0.38f);
        bandLight = stroke(0x66FFF2C0, 0.25f);
        sheenPaint = stroke(0xB3FFF4D2, 0.9f);
        loopDark = stroke(0xFF452A05, 1.15f);
        loopBrass = stroke(0xFFD6A13A, 0.55f);
        rodPaint = stroke(0xFF4E3208, 0.75f);
        ballPaint = fill();
        ballPaint.setShader(new RadialGradient(11.6f, 20.7f, 1.7f,
                new int[]{0xFFF2D27A, 0xFFB07E22, 0xFF5A3A0A}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        ballEdge = stroke(0xFF3E2604, 0.4f);
        paints = new Paint[]{bodyPaint, lipPaint, crownPaint, outline, groove, bandDark, bandLight, sheenPaint,
                loopDark, loopBrass, rodPaint, ballPaint, ballEdge};

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(RING_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue() * RING_MS / 1000f;
            angle = swing(t);
            float lagged = swing(Math.max(0f, t - LAG));
            clapperAngle = Math.max(angle - CLAPPER_MAX, Math.min(angle + CLAPPER_MAX, lagged));
            invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                angle = clapperAngle = 0f;
                invalidateSelf();
            }
        });
    }

    /** Rings the bell: a damped swing about the loop, with the clapper trailing. */
    public void ring() {
        if (reduced) return;
        startAngle = angle;
        direction = -direction;
        amplitude = AMPLITUDE * direction;
        animator.cancel();
        animator.start();
    }

    /** With reduced motion {@link #ring()} does nothing and a swing in progress stops at rest. */
    public void setReducedMotion(boolean reducedMotion) {
        reduced = reducedMotion;
        if (reduced && animator.isRunning()) animator.cancel();
    }

    /** Angle in degrees t seconds into a ring, carrying on from where the bell was when it was rung. */
    private float swing(float t) {
        float wt = OMEGA * t;
        float a = (float) Math.exp(-t / TAU) * (startAngle * (float) Math.cos(wt) + amplitude * (float) Math.sin(wt));
        if (t > 0.85f) {
            float f = Math.min(1f, (t - 0.85f) / 0.15f);
            a *= 1f - f * f * (3f - 2f * f);
        }
        return Math.max(-AMPLITUDE, Math.min(AMPLITUDE, a));
    }

    private void buildPaths() {
        body.moveTo(9.8f, 5.0f);
        body.cubicTo(7.6f, 5.3f, 7.0f, 7.8f, 7.0f, 10.6f);
        body.cubicTo(7.0f, 13.6f, 6.6f, 15.6f, 5.3f, 16.9f);
        body.quadTo(5.0f, 17.5f, 5.5f, 18.0f);
        body.cubicTo(8.6f, 18.9f, 15.4f, 18.9f, 18.5f, 18.0f);
        body.quadTo(19.0f, 17.5f, 18.7f, 16.9f);
        body.cubicTo(17.4f, 15.6f, 17.0f, 13.6f, 17.0f, 10.6f);
        body.cubicTo(17.0f, 7.8f, 16.4f, 5.3f, 14.2f, 5.0f);
        body.quadTo(12.0f, 4.6f, 9.8f, 5.0f);
        body.close();

        lip.moveTo(5.3f, 16.9f);
        lip.quadTo(5.0f, 17.5f, 5.5f, 18.0f);
        lip.cubicTo(8.6f, 18.9f, 15.4f, 18.9f, 18.5f, 18.0f);
        lip.quadTo(19.0f, 17.5f, 18.7f, 16.9f);
        lip.cubicTo(15.4f, 17.7f, 8.6f, 17.7f, 5.3f, 16.9f);
        lip.close();
        lipTop.moveTo(5.3f, 16.9f);
        lipTop.cubicTo(8.6f, 17.7f, 15.4f, 17.7f, 18.7f, 16.9f);

        band1.moveTo(7.15f, 9.0f);
        band1.cubicTo(9.5f, 9.7f, 14.5f, 9.7f, 16.85f, 9.0f);
        band2.moveTo(6.8f, 14.9f);
        band2.cubicTo(9.2f, 15.7f, 14.8f, 15.7f, 17.2f, 14.9f);

        sheen.moveTo(9.2f, 6.2f);
        sheen.cubicTo(8.3f, 7.8f, 8.2f, 11.4f, 8.0f, 15.0f);
    }

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float s = Math.min(b.width(), b.height()) / BOX;
        int save = alpha < 255 ? c.saveLayerAlpha(b.left, b.top, b.right, b.bottom, alpha) : c.save();
        c.clipRect(b);
        c.translate(b.exactCenterX() - 12f * s, b.exactCenterY() - 12f * s);
        c.scale(s, s);

        // clapper first: the body hides its top half
        c.save();
        c.rotate(clapperAngle, PX, PY);
        c.drawLine(12f, 8f, 12f, 20.1f, rodPaint);
        c.drawCircle(12f, 21.15f, 1.15f, ballPaint);
        c.drawCircle(12f, 21.15f, 1.15f, ballEdge);
        c.restore();

        c.save();
        c.rotate(angle, PX, PY);
        c.drawCircle(12f, 2.35f, 1.0f, loopDark);
        c.drawCircle(12f, 2.35f, 1.0f, loopBrass);
        c.drawRoundRect(crown, 1.2f, 1.2f, crownPaint);
        c.drawRoundRect(crown, 1.2f, 1.2f, outline);
        c.drawPath(body, bodyPaint);
        drawBand(c, band1);
        drawBand(c, band2);
        c.drawPath(lip, lipPaint);
        c.drawPath(lipTop, groove);
        // the light is fixed, so the highlight slides against the swing
        float shift = -angle * 0.035f;
        c.translate(shift, 0f);
        c.drawPath(sheen, sheenPaint);
        c.drawLine(7.0f, 17.55f, 8.6f, 17.95f, sheenPaint);
        c.translate(-shift, 0f);
        c.drawPath(body, outline);
        c.restore();

        c.restoreToCount(save);
    }

    private void drawBand(Canvas c, Path band) {
        c.drawPath(band, bandDark);
        c.translate(0f, 0.38f);
        c.drawPath(band, bandLight);
        c.translate(0f, -0.38f);
    }

    private static Paint fill() {
        return new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    private static Paint stroke(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(BOX * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(BOX * density);
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
        for (Paint p : paints) p.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
