package com.gathin.photodream;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen mode: loops through the pictures sync.sh put in the app's pictures/ folder, in filename order,
 * each slowly zooming and drifting (Ken Burns) and crossfading into the next after seconds.txt (20 s by
 * default). Two screen-sized pictures at most, held as GPU bitmaps. MP4 clips play once, muted and cropped to
 * fill the screen, on the hardware decoder (one player at a time), then the loop moves on.
 */
final class Slides {

    private static final long FADE_MS = 1500L;
    /** How far a picture zooms in over its time on screen; it drifts at most half the extra width. */
    private static final float ZOOM = 1.12f;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    /** The month and year of the picture on screen, or null when the option is off. */
    private final TextView caption;
    private final View empty;
    private ExecutorService decoder;
    private ImageView front;
    private ImageView back;
    private final TextureView video;
    private MediaPlayer player;
    private Surface surface;
    /** A clip waiting for the TextureView's surface. */
    private File pendingClip;
    /** Bumped for every clip so callbacks of a released player are ignored. */
    private int clipGeneration;
    private final List<File> images;
    private final SharedPreferences preferences;
    private final long interval;
    private final int width;
    private final int height;
    private boolean running;

    private final Runnable advance = this::showNext;

    Slides(Context context, FrameLayout stage, TextView caption, View empty) {
        this.context = context;
        this.caption = caption;
        this.empty = empty;
        video = new TextureView(context);
        video.setAlpha(0f);
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
                surface = new Surface(texture);
                File clip = pendingClip;
                pendingClip = null;
                if (clip != null && running) playVideo(clip);
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                stopVideo();
                if (surface != null) surface.release();
                surface = null;
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
        });
        stage.addView(video, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        back = image(stage);
        front = image(stage);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        preferences = context.getSharedPreferences("photo_dream", Context.MODE_PRIVATE);
        File dir = context.getExternalFilesDir(null);
        interval = Media.seconds(Photos.read(dir == null ? null : new File(dir, Media.SECONDS))) * 1000L;
        images = dir == null ? new ArrayList<>() : Media.list(new File(dir, Media.PICTURES));
        if (caption != null) caption.setAlpha(0f);
    }

    private ImageView image(FrameLayout stage) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setAlpha(0f);
        stage.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return view;
    }

    void start() {
        running = true;
        empty.setVisibility(images.isEmpty() ? View.VISIBLE : View.GONE);
        if (decoder == null) decoder = Executors.newSingleThreadExecutor();
        showNext();
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (decoder != null) decoder.shutdownNow();
        decoder = null;
        if (front != null) { cancel(front); release(front); }
        if (back != null) { cancel(back); release(back); }
        pendingClip = null;
        stopVideo();
    }

    /** Decodes the next picture off the main thread, then fades it in over the current one. */
    private void showNext() {
        if (!running || images.isEmpty() || decoder == null) return;
        decoder.execute(() -> {
            Bitmap bitmap = null;
            File clip = null;
            String date = "";
            // Skip unreadable pictures, but stop after one round so a folder of broken files doesn't spin.
            for (int tries = 0; tries < images.size() && bitmap == null && clip == null; tries++) {
                File file = Media.next(images, preferences.getString("image", ""));
                preferences.edit().putString("image", file.getName()).apply();
                if (Media.video(file.getName())) {
                    clip = file;
                } else {
                    bitmap = decode(file);
                    if (bitmap != null && caption != null) date = Photos.dateOf(file);
                }
            }
            Bitmap shown = bitmap;
            File playing = clip;
            String shownDate = date;
            handler.post(() -> {
                if (!running) {
                    if (shown != null) shown.recycle();
                    return;
                }
                if (playing != null) {
                    showCaption("");
                    playVideo(playing);   // moves on when the clip ends
                    return;
                }
                if (shown != null) {
                    crossfade(shown);
                    showCaption(shownDate);
                }
                // A single picture still drifts on: it fades into itself with a new zoom.
                handler.postDelayed(advance, interval);
            });
        });
    }

    private void showCaption(String date) {
        if (caption == null) return;
        caption.animate().cancel();
        if (date.isEmpty()) {
            caption.animate().alpha(0f).setDuration(FADE_MS / 2).start();
            return;
        }
        caption.animate().alpha(0f).setDuration(FADE_MS / 2).withEndAction(() -> {
            caption.setText(date);
            caption.animate().alpha(1f).setDuration(FADE_MS / 2).start();
        }).start();
    }

    private void crossfade(Bitmap bitmap) {
        ImageView incoming = back;
        ImageView outgoing = front;
        back = outgoing;
        front = incoming;
        cancel(incoming);
        release(incoming);
        incoming.setImageBitmap(bitmap);
        incoming.setTag(bitmap);
        incoming.bringToFront();
        // Start zoomed in or out at random and drift toward a random spot, over the whole slide.
        boolean zoomIn = random.nextBoolean();
        float from = zoomIn ? 1f : ZOOM;
        float to = zoomIn ? ZOOM : 1f;
        float dx = (random.nextFloat() - 0.5f) * width * (ZOOM - 1f);
        float dy = (random.nextFloat() - 0.5f) * height * (ZOOM - 1f);
        incoming.setAlpha(0f);
        ObjectAnimator fade = ObjectAnimator.ofFloat(incoming, View.ALPHA, 0f, 1f).setDuration(FADE_MS);
        ObjectAnimator drift = ObjectAnimator.ofPropertyValuesHolder(incoming,
                PropertyValuesHolder.ofFloat(View.SCALE_X, from, to),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, from, to),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, zoomIn ? 0f : dx, zoomIn ? dx : 0f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, zoomIn ? 0f : dy, zoomIn ? dy : 0f))
                .setDuration(interval + 2 * FADE_MS);
        drift.setInterpolator(new LinearInterpolator());
        AnimatorSet set = new AnimatorSet();
        set.playTogether(fade, drift);
        incoming.setTag(R.id.animation, set);
        set.start();
        boolean afterClip = player != null;
        handler.postDelayed(() -> {
            cancel(outgoing);
            outgoing.setAlpha(0f);
            release(outgoing);
            if (afterClip) stopVideo();
        }, FADE_MS + 100);
    }

    /** Fades the clip in over whatever shows now and plays it to the end (or loops a lone clip). */
    private void playVideo(File clip) {
        if (surface == null) {
            pendingClip = clip;
            return;
        }
        boolean clipShowing = player != null;
        stopVideo();
        int generation = ++clipGeneration;
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setDataSource(clip.getPath());
            mp.setSurface(surface);
            mp.setVolume(0f, 0f);
            mp.setLooping(images.size() == 1);
            mp.setOnVideoSizeChangedListener((p, w, h) -> cover(w, h));
            mp.setOnPreparedListener(p -> {
                if (generation != clipGeneration || !running) return;
                p.start();
                video.bringToFront();
                if (clipShowing) video.setAlpha(1f);
                else video.animate().alpha(1f).setDuration(FADE_MS).start();
                handler.postDelayed(() -> {
                    if (generation != clipGeneration) return;
                    for (ImageView view : new ImageView[] {front, back}) {
                        cancel(view);
                        view.setAlpha(0f);
                        release(view);
                    }
                }, FADE_MS + 100);
            });
            mp.setOnCompletionListener(p -> {
                if (generation == clipGeneration && running) showNext();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                if (generation == clipGeneration && running) {
                    stopVideo();
                    handler.postDelayed(advance, 1000);
                }
                return true;
            });
            mp.prepareAsync();
        } catch (Exception failure) {
            stopVideo();
            handler.postDelayed(advance, 1000);
        }
    }

    /** Scales the clip to fill the screen, cropping the overflow evenly (like CENTER_CROP). */
    private void cover(int videoWidth, int videoHeight) {
        int w = video.getWidth(), h = video.getHeight();
        if (videoWidth <= 0 || videoHeight <= 0 || w <= 0 || h <= 0) return;
        float scale = Math.max((float) w / videoWidth, (float) h / videoHeight);
        Matrix matrix = new Matrix();
        matrix.setScale(videoWidth * scale / w, videoHeight * scale / h, w / 2f, h / 2f);
        video.setTransform(matrix);
    }

    private void stopVideo() {
        clipGeneration++;
        video.animate().cancel();
        video.setAlpha(0f);
        if (player != null) {
            try { player.stop(); } catch (IllegalStateException ignored) { }
            player.release();
            player = null;
        }
    }

    private static void cancel(ImageView view) {
        Object set = view.getTag(R.id.animation);
        if (set instanceof AnimatorSet) ((AnimatorSet) set).cancel();
        view.setTag(R.id.animation, null);
    }

    /** Frees the view's picture now instead of waiting for the garbage collector. */
    private static void release(ImageView view) {
        Object old = view.getTag();
        view.setImageDrawable(null);
        view.setTag(null);
        if (old instanceof Bitmap) ((Bitmap) old).recycle();
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int w = options.outWidth, h = options.outHeight;
            if (w <= 0 || h <= 0) return null;
            int sample = Media.sampleSize(w, h, width, height);
            if (!Media.accepted(w / sample, h / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = Media.coverScale(w / sample, h / sample, width, height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
