package com.gathin.photodream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Prints mode: three pictures as prints, each the whole picture with rounded corners and a slight tilt. Every
 * interval they slide off to the left one after another and three new ones are dealt in from the right. Clips are
 * left out in this mode. Adapted from Photo Prints by @shivamsingh262 (#936).
 */
final class Prints {

    private static final int PRINTS = 3;
    private static final long OUT_MS = 700L, OUT_STAGGER_MS = 120L;
    private static final long IN_MS = 800L, IN_STAGGER_MS = 180L;
    /** Decode size, in dp: a landscape photo's width, a portrait one's height. */
    private static final int LANDSCAPE_W_DP = 300, PORTRAIT_H_DP = 320;
    /** Each print's resting tilt in degrees; the middle one lies on top. */
    private static final int[] TILT = {-5, 3, -2};
    private static final int SIDE_DP = 48, TOP_DP = 20, COLUMN_GAP_DP = 24, BOTTOM_GAP_DP = 4, BOTTOM_DP = 48;
    private static final int JITTER_DP = 14, JITTER_Y_DP = 6, JITTER_DEG = 2;
    private static final int MAX_TILT_DEG = 7;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final float density;
    private final int screenW;
    private final int screenH;
    private final long interval;
    private final boolean dates;
    /** The clock line, to keep the prints above it; null when it is off. */
    private final View clock;
    private final View empty;
    private final File folder;
    private final View[] cards = new View[PRINTS];
    private final ImageView[] photoViews = new ImageView[PRINTS];
    private final TextView[] dateViews = new TextView[PRINTS];
    private ExecutorService loader;
    /** Loader thread only. */
    private final List<File> queue = new ArrayList<>();
    private boolean dealt;
    /** Bumped on stop, so a decode that finishes afterwards is dropped instead of shown. */
    private int generation;

    private static final class Print {
        Bitmap bitmap;
        String date;
    }

    private final Runnable next = this::loadNext;

    Prints(Context context, FrameLayout stage, View clock, View empty, boolean dates) {
        this.context = context;
        this.clock = clock;
        this.empty = empty;
        this.dates = dates;
        density = context.getResources().getDisplayMetrics().density;
        screenW = context.getResources().getDisplayMetrics().widthPixels;
        screenH = context.getResources().getDisplayMetrics().heightPixels;
        File dir = context.getExternalFilesDir(null);
        folder = dir == null ? null : new File(dir, Media.PICTURES);
        interval = Media.seconds(Photos.read(dir == null ? null : new File(dir, Media.SECONDS))) * 1000L;
        stage.setClipChildren(false);
        LayoutInflater inflater = LayoutInflater.from(context);
        float shadowRoom = context.getResources().getDimensionPixelSize(R.dimen.print_shadow_room);
        for (int i = 0; i < PRINTS; i++) {
            cards[i] = inflater.inflate(R.layout.print, stage, false);
            // Drawn into a layer with a clear margin, the tilted print is filtered as one image: smooth edges.
            cards[i].setLayerType(View.LAYER_TYPE_HARDWARE, null);
            cards[i].setBackground(new PrintShadow(shadowRoom, 16 * density, 22 * density, 4 * density, 0x73000000));
            cards[i].findViewById(R.id.printCard).setClipToOutline(true);
            photoViews[i] = cards[i].findViewById(R.id.printPhoto);
            dateViews[i] = cards[i].findViewById(R.id.printDate);
            cards[i].setTranslationX(screenW * 2f);   // off screen until the first deal
            stage.addView(cards[i]);
        }
        cards[1].bringToFront();
    }

    void start() {
        if (loader == null) loader = Executors.newSingleThreadExecutor();
        handler.post(next);
    }

    void stop() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (loader != null) loader.shutdownNow();
        loader = null;
        dealt = false;
        for (int i = 0; i < PRINTS; i++) {
            cards[i].animate().cancel();
            cards[i].setTranslationX(screenW * 2f);
            release(photoViews[i]);
        }
    }

    private void loadNext() {
        int gen = generation;
        ExecutorService l = loader;
        if (l == null) return;
        l.execute(() -> {
            Print[] batch = new Print[PRINTS];
            Set<File> used = new HashSet<>();
            for (int i = 0; i < PRINTS; i++) {
                try {
                    batch[i] = nextPrint(used);
                } catch (OutOfMemoryError e) {
                    batch[i] = null;
                }
            }
            handler.post(() -> {
                if (gen != generation) {
                    for (Print p : batch) if (p != null && p.bitmap != null) p.bitmap.recycle();
                    return;
                }
                sweep(batch);
                handler.postDelayed(next, interval);
            });
        });
    }

    /** Loader thread only: the next picture in a shuffled round, decoded near its print size. */
    private Print nextPrint(Set<File> used) {
        for (int tries = 0; tries < 4; tries++) {
            if (queue.isEmpty()) {
                for (File f : Media.list(folder)) if (Media.image(f.getName())) queue.add(f);
                queue.removeAll(used);
                Collections.shuffle(queue, random);
            }
            if (queue.isEmpty()) return null;
            File file = queue.remove(0);
            if (!used.add(file)) continue;
            int[] size = Photos.size(file);
            if (size == null) continue;
            int w, h;
            if (size[1] > size[0]) {
                h = Math.round(PORTRAIT_H_DP * density);
                w = h * size[0] / size[1];
            } else {
                w = Math.round(LANDSCAPE_W_DP * density);
                h = w * size[1] / size[0];
            }
            Bitmap bitmap = Photos.decode(file, w, h);
            if (bitmap == null) continue;
            Print p = new Print();
            p.bitmap = bitmap;
            p.date = dates ? Photos.dateOf(file) : "";
            return p;
        }
        return null;
    }

    private void sweep(Print[] batch) {
        boolean none = true;
        for (Print p : batch) none &= p == null;
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        long dealAt = 0;
        if (dealt) {
            for (int i = 0; i < PRINTS; i++) {
                View card = cards[i];
                card.animate().translationX(-(screenW + card.getWidth() + 200f))
                        .rotation(card.getRotation() - 12f)
                        .setStartDelay(i * OUT_STAGGER_MS).setDuration(OUT_MS)
                        .setInterpolator(new AccelerateInterpolator(1.6f)).start();
            }
            dealAt = OUT_MS + (PRINTS - 1) * OUT_STAGGER_MS + 60;
        }
        dealt = true;
        handler.postDelayed(() -> deal(batch), dealAt);
    }

    /** Where the prints must end: above the clock line, or near the bottom when the clock is off. */
    private float areaBottom() {
        if (clock == null || clock.getHeight() == 0) return screenH - BOTTOM_DP * density;
        return clock.getTop() - BOTTOM_GAP_DP * density - 6 * density;
    }

    /** Three equal columns: each print as large as its column allows, tilted, with a little play. */
    private void deal(Print[] batch) {
        float side = SIDE_DP * density, jitter = JITTER_DP * density, jitterY = JITTER_Y_DP * density;
        float colGap = COLUMN_GAP_DP * density;
        int shadowRoom = context.getResources().getDimensionPixelSize(R.dimen.print_shadow_room);
        float areaTop = TOP_DP * density, areaBottom = areaBottom();
        float colW = (screenW - 2 * side - (PRINTS - 1) * colGap) / PRINTS;
        float boxW = colW - 2 * jitter, boxH = areaBottom - areaTop - 2 * jitterY;
        double rad = Math.toRadians(MAX_TILT_DEG);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        for (int i = 0; i < PRINTS; i++) {
            Print p = batch[i];
            View card = cards[i];
            ImageView photo = photoViews[i];
            release(photo);
            if (p == null) {
                card.setVisibility(View.INVISIBLE);
                continue;
            }
            float a = (float) p.bitmap.getWidth() / p.bitmap.getHeight();
            float h = Math.min(boxW / (a * cos + sin), boxH / (a * sin + cos));
            int ph = Math.max(1, Math.round(h)), pw = Math.max(1, Math.round(h * a));
            float cx = side + colW / 2f + i * (colW + colGap) + (random.nextFloat() * 2 - 1) * jitter;
            float cy = (areaTop + areaBottom) / 2f + (random.nextFloat() * 2 - 1) * jitterY;
            float tilt = TILT[i] + (random.nextFloat() * 2 - 1) * JITTER_DEG;
            card.setVisibility(View.VISIBLE);
            photo.setImageBitmap(p.bitmap);
            ViewGroup.LayoutParams lp = photo.getLayoutParams();
            lp.width = pw;
            lp.height = ph;
            photo.setLayoutParams(lp);
            dateViews[i].setText(p.date);
            dateViews[i].setVisibility(p.date.isEmpty() ? View.GONE : View.VISIBLE);
            ViewGroup.LayoutParams dp = dateViews[i].getLayoutParams();
            dp.width = pw;
            dateViews[i].setLayoutParams(dp);
            FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) card.getLayoutParams();
            cp.leftMargin = Math.round(cx - pw / 2f) - shadowRoom;
            cp.topMargin = Math.round(cy - ph / 2f) - shadowRoom;
            card.setLayoutParams(cp);
            card.animate().cancel();
            card.setTranslationX(screenW + 200f);
            card.setRotation(tilt + 12f);
            card.animate().translationX(0f).rotation(tilt)
                    .setStartDelay(i * IN_STAGGER_MS).setDuration(IN_MS)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    private static void release(ImageView view) {
        Drawable d = view.getDrawable();
        view.setImageDrawable(null);
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null && !b.isRecycled()) b.recycle();
        }
    }
}
