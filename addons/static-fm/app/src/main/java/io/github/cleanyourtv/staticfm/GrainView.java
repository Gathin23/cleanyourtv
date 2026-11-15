package io.github.cleanyourtv.staticfm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Renders on its own thread up to 30 times a second: reads the sound, steps the envelopes, paints the
 * 960x540 frame and scales it to the screen without filtering, so every glyph stays a crisp block of pixels.
 * A card names the track that is playing; a line naming the current choices fades in after each change.
 */
public class GrainView extends SurfaceView implements SurfaceHolder.Callback {
    private static final long FRAME_MS = 33, LABEL_MS = 3500;

    private final int[] frame = new int[Painter.W * Painter.H];
    private final Bitmap bitmap = Bitmap.createBitmap(Painter.W, Painter.H, Bitmap.Config.ARGB_8888);
    private final Painter painter = new Painter();
    private final Levels levels = new Levels();
    private final Sound sound = new Sound();
    private final Prefs prefs;
    private final Paint pixels = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF box = new android.graphics.RectF();
    private final Rect src = new Rect(0, 0, Painter.W, Painter.H), dst = new Rect();
    private final long start = SystemClock.uptimeMillis();

    private HandlerThread thread;
    private Handler handler;
    private volatile boolean surface, running;
    private volatile int style, palette, reaction;
    private volatile String labelText;
    /** The track playing, looked up every couple of seconds on the render thread; null for none. */
    private NowPlaying nowPlaying;
    private String track;
    private long trackCheckedAt;
    private volatile long labelUntil;
    private int frames;
    private long lastFrameMs;

    public GrainView(Context context) {
        super(context);
        prefs = new Prefs(context);
        style = prefs.style();
        palette = prefs.palette();
        reaction = prefs.reaction();
        pixels.setFilterBitmap(false);
        label.setTypeface(Typeface.MONOSPACE);
        getHolder().addCallback(this);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running || !surface) return;
            long t0 = SystemClock.uptimeMillis();
            drawFrame(t0);
            long spent = SystemClock.uptimeMillis() - t0;
            lastFrameMs = spent;
            handler.postDelayed(this, Math.max(5, FRAME_MS - spent));
        }
    };

    private void drawFrame(long now) {
        float t = (now - start) / 1000f;
        float bass = 0, mid = 0, high = 0;
        boolean heard = sound.read();
        if (heard) {
            bass = sound.bass;
            mid = sound.mid;
            high = sound.high;
        }
        levels.update(bass, mid, high, Prefs.REACTIONS[reaction], t);
        if (++frames % 200 == 0) {
            // about every 7 s: adb logcat -s StaticFM shows whether the TV's sound reaches the picture
            Log.i("StaticFM", "sound=" + heard + " raw bass=" + bass + " mid=" + mid + " level bass=" + levels.bass
                    + " style=" + style + " last frame ms=" + lastFrameMs);
        }
        painter.lively = reaction == 2;
        painter.render(frame, style, Palette.ALL[palette], t, levels.bass, levels.mid, levels.high);
        bitmap.setPixels(frame, 0, Painter.W, 0, 0, Painter.W, Painter.H);
        SurfaceHolder holder = getHolder();
        Canvas canvas = lock(holder);
        if (canvas == null) return;
        try {
            dst.set(0, 0, canvas.getWidth(), canvas.getHeight());
            canvas.drawBitmap(bitmap, src, dst, pixels);
            if (now - trackCheckedAt > 2000) {
                trackCheckedAt = now;
                if (nowPlaying == null) nowPlaying = new NowPlaying(getContext());
                track = nowPlaying.read();
            }
            if (track != null) drawTrackCard(canvas);
            long left = labelUntil - now;
            if (left > 0 && labelText != null) {
                label.setTextSize(canvas.getHeight() / 36f);
                label.setColor(0xFFD8D2C4);
                label.setAlpha((int) (230 * Math.min(1f, left / 600f)));
                canvas.drawText(labelText, canvas.getWidth() * 0.05f, canvas.getHeight() * 0.94f, label);
            }
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    /**
     * What is playing, on a dark card in the top left so it reads over the busy picture: the title, then a thin
     * progress bar between the time played and the track's length.
     */
    private void drawTrackCard(Canvas canvas) {
        float w = canvas.getWidth(), h = canvas.getHeight();
        float pad = h / 60f, titleSize = h / 46f, timeSize = h / 62f, gap = h / 110f;
        String elapsed = null, total = null;
        float fraction = -1;
        if (nowPlaying.durationMs > 0) {
            long pos = nowPlaying.position(SystemClock.elapsedRealtime());
            elapsed = NowPlaying.clock(pos);
            total = NowPlaying.clock(nowPlaying.durationMs);
            fraction = pos / (float) nowPlaying.durationMs;
        }
        label.setTextSize(titleSize);
        float titleW = label.measureText(track);
        float barW = Math.max(h / 4.5f, Math.min(titleW, h / 2.6f));
        label.setTextSize(timeSize);
        float timeW = elapsed == null ? 0 : label.measureText(total);
        float rowW = elapsed == null ? 0 : timeW * 2 + barW + gap * 4;
        float cardW = Math.max(titleW, rowW) + pad * 2;
        float cardH = pad * 2 + titleSize + (elapsed == null ? 0 : gap * 2 + timeSize);
        float left = w * 0.04f, top = h * 0.05f;
        box.set(left, top, left + cardW, top + cardH);
        card.setStyle(Paint.Style.FILL);
        card.setColor(0xF0121317);
        canvas.drawRoundRect(box, h / 90f, h / 90f, card);

        float x = left + pad, y = box.top + pad + titleSize * 0.82f;
        label.setTextSize(titleSize);
        label.setColor(0xFFE2DCCD);
        canvas.drawText(track, x, y, label);
        if (elapsed == null) return;
        float rowY = y + gap * 2 + timeSize;
        label.setTextSize(timeSize);
        label.setColor(0xFFA8A296);
        canvas.drawText(elapsed, x, rowY, label);
        canvas.drawText(total, x + timeW + gap * 4 + barW, rowY, label);
        // the bar: a thin line, filled up to where the track is
        float barX = x + timeW + gap * 2, barY = rowY - timeSize * 0.32f, thick = Math.max(2f, h / 360f);
        card.setColor(0x40E2DCCD);
        box.set(barX, barY - thick / 2, barX + barW, barY + thick / 2);
        canvas.drawRoundRect(box, thick, thick, card);
        card.setColor(0xD0E2DCCD);
        box.set(barX, barY - thick / 2, barX + barW * Math.max(0, Math.min(1, fraction)), barY + thick / 2);
        canvas.drawRoundRect(box, thick, thick, card);
    }

    /** The GPU canvas scales the frame to the screen far faster than the CPU one; the CPU one is the fallback. */
    private boolean hardwareFailed;

    private Canvas lock(SurfaceHolder holder) {
        // a surface on its way out refuses both kinds; that is no reason to give up on the GPU for good
        if (!holder.getSurface().isValid()) return null;
        if (!hardwareFailed) {
            try {
                return holder.lockHardwareCanvas();
            } catch (RuntimeException e) {
                if (!holder.getSurface().isValid()) return null;
                hardwareFailed = true;
            }
        }
        return holder.lockCanvas();
    }

    public void start() {
        running = true;
        if (thread == null) {
            thread = new HandlerThread("staticfm-render");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        handler.post(() -> sound.open(getContext()));
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    public void stop() {
        running = false;
        if (thread != null) {
            handler.removeCallbacks(tick);
            handler.post(sound::close);
            thread.quitSafely();
            thread = null;
            handler = null;
        }
    }

    /** Re-opens the sound, after the permission was granted. */
    public void reopenSound() {
        if (handler != null) handler.post(() -> sound.open(getContext()));
    }

    /** Shows one style without saving it: for screenshots, am start ... --ei style 5. */
    public void preview(int previewStyle) {
        style = Math.floorMod(previewStyle, Painter.STYLES);
        showLabel();
    }

    public void nextStyle(int d) {
        style = Math.floorMod(style + d, Painter.STYLES);
        saved();
    }

    public void nextPalette(int d) {
        palette = Math.floorMod(palette + d, Palette.ALL.length);
        saved();
    }

    public void nextReaction() {
        reaction = Math.floorMod(reaction + 1, Prefs.REACTIONS.length);
        saved();
    }

    private void saved() {
        prefs.set(style, palette, reaction);
        showLabel();
    }

    public void showLabel() {
        Context c = getContext();
        String[] styles = c.getResources().getStringArray(R.array.styles);
        String[] reactions = c.getResources().getStringArray(R.array.reactions);
        labelText = styles[style] + "  ·  " + c.getString(Palette.ALL[palette].nameRes) + "  ·  " + reactions[reaction];
        labelUntil = SystemClock.uptimeMillis() + LABEL_MS;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = true;
        if (running && handler != null) {
            handler.removeCallbacks(tick);
            handler.post(tick);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surface = false;
    }
}
