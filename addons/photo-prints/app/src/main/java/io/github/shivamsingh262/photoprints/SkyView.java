package io.github.shivamsingh262.photoprints;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import java.util.Calendar;

/**
 * A full-screen gradient that follows the clock (Sky): night blues, a warm horizon at sunset, a soft glow where
 * the sun sits. One small bitmap (480x270) stretched over the screen and redrawn at most once a minute, so it
 * costs a single full-screen draw per minute on a weak TV GPU.
 */
public class SkyView extends View {

    private static final int TEX_W = 480, TEX_H = 270;

    private int lastMinute = -1;
    private Bitmap texture;

    public SkyView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        update();
    }

    /** Picks up a new time of day; cheap and safe to call often. */
    public void update() {
        Calendar now = Calendar.getInstance();
        int minute = Sky.minuteOfDay(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        if (minute == lastMinute) return;
        lastMinute = minute;

        Sky.Palette sky = Sky.at(minute);
        boolean first = texture == null;
        if (first) texture = Bitmap.createBitmap(TEX_W, TEX_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(texture);
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0f, 0f, 0f, TEX_H,
                new int[]{sky.top, sky.mid, sky.bottom},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, TEX_W, TEX_H, paint);

        if (sky.sun > 0) {
            // A warm core low on the horizon, where the sun (or its afterglow) sits.
            int warm = Sky.lerpColor(sky.bottom, 0xFFFFD9A0, 0.65f);
            paint.setShader(new RadialGradient(
                    sky.sunX * TEX_W, TEX_H * 0.80f, TEX_W * 0.55f,
                    new int[]{(sky.sun << 24) | (warm & 0x00FFFFFF), warm & 0x00FFFFFF},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, TEX_W, TEX_H, paint);
        }

        if (first) {
            BitmapDrawable drawable = new BitmapDrawable(getResources(), texture);
            drawable.setGravity(Gravity.FILL);
            setBackground(drawable);
        }
        invalidate();
    }
}
