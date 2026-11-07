package io.github.nottoobright.koipond;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Random;

/**
 * The pond on a render thread: steps the model, turns its drops into ripples, renders the 480x270 pixel frame and
 * scales it to the screen without filtering, then draws the breathing label and the hint at full resolution.
 * Frames are paced by vsync (Choreographer on the render thread's looper): one frame per 1/30 s, so every second
 * vsync on a 60 Hz panel. Calls from the UI thread only set flags; the model and the renderer are built and touched
 * by the render thread alone.
 */
public class PondView extends SurfaceView implements SurfaceHolder.Callback, Choreographer.FrameCallback {

    private static final String TAG = "KoiPond";
    private static final int W = PondModel.W, H = PondModel.H;
    private static final long FRAME_NS = 1_000_000_000L / 30;
    /** A vsync this much before a full frame interval still draws, so vsync jitter never skips a frame. */
    private static final long FRAME_SLACK_NS = 4_000_000L;
    private static final long MAX_STEP_NS = 100_000_000L;
    private static final int STATS_FRAMES = 300;
    private static final int MAX_DROPS = 64;
    private static final long HINT_MS = 6000, HINT_FADE_MS = 1000;
    private static final float RING_FADE_MS = 600f;
    /** The ripple when the breath turns: TURN_DROPS small drops round the ring, each about as hard as a rain drop. */
    private static final int TURN_RIPPLE = 150, TURN_DROPS = 16;
    /** Null hardware canvases in a row, with the surface valid, before the software canvas is tried. */
    private static final int HARDWARE_NULL_LIMIT = 3;
    private static final int LABEL_COLOR = 0xEAF6F0;
    private static final int LABEL_ALPHA = 250;
    private static final int TEXT_SHADOW_ALPHA = 0xD8;
    /** A soft dark pill behind the breathing label, so it reads on the bright daytime water too. */
    private static final int PILL_COLOR = 0x08241F, PILL_ALPHA = 125;

    private final long seed;
    private final int[] frame = new int[W * H];
    private final Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final Random rng;

    private final Paint pixelPaint = new Paint();
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();
    private float pillHalfW, pillPad, pillRadius;
    private final Rect src = new Rect(0, 0, W, H);
    private final Rect dst = new Rect();
    private final float shadowR, shadowDy;

    private final String breatheIn, breatheOut, hint;
    private final String[] numbers = new String[10];

    private final float[] dropX = new float[MAX_DROPS];
    private final float[] dropY = new float[MAX_DROPS];
    private final int[] dropStrength = new int[MAX_DROPS];

    private final long[] frameNs = new long[STATS_FRAMES];
    private final long[] sortedNs = new long[STATS_FRAMES];
    private int statsCount;

    // Set on the UI thread, consumed by the render thread at the start of the next frame.
    private volatile boolean breathWanted;
    private volatile int pebbleRequests, rainRequests;
    private volatile int fixedMinute = -1;
    private volatile boolean showHint;

    // Render-thread state. The model and renderer are built on the first frame, off the UI thread, and kept for
    // the view's life; a restarted render thread sees them through the join and start in between.
    private PondModel model;
    private PondRenderer renderer;
    private Choreographer choreographer;
    private long lastFrameNs;
    private boolean breathing;
    private long breathStart;
    /** The ring's opacity 0..1, eased in and out; its size, label and count are held while it fades out. */
    private float ringFade, ringSize;
    /** Whether the last frame was an in-breath; a change sends the turn ripple. A new guide starts on an in-breath. */
    private boolean wasInhaling = true;
    private String ringLabel;
    private int ringSecs;
    private int pebblesDone, rainsDone;
    private int appliedFixedMinute = -2;
    private long nextClockNs;
    private Palette palette;
    private float fireflies;
    private long modelNs, modelMs;
    private boolean useHardwareCanvas = true;
    private int hardwareNulls;

    // UI-thread lifecycle.
    private boolean started, surfaceReady;
    private HandlerThread thread;
    private volatile boolean alive;

    public PondView(Context context) {
        super(context);
        seed = System.nanoTime();
        rng = new Random(seed ^ 0x5DEECE66DL);
        getHolder().addCallback(this);
        setKeepScreenOn(true);

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        shadowR = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, dm);
        shadowDy = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, dm);
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);

        pixelPaint.setFilterBitmap(false);
        pixelPaint.setAntiAlias(false);
        pixelPaint.setDither(false);

        Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        setupText(labelPaint, regular, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30, dm));
        setupText(numberPaint, regular, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20, dm));
        setupText(hintPaint, light, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16, dm));

        breatheIn = context.getString(R.string.breathe_in);
        breatheOut = context.getString(R.string.breathe_out);
        hint = context.getString(R.string.hint);
        for (int i = 0; i < numbers.length; i++) numbers[i] = Integer.toString(i);

        pillPaint.setColor(PILL_COLOR | 0xFF000000);
        pillPad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22, dm);
        pillRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18, dm);
        pillHalfW = Math.max(labelPaint.measureText(breatheIn), labelPaint.measureText(breatheOut)) / 2f + pillPad;
    }

    private void setupText(Paint p, Typeface face, float size) {
        p.setTypeface(face);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(LABEL_COLOR | 0xFF000000);
        p.setAlpha(LABEL_ALPHA);
        p.setShadowLayer(shadowR, 0, shadowDy, TEXT_SHADOW_ALPHA << 24);
    }

    /** Fades text and its shadow together; a translucent shadow colour keeps its own alpha otherwise. */
    private void fadeText(Paint p, float f) {
        int alpha = Math.round(LABEL_ALPHA * f);
        if (p.getAlpha() == alpha) return;
        p.setAlpha(alpha);
        p.setShadowLayer(shadowR, 0, shadowDy, Math.round(TEXT_SHADOW_ALPHA * f) << 24);
    }

    // ---- Public API (UI thread) ----

    /** Starts drawing once the surface exists too. */
    public void start() {
        started = true;
        startThread();
    }

    /** Stops drawing and waits for the render thread to finish. */
    public void stop() {
        started = false;
        stopThread();
    }

    /** Starts or stops the breathing guide; the koi gather around the ring while it runs. */
    public void toggleBreath() {
        breathWanted = !breathWanted;
    }

    /** Drops a pebble at a random spot in the middle half of the pond. */
    public void pebble() {
        pebbleRequests++;
    }

    /** Fixes the clock at a minute of the day (0..1439); a negative value follows the TV clock again. */
    public void setFixedMinute(int minute) {
        fixedMinute = minute < 0 ? -1 : minute % 1440;
    }

    /** Starts a rain shower at once. */
    public void startRain() {
        rainRequests++;
    }

    /** Shows the key hint for the first seconds. */
    public void setShowHint(boolean show) {
        showHint = show;
    }

    // ---- Surface ----

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        startThread();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // The render thread reads the canvas size every frame.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        stopThread();
    }

    private void startThread() {
        if (!started || !surfaceReady || thread != null) return;
        alive = true;
        HandlerThread t = new HandlerThread("KoiPond");
        t.start();
        thread = t;
        new Handler(t.getLooper()).post(this::begin);
    }

    private void stopThread() {
        HandlerThread t = thread;
        if (t == null) return;
        alive = false;
        // A frame in progress finishes first; no frame callback runs after the looper quits.
        t.quit();
        boolean interrupted = false;
        while (true) {
            try {
                t.join();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        thread = null;
        if (interrupted) Thread.currentThread().interrupt();
    }

    // ---- Render thread ----

    private void begin() {
        if (model == null) {
            model = new PondModel(seed);
            renderer = new PondRenderer(seed);
        }
        nextClockNs = 0;
        lastFrameNs = 0;
        choreographer = Choreographer.getInstance();
        choreographer.postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!alive) return;
        choreographer.postFrameCallback(this);
        if (lastFrameNs != 0 && frameTimeNanos - lastFrameNs < FRAME_NS - FRAME_SLACK_NS) return;
        // Time steps come from vsync timestamps, so render-time jitter does not reach the motion.
        long deltaNs = lastFrameNs == 0 ? 0L : Math.max(0L, Math.min(MAX_STEP_NS, frameTimeNanos - lastFrameNs));
        lastFrameNs = frameTimeNanos;
        // The model clock only runs while drawing, so a pause does not jump the fish or the rain.
        modelNs += deltaNs;
        modelMs = modelNs / 1_000_000L;

        long start = System.nanoTime();
        frame(getHolder(), deltaNs / 1e9f, start);
        recordStats(System.nanoTime() - start);
    }

    private void frame(SurfaceHolder holder, float dt, long nowNs) {
        long now = modelMs;
        consumeRequests(now);
        updateClock(nowNs);

        model.step(dt, now);

        Ripples ripples = renderer.ripples();
        int n = model.drainDrops(dropX, dropY, dropStrength, MAX_DROPS);
        for (int i = 0; i < n; i++) {
            int s = dropStrength[i];
            int r = s <= 160 ? 2 : s <= 320 ? 3 : 4;
            ripples.drop(((int) dropX[i]) >> 1, ((int) dropY[i]) >> 1, r, -s);
        }
        ripples.step();

        updateRing(dt, now);
        renderer.render(model, palette, fireflies, ringFade > 0f ? ringSize : -1f, ringFade, now, frame);
        bitmap.setPixels(frame, 0, W, 0, 0, W, H);

        Canvas canvas = lockCanvas(holder);
        if (canvas == null) return;
        try {
            draw(canvas, now);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void consumeRequests(long now) {
        boolean want = breathWanted;
        if (want != breathing) {
            breathing = want;
            breathStart = now;
            model.setGather(want, W / 2f, H / 2f);
        }
        int pebbles = pebbleRequests;
        while (pebblesDone != pebbles) {
            pebblesDone++;
            float x = W / 4f + rng.nextFloat() * (W / 2f);
            float y = H / 4f + rng.nextFloat() * (H / 2f);
            model.scatter(x, y);
        }
        int rains = rainRequests;
        if (rainsDone != rains) {
            rainsDone = rains;
            model.startRain(now);
        }
    }

    /** Eases the ring in while breathing and out after it, holding the last size and label as it fades. */
    private void updateRing(float dt, long now) {
        float step = dt * 1000f / RING_FADE_MS;
        ringFade = breathing ? Math.min(1f, ringFade + step) : Math.max(0f, ringFade - step);
        if (!breathing) {
            wasInhaling = true;
            return;
        }
        long e = now - breathStart;
        boolean inhaling = Breath.inhaling(e);
        if (inhaling != wasInhaling) {
            // The breath turns: a soft ripple rolls out from the ring, a cue that needs no reading.
            // Small drops all round the ring, not one wide disc: a disc that size leaves a dark hole in the middle.
            float cells = (PondRenderer.RING_MIN + Breath.size(e) * PondRenderer.RING_GROW) / 2f;
            Ripples ripples = renderer.ripples();
            for (int i = 0; i < TURN_DROPS; i++) {
                double a = i * 2 * Math.PI / TURN_DROPS;
                ripples.drop(W / 4 + (int) Math.round(Math.cos(a) * cells),
                        H / 4 + (int) Math.round(Math.sin(a) * cells), 2, -TURN_RIPPLE);
            }
            wasInhaling = inhaling;
        }
        ringSize = Breath.size(e);
        ringLabel = inhaling ? breatheIn : breatheOut;
        ringSecs = Breath.secondsLeft(e);
    }

    /** The minute of day, palette and fireflies change slowly: recomputed once a second, or when the fixed minute changes. */
    private void updateClock(long nowNs) {
        int fixed = fixedMinute;
        if (palette != null && fixed == appliedFixedMinute && nowNs < nextClockNs) return;
        appliedFixedMinute = fixed;
        nextClockNs = nowNs + 1_000_000_000L;
        float minute;
        if (fixed >= 0) {
            minute = fixed;
        } else {
            LocalTime t = LocalTime.now();
            minute = t.getHour() * 60 + t.getMinute() + t.getSecond() / 60f + t.getNano() / 60e9f;
        }
        palette = TimeOfDay.at(minute);
        fireflies = TimeOfDay.fireflies(minute);
    }

    /**
     * The hardware canvas, or the software one where it does not work. SurfaceView's lockHardwareCanvas catches a
     * failure itself and returns null, so a run of nulls while the surface is valid is the sign to switch; a single
     * null, or one the software canvas shares, is only the surface not being ready yet.
     */
    private Canvas lockCanvas(SurfaceHolder holder) {
        if (useHardwareCanvas) {
            Canvas c = null;
            try {
                c = holder.lockHardwareCanvas();
            } catch (RuntimeException e) {
                Log.w(TAG, "lockHardwareCanvas failed, using lockCanvas", e);
                useHardwareCanvas = false;
            }
            if (c != null) {
                hardwareNulls = 0;
                return c;
            }
            if (useHardwareCanvas) {
                if (!holder.getSurface().isValid() || ++hardwareNulls < HARDWARE_NULL_LIMIT) return null;
                hardwareNulls = 0;
                Canvas software = holder.lockCanvas();
                if (software != null) {
                    Log.w(TAG, "lockHardwareCanvas keeps returning null, using lockCanvas");
                    useHardwareCanvas = false;
                }
                return software;
            }
        }
        return holder.lockCanvas();
    }

    private void draw(Canvas canvas, long now) {
        int cw = canvas.getWidth(), ch = canvas.getHeight();
        // Largest 16:9 rectangle that fits, centred; on a 16:9 TV it is the whole surface.
        int w = cw, h = cw * H / W;
        if (h > ch) {
            h = ch;
            w = ch * W / H;
        }
        int left = (cw - w) / 2, top = (ch - h) / 2;
        dst.set(left, top, left + w, top + h);
        if (w != cw || h != ch) canvas.drawColor(Color.BLACK);
        canvas.drawBitmap(bitmap, src, dst, pixelPaint);

        float scale = w / (float) W;
        if (ringFade > 0f && ringLabel != null) {
            fadeText(labelPaint, ringFade);
            fadeText(numberPaint, ringFade);
            float cx = left + w / 2f;
            float cy = top + (H / 2f + 80f) * scale;
            float base = cy + labelPaint.getTextSize() * 0.35f;
            float bottom = base + numberPaint.getTextSize() * 1.3f + pillPad * 0.6f;
            pill.set(cx - pillHalfW, base - labelPaint.getTextSize() - pillPad * 0.45f, cx + pillHalfW, bottom);
            pillPaint.setAlpha(Math.round(PILL_ALPHA * ringFade));
            canvas.drawRoundRect(pill, pillRadius, pillRadius, pillPaint);
            canvas.drawText(ringLabel, cx, base, labelPaint);
            if (ringSecs > 0 && ringSecs < numbers.length) {
                canvas.drawText(numbers[ringSecs], cx, base + numberPaint.getTextSize() * 1.3f, numberPaint);
            }
        }
        if (showHint && now < HINT_MS) {
            fadeText(hintPaint, now < HINT_MS - HINT_FADE_MS ? 1f : (HINT_MS - now) / (float) HINT_FADE_MS);
            float y = top + h - hintPaint.getTextSize() * 1.6f;
            canvas.drawText(hint, left + w / 2f, y, hintPaint);
        }
    }

    private void recordStats(long workNs) {
        frameNs[statsCount++] = workNs;
        if (statsCount < STATS_FRAMES) return;
        statsCount = 0;
        long sum = 0;
        for (long v : frameNs) sum += v;
        System.arraycopy(frameNs, 0, sortedNs, 0, STATS_FRAMES);
        Arrays.sort(sortedNs);
        long p99 = sortedNs[(int) Math.ceil(STATS_FRAMES * 0.99) - 1];
        Log.i(TAG, "frame avg " + (sum / STATS_FRAMES) / 1000 / 1000f + " ms, p99 " + p99 / 1000 / 1000f
                + " ms over " + STATS_FRAMES + " frames, " + (useHardwareCanvas ? "hardware" : "software") + " canvas");
    }
}
