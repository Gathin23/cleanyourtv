package io.github.luyzfernando08.tvstatic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/**
 * An off-the-air picture in one of three styles: snow (a small bitmap stretched without smoothing, so each
 * dot stays a hard block, with a slow rolling band), colour bars, or an old set's blue "no signal" screen.
 * Timeline: the tube opens from a bright line, the picture holds, then it fades and the window, translucent,
 * shows what is underneath.
 */
class StaticView extends View {
    static final int OPEN_MS = 300;
    static final int FADE_MS = 500;
    private static final int W = 240, H = 135;

    /** Snow time: the chosen length less the opening and the fade. */
    final int snowMs;
    private final int style;
    private final String noSignal, channel;
    private final Runnable done;
    private final Bitmap snow = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final int[] pixels = new int[W * H];
    private final Paint paint = new Paint();
    private final Paint fill = new Paint();
    /** The "no signal" on-screen display, drawn small without anti-aliasing and blown up: blocky like a 90s set. */
    private static final int OSD_W = 320, OSD_H = 180;
    private Bitmap osdOn, osdOff;
    private final Paint shade = new Paint();
    private final Paint scan = new Paint();
    private final Rect dst = new Rect();
    private final Rect full = new Rect();
    private long start;
    private long seed = System.nanoTime() | 1;
    private boolean finished;

    /** 75% colour bars, then the reversed strip and the bottom row of an SMPTE test card. */
    private static final int[] BARS_TOP = {0xFFC0C0C0, 0xFFC0C000, 0xFF00C0C0, 0xFF00C000, 0xFFC000C0, 0xFFC00000, 0xFF0000C0};
    private static final int[] BARS_MID = {0xFF0000C0, 0xFF131313, 0xFFC000C0, 0xFF131313, 0xFF00C0C0, 0xFF131313, 0xFFC0C0C0};
    private static final int[] BARS_LOW = {0xFF00214C, 0xFFFFFFFF, 0xFF32006A, 0xFF131313, 0xFF090909, 0xFF131313, 0xFF1D1D1D};

    StaticView(Context context, int seconds, int style, Runnable done) {
        super(context);
        this.style = style;
        noSignal = context.getString(R.string.no_signal);
        channel = context.getString(R.string.osd_channel);
        scan.setColor(0x38000000);
        snowMs = Math.max(500, seconds * 1000 - OPEN_MS - FADE_MS);
        this.done = done;
        paint.setFilterBitmap(false);
    }

    private int next() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (int) seed;
    }

    private void fillSnow(long t) {
        // A darker band rolls slowly down the picture, as when the vertical hold drifts.
        int band = (int) ((t / 6) % (H + 30)) - 30;
        for (int y = 0; y < H; y++) {
            boolean inBand = y >= band && y < band + 18;
            int row = y * W;
            for (int x = 0; x < W; x++) {
                int v = next() & 0xFF;
                if (inBand) v = v * 3 / 5;
                pixels[row + x] = 0xFF000000 | v << 16 | v << 8 | v;
            }
        }
        snow.setPixels(pixels, 0, W, 0, 0, W, H);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (start == 0) start = SystemClock.uptimeMillis();
        long t = SystemClock.uptimeMillis() - start;
        int w = getWidth(), h = getHeight();
        if (t >= OPEN_MS + snowMs + FADE_MS) {
            if (!finished) {
                finished = true;
                done.run();
            }
            return;
        }
        if (style == Prefs.STYLE_SNOW) fillSnow(t);
        full.set(0, 0, w, h);
        if (t < OPEN_MS) {
            // The tube opens: black, a bright line across the middle that grows into the picture.
            canvas.drawColor(Color.BLACK);
            float k = t / (float) OPEN_MS;
            int half = Math.max(2, (int) (h / 2f * k * k));
            dst.set(0, h / 2 - half, w, h / 2 + half);
            canvas.save();
            canvas.clipRect(dst);
            drawPicture(canvas, 255);
            canvas.restore();
            fill.setColor(Color.argb((int) (220 * (1 - k)), 255, 255, 255));
            canvas.drawRect(dst, fill);
        } else {
            int alpha = 255;
            if (t > OPEN_MS + snowMs) alpha = (int) (255 * (1 - (t - OPEN_MS - snowMs) / (float) FADE_MS));
            drawPicture(canvas, alpha);
        }
        postInvalidateOnAnimation();
    }

    private void drawPicture(Canvas canvas, int alpha) {
        int w = full.width(), h = full.height();
        if (style == Prefs.STYLE_BARS) {
            int top = h * 67 / 100, mid = h * 75 / 100;
            drawRow(canvas, BARS_TOP, 0, top, w, alpha);
            drawRow(canvas, BARS_MID, top, mid, w, alpha);
            // Bottom row: four wide blocks (each 5/4 of a bar), then the three near-black PLUGE steps.
            int x = 0, wide = w * 5 / 28;
            for (int i = 0; i < BARS_LOW.length; i++) {
                int right = i < 4 ? x + wide : (i == BARS_LOW.length - 1 ? w : x + (w - 4 * wide) / 3);
                fill.setColor(BARS_LOW[i]);
                fill.setAlpha(alpha);
                canvas.drawRect(x, mid, right, h, fill);
                x = right;
            }
        } else if (style == Prefs.STYLE_NO_SIGNAL) {
            if (osdOn == null) makeOsd(w, h);
            fill.setColor(0xFF1414A8);
            fill.setAlpha(alpha);
            canvas.drawRect(full, fill);
            // The centre text blinks once a second, as the set's own message did.
            paint.setAlpha(alpha);
            canvas.drawBitmap(SystemClock.uptimeMillis() / 600 % 2 == 0 ? osdOn : osdOff, null, full, paint);
            scan.setAlpha(alpha * 0x38 / 255);
            for (int y = 0; y < h; y += 4) canvas.drawRect(0, y, w, y + 2, scan);
            shade.setAlpha(alpha);
            canvas.drawRect(full, shade);
        } else {
            paint.setAlpha(alpha);
            canvas.drawBitmap(snow, null, full, paint);
        }
    }

    private void drawRow(Canvas canvas, int[] colors, int top, int bottom, int w, int alpha) {
        for (int i = 0; i < colors.length; i++) {
            fill.setColor(colors[i]);
            fill.setAlpha(alpha);
            canvas.drawRect(w * i / colors.length, top, w * (i + 1) / colors.length, bottom, fill);
        }
    }

    /** Two OSD frames (with and without the centre text) and the dark corners of a curved tube. */
    private void makeOsd(int w, int h) {
        osdOn = Bitmap.createBitmap(OSD_W, OSD_H, Bitmap.Config.ARGB_8888);
        osdOff = Bitmap.createBitmap(OSD_W, OSD_H, Bitmap.Config.ARGB_8888);
        Paint p = new Paint();                      // no anti-aliasing: hard pixel edges
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        for (Bitmap b : new Bitmap[]{osdOn, osdOff}) {
            Canvas c = new Canvas(b);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(14);
            p.setColor(0xFF000000);
            c.drawText(channel, 17, 25, p);
            p.setColor(0xFF4CFF4C);
            c.drawText(channel, 16, 24, p);
            if (b == osdOn) {
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(26);
                p.setColor(0xFF000000);
                c.drawText(noSignal, OSD_W / 2f + 2, OSD_H / 2f + 11, p);
                p.setColor(0xFFFFFFFF);
                c.drawText(noSignal, OSD_W / 2f, OSD_H / 2f + 9, p);
            }
        }
        shade.setShader(new RadialGradient(w / 2f, h / 2f, (float) Math.hypot(w, h) / 2f,
                new int[]{0x00000000, 0x00000000, 0xB0000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }
}
