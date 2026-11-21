package com.gathin.photodream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The sharp option: the pictures drawn into a surface the size of the TV's panel instead of into the window.
 * Many 4K TVs lay their apps out at 1920x1080 and enlarge the result, so a picture shown in a view never has
 * more than 1080 lines of its own. A SurfaceView's buffer can be any size, and one the size of the panel
 * reaches it pixel for pixel. The motion is the view path's in Slides: a slow zoom and drift, and a crossfade
 * into the next picture. Two panel-sized pictures at most, about 33 MB each at 4K, which is why this is a
 * switch and off by default.
 */
final class SharpStage implements SurfaceHolder.Callback {

    /** A frame that takes longer than this on average is drawn every other refresh instead. */
    private static final float SLOW_FRAME_MS = 12f;

    /** One picture and its motion. Only the render thread reads or writes these. */
    private static final class Layer {
        Bitmap bitmap;
        long start;
        long duration;
        boolean zoomIn;
        float dx;
        float dy;
        /** The animation option is off: no zoom or drift, the picture only fades in. */
        boolean still;
    }

    final SurfaceView view;
    final int width;
    final int height;

    private final Object surfaceLock = new Object();
    /** Guarded by surfaceLock: null while the surface is gone, so nothing is drawn into a dead one. */
    private Surface surface;
    private HandlerThread thread;
    private Handler render;

    // Render thread only, from here down.
    private Choreographer choreographer;
    private Layer front;
    private Layer back;
    /** Pictures no longer drawn, recycled one frame later so the frame that last drew them is done. */
    private final List<Bitmap> retired = new ArrayList<>();
    private boolean frameQueued;
    private boolean skipNext;
    private float frameMs;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Random random = new Random();
    private final Choreographer.FrameCallback frame = nanos -> {
        frameQueued = false;
        draw();
    };

    /** The panel's own size in pixels, which can be more than the window's; null when the TV does not say. */
    static int[] panel(Context context) {
        DisplayManager manager = context.getSystemService(DisplayManager.class);
        Display display = manager == null ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
        Display.Mode mode = display == null ? null : display.getMode();
        if (mode == null || mode.getPhysicalWidth() <= 0 || mode.getPhysicalHeight() <= 0) return null;
        int w = mode.getPhysicalWidth(), h = mode.getPhysicalHeight();
        // A TV is wider than tall; a mode reported the other way round is the same panel.
        return w >= h ? new int[]{w, h} : new int[]{h, w};
    }

    SharpStage(Context context, int width, int height) {
        this.width = width;
        this.height = height;
        view = new SurfaceView(context);
        SurfaceHolder holder = view.getHolder();
        holder.setFixedSize(width, height);
        holder.setFormat(PixelFormat.RGBX_8888);
        holder.addCallback(this);
    }

    void start() {
        if (thread != null) return;
        thread = new HandlerThread("photo-sharp");
        thread.start();
        render = new Handler(thread.getLooper());
        render.post(() -> choreographer = Choreographer.getInstance());
    }

    void stop() {
        HandlerThread t = thread;
        Handler h = render;
        thread = null;
        render = null;
        if (t == null || h == null) return;
        h.post(() -> {
            if (choreographer != null) choreographer.removeFrameCallback(frame);
            frameQueued = false;
            release();
        });
        t.quitSafely();
    }

    /** Fades the picture in over the one showing, still or moving. The stage owns the bitmap from here on. */
    void show(Bitmap bitmap, long duration, boolean still) {
        Handler h = render;
        if (h == null) {
            bitmap.recycle();
            return;
        }
        h.post(() -> {
            if (back != null) retired.add(back.bitmap);
            back = front;
            Layer layer = new Layer();
            layer.bitmap = bitmap;
            layer.start = SystemClock.uptimeMillis();
            layer.duration = Math.max(1, duration);
            // Start zoomed in or out at random and drift toward a random spot, as the view path does.
            layer.still = still;
            layer.zoomIn = still || random.nextBoolean();
            layer.dx = still ? 0f : (random.nextFloat() - 0.5f) * width * (Slides.ZOOM - 1f);
            layer.dy = still ? 0f : (random.nextFloat() - 0.5f) * height * (Slides.ZOOM - 1f);
            front = layer;
            schedule();
        });
    }

    /** Drops the pictures and leaves the surface black: a clip plays in the view above it. */
    void clear() {
        Handler h = render;
        if (h == null) return;
        h.post(() -> {
            release();
            draw();
        });
    }

    private void release() {
        for (Bitmap bitmap : retired) bitmap.recycle();
        retired.clear();
        if (front != null) front.bitmap.recycle();
        if (back != null) back.bitmap.recycle();
        front = null;
        back = null;
    }

    private void schedule() {
        if (frameQueued || choreographer == null) return;
        frameQueued = true;
        choreographer.postFrameCallback(frame);
    }

    private void draw() {
        for (Bitmap bitmap : retired) bitmap.recycle();
        retired.clear();
        if (front != null && frameMs > SLOW_FRAME_MS && (skipNext = !skipNext)) {
            schedule();
            return;
        }
        long now = SystemClock.uptimeMillis();
        float alpha = front == null ? 0f : Math.min(1f, (now - front.start) / (float) Slides.FADE_MS);
        synchronized (surfaceLock) {
            if (surface == null || !surface.isValid()) return;   // surfaceCreated schedules the next frame
            Canvas canvas;
            try {
                canvas = surface.lockHardwareCanvas();
            } catch (RuntimeException gone) {
                return;
            }
            try {
                canvas.drawColor(Color.BLACK);
                if (back != null && alpha < 1f) paint(canvas, back, now, 1f);
                if (front != null) paint(canvas, front, now, alpha);
            } finally {
                surface.unlockCanvasAndPost(canvas);
            }
        }
        float took = SystemClock.uptimeMillis() - now;
        frameMs = frameMs == 0f ? took : frameMs * 0.9f + took * 0.1f;
        if (alpha >= 1f && back != null) {
            retired.add(back.bitmap);
            back = null;
        }
        // The drift never rests while a picture shows, so every refresh draws. A still picture needs frames only
        // until its fade is done and the one under it has been recycled.
        if (front != null && (!front.still || alpha < 1f || !retired.isEmpty())) schedule();
    }

    private void paint(Canvas canvas, Layer layer, long now, float alpha) {
        float t = layer.still ? 0f : Math.min(1f, Math.max(0f, (now - layer.start) / (float) layer.duration));
        float zoom = layer.zoomIn ? 1f + (Slides.ZOOM - 1f) * t : Slides.ZOOM - (Slides.ZOOM - 1f) * t;
        // The drift grows with the zoom, so the picture's edge never comes into view.
        float shift = layer.zoomIn ? t : 1f - t;
        int bw = layer.bitmap.getWidth(), bh = layer.bitmap.getHeight();
        float scale = Math.max(width / (float) bw, height / (float) bh) * zoom;
        matrix.reset();
        matrix.postTranslate(-bw / 2f, -bh / 2f);
        matrix.postScale(scale, scale);
        matrix.postTranslate(width / 2f + layer.dx * shift, height / 2f + layer.dy * shift);
        paint.setAlpha(Math.round(alpha * 255));
        canvas.drawBitmap(layer.bitmap, matrix, paint);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        attach(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        attach(holder);
    }

    /** The surface to draw into from now on, and a frame on it as soon as the render thread gets to it. */
    private void attach(SurfaceHolder holder) {
        synchronized (surfaceLock) {
            surface = holder.getSurface();
        }
        Handler handler = render;
        if (handler != null) handler.post(this::schedule);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Taken under the lock a frame holds while it draws: once this returns, nothing draws again.
        synchronized (surfaceLock) {
            surface = null;
        }
    }
}
