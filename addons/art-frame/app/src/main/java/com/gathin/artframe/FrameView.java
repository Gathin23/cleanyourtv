package com.gathin.artframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The frame: draws the art into its own surface. On a 4K panel whose apps run at 1080p the surface buffer is set to
 * the panel's own size, so the picture is drawn with every one of its pixels rather than upscaled; the matte, frame,
 * caption and clock are drawn into the same buffer. A still picture is drawn once and left alone; the screen is only
 * redrawn every frame while a crossfade, the slow zoom or a fading caption needs it.
 */
final class FrameView extends SurfaceView implements SurfaceHolder.Callback, Choreographer.FrameCallback {
    private static final long FADE_MS = 1500;
    private static final long CAPTION_MS = 10000;
    private static final long TICK_MS = 30000;
    private static final long FRAME_MS = 33;

    private final Library library;
    private final Prefs prefs;
    private final boolean dream;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private List<Artwork> playlist = new ArrayList<>();
    private int index;
    private Slide current;
    private Slide previous;
    private int bufW;
    private int bufH;
    private boolean surfaceReady;
    private boolean running;
    private boolean frameQueued;
    private boolean hardwareCanvas = true;
    private long lastDraw;
    private long captionUntil;
    private int loadToken;
    private String emptyMessage = "";
    /** 4K through the video decoder (see VideoStill), when the setting is on and the TV can. */
    private VideoStill video;
    private boolean videoMode;
    private android.graphics.Bitmap videoCanvas;
    private String videoKey = "";
    private boolean videoBusy;
    /** What the decoder's kept pixels show; only touched on the decoder thread, cleared for a new decoder. */
    private volatile String videoBaseKey = "";
    /** Set while drawing the 4K picture, whose brightness and contrast are applied later, during conversion. */
    private volatile boolean noFilter;
    private RenderListener renderListener;

    /** Told when a 4K picture starts being made and when it is on the screen. */
    interface RenderListener {
        void rendering(boolean busy);
    }
    private boolean videoAgain;
    /** While adjusting a piece: no caption or clock over it. */
    private boolean adjusting;

    private static final class DecoderRefused extends Exception {
        DecoderRefused(Exception cause) {
            super(cause);
        }
    }

    private static final class Slide {
        final Artwork art;
        final Bitmap bitmap;
        final long shownAt;

        Slide(Artwork art, Bitmap bitmap, long shownAt) {
            this.art = art;
            this.bitmap = bitmap;
            this.shownAt = shownAt;
        }
    }

    FrameView(Context context, boolean dream) {
        super(context);
        this.dream = dream;
        library = Library.get(context);
        prefs = new Prefs(context);
        getHolder().addCallback(this);
        setFocusable(false);
    }

    // --- control from the activity or dream ---------------------------------------------------------------

    void setPlaylist(List<Artwork> list, String startId) {
        playlist = new ArrayList<>(list);
        if (prefs.shuffle() && playlist.size() > 1) Collections.shuffle(playlist);
        index = 0;
        for (int i = 0; i < playlist.size(); i++) if (playlist.get(i).id.equals(startId)) index = i;
        emptyMessage = getContext().getString(dream ? R.string.empty_dream : R.string.empty_frame);
        load();
    }

    void step(int delta) {
        if (playlist.size() < 2) {
            showCaption();
            return;
        }
        index = Math.floorMod(index + delta, playlist.size());
        load();
    }

    void showCaption() {
        captionUntil = now() + CAPTION_MS;
        requestDraw();
        // The video route redraws only on change: come back when the caption should be gone.
        main.postDelayed(this::requestDraw, CAPTION_MS + 1100);
    }

    void setRenderListener(RenderListener l) {
        renderListener = l;
    }

    private void busy(boolean on) {
        if (renderListener != null) renderListener.rendering(on);
    }

    void setAdjusting(boolean on) {
        adjusting = on;
        requestDraw();
    }

    /** Draws the current piece again, after its brightness, contrast or matte changed. */
    void refresh() {
        requestDraw();
    }

    Artwork currentArt() {
        return current == null ? null : current.art;
    }

    void start() {
        running = true;
        main.removeCallbacks(tick);
        main.postDelayed(tick, TICK_MS);
        requestDraw();
    }

    void stop() {
        running = false;
        main.removeCallbacksAndMessages(null);
        Choreographer.getInstance().removeFrameCallback(this);
        frameQueued = false;
    }

    void release() {
        stop();
        releaseVideo();
        decoder.shutdown();
        current = null;
        previous = null;
    }

    // --- surface -------------------------------------------------------------------------------------------

    /**
     * On a 4K panel whose apps run at a lower resolution, with the 4K setting on, pictures go through the video
     * decoder; a SurfaceView buffer set to 4K would reach the app layer, which such TVs show at 1080p or not at all.
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        videoMode = false;
        Display d = getDisplay();
        if (prefs.uhd() && d != null) {
            Display.Mode mode = d.getMode();
            int pw = Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight());
            if (pw >= VideoStill.W && getWidth() > 0 && getWidth() < pw && VideoStill.supported()) {
                try {
                    video = new VideoStill(holder.getSurface());
                    videoMode = true;
                    videoKey = "";
                    videoBaseKey = "";
                    Log.i(Library.TAG, "showing pictures at 3840x2160 through the video decoder");
                } catch (Exception e) {
                    Log.w(Library.TAG, "no 4K video route, drawing at " + getWidth() + "x" + getHeight() + ": " + e);
                }
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        int w = videoMode ? VideoStill.W : width;
        int h = videoMode ? VideoStill.H : height;
        boolean resized = w != bufW || h != bufH;
        bufW = w;
        bufH = h;
        surfaceReady = true;
        if (resized && !playlist.isEmpty()) load();
        else requestDraw();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseVideo();
    }

    private void releaseVideo() {
        VideoStill v = video;
        video = null;
        videoMode = false;
        if (v != null) decoder.execute(v::release);
    }

    int bufferWidth() {
        return bufW;
    }

    // --- loading ------------------------------------------------------------------------------------------

    private void load() {
        main.removeCallbacks(advance);
        if (playlist.isEmpty() || bufW == 0) {
            requestDraw();
            return;
        }
        Artwork art = playlist.get(index);
        File f = library.picture(art);
        int w = bufW;
        int h = bufH;
        int token = ++loadToken;
        boolean hw = hardwareCanvas && !videoMode;
        decoder.execute(() -> {
            Bitmap bm = null;
            try {
                bm = Images.decodeForScreen(f, w, h, hw);
            } catch (Exception | OutOfMemoryError e) {
                Log.w(Library.TAG, "cannot show " + art.title + ": " + e);
            }
            Bitmap result = bm;
            main.post(() -> {
                if (token != loadToken) return;
                if (result != null) {
                    previous = !videoMode && prefs.crossfade() && current != null ? current : null;
                    current = new Slide(art, result, now());
                    if (!"off".equals(prefs.caption())) showCaption();
                }
                scheduleAdvance();
                requestDraw();
            });
        });
    }

    private void scheduleAdvance() {
        main.removeCallbacks(advance);
        int s = prefs.seconds();
        if (running && s > 0 && playlist.size() > 1) main.postDelayed(advance, s * 1000L);
    }

    private final Runnable advance = () -> step(1);

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            requestDraw();
            if (running) main.postDelayed(this, TICK_MS);
        }
    };

    // --- drawing ------------------------------------------------------------------------------------------

    private void requestDraw() {
        if (!frameQueued) {
            frameQueued = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        frameQueued = false;
        if (!surfaceReady) return;
        long t = now();
        if (t - lastDraw < FRAME_MS && animating(t)) {
            requestDraw();
            return;
        }
        lastDraw = t;
        drawOnce(t);
        if (running && animating(t)) requestDraw();
    }

    private boolean animating(long t) {
        if (current == null || videoMode) return false;
        if (!prefs.isArtTime()) return false;
        if (previous != null && t - current.shownAt < FADE_MS) return true;
        if (prefs.motion()) return true;
        return "change".equals(prefs.caption()) && t < captionUntil + 1000;
    }

    private void drawOnce(long t) {
        if (videoMode) {
            drawVideo(t);
            return;
        }
        SurfaceHolder h = getHolder();
        Canvas c = null;
        try {
            if (hardwareCanvas) {
                try {
                    c = h.lockHardwareCanvas();
                } catch (Exception e) {
                    // No GPU canvas on this TV: draw in software, and decode future pictures for it.
                    hardwareCanvas = false;
                    Log.i(Library.TAG, "software canvas: " + e);
                    if (current != null) load();
                    return;
                }
            } else {
                c = h.lockCanvas();
            }
            if (c == null) return;
            render(c, t, current, previous);
        } finally {
            if (c != null) h.unlockCanvasAndPost(c);
        }
    }

    /**
     * The video route: the whole frame (matte, frame, caption, clock, dimming) is drawn into a 4K picture and handed
     * to the decoder, but only when something on it changed. Falls back to normal drawing if the decoder refuses.
     */
    private void drawVideo(long t) {
        Slide cur = current;
        boolean captionOn = "always".equals(prefs.caption()) || t < captionUntil;
        String clock = prefs.clock() ? DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()) : "";
        // Everything drawn into the picture except brightness and contrast, which are applied while converting.
        String baseKey = (cur == null ? "-" : cur.art.id + "/" + cur.art.matte + "/" + cur.art.fill + "/" + cur.art.whole
                + "/" + cur.art.focus + "/" + cur.art.matteSize + "/" + cur.art.matteTexture + "/" + cur.art.depth)
                + "|" + captionOn + "|" + clock + "|" + prefs.dimFactor() + "|" + prefs.isArtTime() + "|" + prefs.signature()
                + "|" + playlist.size() + "|" + adjusting;
        String key = baseKey + (cur == null ? "" : "|" + cur.art.brightness + "/" + cur.art.contrast);
        int bri = cur == null ? 0 : cur.art.brightness;
        int con = cur == null ? 0 : cur.art.contrast;
        if (key.equals(videoKey)) {
            if (!videoBusy) busy(false);
            return;
        }
        if (videoBusy) {
            // A picture is being made: make the newest one when it is done, not every step in between.
            videoAgain = true;
            return;
        }
        videoKey = key;
        videoBusy = true;
        busy(true);
        long renderAt = captionOn ? t : Math.max(t, captionUntil + 1000);
        VideoStill v = video;
        decoder.execute(() -> {
            if (v != video) return;
            try {
                if (videoCanvas == null) {
                    videoCanvas = Bitmap.createBitmap(VideoStill.W, VideoStill.H, Bitmap.Config.ARGB_8888);
                }
                Slide drawn = cur;
                if (cur != null && cur.bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                    // Loaded before the video route started: a GPU bitmap cannot be drawn in software.
                    drawn = new Slide(cur.art, cur.bitmap.copy(Bitmap.Config.ARGB_8888, false), cur.shownAt);
                }
                if (!baseKey.equals(videoBaseKey)) {
                    // Draw the matte, frame and picture only when they changed; brightness and contrast alone don't.
                    long t0 = System.nanoTime();
                    noFilter = true;
                    try {
                        render(new Canvas(videoCanvas), renderAt, drawn, null);
                    } finally {
                        noFilter = false;
                    }
                    v.setPicture(videoCanvas);
                    videoBaseKey = baseKey;
                    Log.i(Library.TAG, "4K picture drawn in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
                }
                PcmStill.Adjust adjust = null;
                if (drawn != null && prefs.isArtTime()) {
                    RectF r = placement(drawn, VideoStill.W, VideoStill.H);
                    adjust = new PcmStill.Adjust(bri, con, Math.round(r.left), Math.round(r.top),
                            Math.round(r.right), Math.round(r.bottom));
                }
                try {
                    v.show(adjust);
                } catch (Exception first) {
                    // One more go with a fresh decoder before giving up on the 4K route for this visit.
                    Log.w(Library.TAG, "4K picture failed, retrying with a new decoder", first);
                    v.release();
                    VideoStill fresh;
                    try {
                        fresh = new VideoStill(getHolder().getSurface());
                        fresh.setPicture(videoCanvas);
                        fresh.show(adjust);
                    } catch (Exception second) {
                        throw new DecoderRefused(second);
                    }
                    VideoStill swapped = fresh;
                    main.post(() -> {
                        if (video == v) video = swapped;
                        else swapped.release();
                    });
                }
                main.post(() -> {
                    videoBusy = false;
                    if (videoAgain) {
                        videoAgain = false;
                        requestDraw();
                    } else {
                        busy(false);
                    }
                });
            } catch (Exception | OutOfMemoryError e) {
                Log.w(Library.TAG, "4K video route failed, drawing normally for now", e);
                main.post(() -> {
                    videoBusy = false;
                    busy(false);
                    if (v != video) return;
                    releaseVideo();
                    bufW = getWidth();
                    bufH = getHeight();
                    load();
                });
            }
        });
    }

    private void render(Canvas c, long t, Slide current, Slide previous) {
        int w = c.getWidth();
        int h = c.getHeight();
        if (!prefs.isArtTime()) {
            c.drawColor(Color.BLACK);
            return;
        }
        if (current == null) {
            c.drawColor(0xFF101114);
            if (playlist.isEmpty()) drawCentered(c, emptyMessage, w, h);
            return;
        }
        String matte = effectiveMatte(current.art);
        boolean hasMatte = !"none".equals(matte);
        int matteColor = hasMatte ? Prefs.matteColor(matte) : Color.BLACK;
        c.drawColor(matteColor);
        if (hasMatte) Matte.drawTexture(c, effectiveTexture(current.art), w, h);

        float fade = previous == null ? 1f : Math.min(1f, (t - current.shownAt) / (float) FADE_MS);
        if (previous != null && fade < 1f) drawPicture(c, previous, w, h, hasMatte, matteColor, t, 1f);
        else if (previous != null && previous == this.previous) this.previous = null;
        drawPicture(c, current, w, h, hasMatte, matteColor, t, fade);

        if (!adjusting) {
            drawCaption(c, current.art, w, h, hasMatte, matteColor, t);
            if (prefs.clock()) drawClock(c, w, h, matteColor);
        }

        float dim = prefs.dimFactor();
        if (dim < 1f) c.drawColor(Color.argb(Math.round((1f - dim) * 255), 0, 0, 0));
    }

    private String effectiveSize(Artwork a) {
        return "auto".equals(a.matteSize) ? prefs.matteSize() : a.matteSize;
    }

    private String effectiveDepth(Artwork a) {
        return "auto".equals(a.depth) ? prefs.depth() : a.depth;
    }

    /**
     * The picture sunk behind the matte: light falls from the top left, so the matte's cut edge casts a soft shadow
     * across the top and left of the picture and a fainter one at the bottom and right, and the bevel of the cut
     * catches the light on its lower and right faces.
     */
    private void drawDepth(Canvas c, RectF r, int h, int matteColor, int alpha, String depth) {
        if ("none".equals(depth)) return;
        boolean deep = "deep".equals(depth);
        float s = h * (deep ? 0.026f : 0.013f);
        int dark = Math.round(alpha * (deep ? 0.62f : 0.42f));
        int faint = Math.round(dark * 0.35f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.save();
        c.clipRect(r);
        p.setShader(new android.graphics.LinearGradient(0, r.top, 0, r.top + s,
                Color.argb(dark, 0, 0, 0), 0, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(r.left, r.top, r.right, r.top + s, p);
        p.setShader(new android.graphics.LinearGradient(r.left, 0, r.left + s, 0,
                Color.argb(dark, 0, 0, 0), 0, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(r.left, r.top, r.left + s, r.bottom, p);
        float s2 = s * 0.45f;
        p.setShader(new android.graphics.LinearGradient(0, r.bottom, 0, r.bottom - s2,
                Color.argb(faint, 0, 0, 0), 0, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(r.left, r.bottom - s2, r.right, r.bottom, p);
        p.setShader(new android.graphics.LinearGradient(r.right, 0, r.right - s2, 0,
                Color.argb(faint, 0, 0, 0), 0, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(r.right - s2, r.top, r.right, r.bottom, p);
        c.restore();
        // The bevel: a narrow band around the opening, lit on the bottom and right, in shade on the top and left.
        p.setShader(null);
        float e = Math.max(2f, h * (deep ? 0.0045f : 0.003f));
        p.setColor(blend(matteColor, 0xFFFFFFFF, 0.55f));
        p.setAlpha(alpha);
        c.drawRect(r.left - e, r.bottom, r.right + e, r.bottom + e, p);
        c.drawRect(r.right, r.top - e, r.right + e, r.bottom + e, p);
        p.setColor(blend(matteColor, 0xFF000000, 0.22f));
        p.setAlpha(alpha);
        c.drawRect(r.left - e, r.top - e, r.right + e, r.top, p);
        c.drawRect(r.left - e, r.top - e, r.left, r.bottom, p);
    }

    private String effectiveTexture(Artwork a) {
        return "auto".equals(a.matteTexture) ? prefs.matteTexture() : a.matteTexture;
    }

    private String effectiveMatte(Artwork a) {
        if (a.fill) return "none";
        if (!"auto".equals(a.matte)) return a.matte;
        return prefs.matte() ? prefs.matteColour() : "none";
    }

    /** Where a piece is drawn on a w x h canvas: the whole screen when it fills it, otherwise as pictureRect. */
    private RectF placement(Slide s, int w, int h) {
        if (s.art.fill) return new RectF(0, 0, w, h);
        return pictureRect(s, w, h, !"none".equals(effectiveMatte(s.art)));
    }

    /** Where the picture sits: fitted inside the screen, or inside the matte's opening. */
    private RectF pictureRect(Slide s, int w, int h, boolean hasMatte) {
        float margin = hasMatte ? h * Matte.margin(effectiveSize(s.art)) : 0f;
        float border = prefs.border() ? h * 0.012f : 0f;
        float boxW = w - 2 * (margin + border);
        float boxH = h - 2 * (margin + border);
        float left0 = (w - boxW) / 2f;
        float top0 = (h - boxH) / 2f;
        if (hasMatte && !s.art.whole) {
            // "Matte, even": the picture fills the opening, cropped to its shape, so the matte is equally thick on
            // every side. "Matte, whole picture" shows all of it, centred, the matte wider where it must be.
            return new RectF(left0, top0, left0 + boxW, top0 + boxH);
        }
        float k = Math.min(boxW / s.bitmap.getWidth(), boxH / s.bitmap.getHeight());
        float pw = s.bitmap.getWidth() * k;
        float ph = s.bitmap.getHeight() * k;
        float left = (w - pw) / 2f;
        float top = (h - ph) / 2f;
        return new RectF(left, top, left + pw, top + ph);
    }

    private void drawPicture(Canvas c, Slide s, int w, int h, boolean hasMatte, int matteColor, long t, float alpha) {
        RectF r = pictureRect(s, w, h, hasMatte);
        int a = Math.round(alpha * 255);
        if (prefs.border()) {
            float b = h * 0.012f;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            // A dark moulding with a thin light inner edge, as a frame around the picture.
            p.setColor(0xFF2B231D);
            p.setAlpha(a);
            c.drawRect(r.left - b, r.top - b, r.right + b, r.bottom + b, p);
            p.setColor(0xFF6B5A4A);
            p.setAlpha(a);
            float e = Math.max(1f, h * 0.0015f);
            c.drawRect(r.left - e, r.top - e, r.right + e, r.bottom + e, p);
        } else if (hasMatte && "none".equals(effectiveDepth(s.art))) {
            // The bevel of a cut matte: a slightly darker line around the opening.
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(blend(matteColor, 0xFF000000, 0.18f));
            p.setAlpha(a);
            float e = Math.max(1f, h * 0.002f);
            c.drawRect(r.left - e, r.top - e, r.right + e, r.bottom + e, p);
        }
        paint.setAlpha(a);
        paint.setColorFilter(noFilter ? null : Images.filter(s.art.brightness, s.art.contrast, 1f));
        if (s.art.fill) {
            // Edge to edge: the screen-shaped part of the picture, no matte or frame.
            int[] crop = Artwork.fillCrop(s.bitmap.getWidth(), s.bitmap.getHeight(), w, h, s.art.focus);
            android.graphics.Rect src = new android.graphics.Rect(crop[0], crop[1], crop[2], crop[3]);
            RectF full = new RectF(0, 0, w, h);
            c.save();
            c.clipRect(full);
            if (prefs.motion()) applyMotion(c, s, full, t);
            c.drawBitmap(s.bitmap, src, full, paint);
            c.restore();
            paint.setColorFilter(null);
            return;
        }
        c.save();
        c.clipRect(r);
        if (prefs.motion()) applyMotion(c, s, r, t);
        // When the picture fills a matte opening of a slightly different shape, show the matching part of it.
        android.graphics.Rect src = null;
        float pictureRatio = s.bitmap.getWidth() / (float) s.bitmap.getHeight();
        if (Math.abs(pictureRatio - r.width() / r.height()) > 0.005f * pictureRatio) {
            int[] crop = Artwork.fillCrop(s.bitmap.getWidth(), s.bitmap.getHeight(),
                    Math.round(r.width()), Math.round(r.height()), s.art.focus);
            src = new android.graphics.Rect(crop[0], crop[1], crop[2], crop[3]);
        }
        c.drawBitmap(s.bitmap, src, r, paint);
        c.restore();
        paint.setColorFilter(null);
        if (hasMatte) drawDepth(c, r, h, matteColor, a, effectiveDepth(s.art));
    }

    /** The slow zoom: up to 6% larger over the piece's time, drifting toward a corner chosen from the piece. */
    private void applyMotion(Canvas c, Slide s, RectF r, long t) {
        long span = Math.max(30000, prefs.seconds() * 1000L);
        float p = Math.min(1f, (t - s.shownAt) / (float) span);
        float zoom = 1f + 0.06f * p;
        int dir = (s.art.id.hashCode() & 3);
        float dx = (dir & 1) == 0 ? -1 : 1;
        float dy = (dir & 2) == 0 ? -1 : 1;
        c.translate(r.centerX() + dx * r.width() * 0.02f * p, r.centerY() + dy * r.height() * 0.02f * p);
        c.scale(zoom, zoom);
        c.translate(-r.centerX(), -r.centerY());
    }

    private void drawCaption(Canvas c, Artwork a, int w, int h, boolean hasMatte, int matteColor, long t) {
        String mode = prefs.caption();
        if ("off".equals(mode) && t >= captionUntil) return;
        float alpha = 1f;
        if (!"always".equals(mode)) {
            if (t >= captionUntil + 1000) return;
            if (t > captionUntil) alpha = 1f - (t - captionUntil) / 1000f;
        }
        if (a.title.isEmpty()) return;
        boolean light = luminance(matteColor) > 0.5f && hasMatte;
        int ink = light ? 0xFF2A2724 : 0xFFF2EFE8;
        float size = h * 0.022f;
        float pad = h * 0.03f;
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        text.setTextSize(size);
        String title = TextUtils.ellipsize(a.title, text, w * 0.45f, TextUtils.TruncateAt.END).toString();
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        text.setTextSize(size * 0.75f);
        String by = TextUtils.ellipsize(a.byline(), text, w * 0.45f, TextUtils.TruncateAt.END).toString();
        float right = w - pad;
        float bottom = h - pad;
        if (!hasMatte) {
            // Over the picture: a soft dark plate behind the words.
            text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            text.setTextSize(size);
            float tw = Math.max(text.measureText(title), text.measureText(by) * 0.75f);
            Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
            plate.setColor(Color.argb(Math.round(150 * alpha), 0, 0, 0));
            c.drawRoundRect(right - tw - pad, bottom - size * 2.6f - pad * 0.6f, right + pad * 0.5f, bottom + pad * 0.5f,
                    size * 0.4f, size * 0.4f, plate);
        }
        int al = Math.round(alpha * 255);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        text.setTextSize(size);
        text.setColor(ink);
        text.setAlpha(al);
        c.drawText(title, right, bottom - size * 1.05f, text);
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        text.setTextSize(size * 0.75f);
        text.setAlpha(Math.round(al * 0.75f));
        c.drawText(by, right, bottom, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private void drawClock(Canvas c, int w, int h, int matteColor) {
        String s = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        text.setTextSize(h * 0.03f);
        text.setColor(luminance(matteColor) > 0.5f ? 0xCC2A2724 : 0xCCF2EFE8);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(s, w - h * 0.03f, h * 0.06f, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCentered(Canvas c, String s, int w, int h) {
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        text.setTextSize(h * 0.03f);
        text.setColor(0xFFD9D4CA);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(s, w / 2f, h / 2f, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private static float luminance(int color) {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f;
    }

    private static int blend(int a, int b, float k) {
        return Color.rgb(Math.round(Color.red(a) * (1 - k) + Color.red(b) * k),
                Math.round(Color.green(a) * (1 - k) + Color.green(b) * k),
                Math.round(Color.blue(a) * (1 - k) + Color.blue(b) * k));
    }

    private static long now() {
        return android.os.SystemClock.uptimeMillis();
    }
}
