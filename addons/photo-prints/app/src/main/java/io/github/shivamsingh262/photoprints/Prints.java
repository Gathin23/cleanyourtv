package io.github.shivamsingh262.photoprints;

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
 * The scene both the screensaver (PrintsDream) and the card's slideshow (SlideshowActivity) show: the owner's
 * photos as three prints on a time-of-day sky, each the whole photo with rounded corners and its month and year
 * written on it, above a line with the time and the date. Every 15 seconds the three slide off to the left one
 * after another and three new ones are dealt in from the right, each at a slight tilt. With no photos it shows
 * where to put them. No network: nothing leaves the TV.
 */
final class Prints {

    private static final int PRINTS = 3;
    private static final long SHOW_MS = 15_000L;
    private static final long OUT_MS = 700L, OUT_STAGGER_MS = 120L;
    private static final long IN_MS = 800L, IN_STAGGER_MS = 180L;
    /** Decode size, in dp: a landscape photo's width, a portrait one's height. deal() then fits it to its column. */
    private static final int LANDSCAPE_W_DP = 300, PORTRAIT_H_DP = 320;
    /** Each print's resting tilt in degrees; the middle one lies on top. */
    private static final int[] TILT = {-5, 3, -2};
    /** The gap at the screen's left and right edges and at its top; between two prints; above the clock line. */
    private static final int SIDE_DP = 48, TOP_DP = 20, COLUMN_GAP_DP = 24, STRIP_GAP_DP = 4;
    /** Random play around the rest position, so no two sets lie the same way. */
    private static final int JITTER_DP = 14, JITTER_Y_DP = 6, JITTER_DEG = 2;
    /** The most any print tilts: the steepest TILT plus JITTER_DEG. */
    private static final int MAX_TILT_DEG = 7;
    /** How far the clock line may wander so it does not burn in: sideways, and up (the room above is the photos'). */
    private static final int NUDGE_DP = 20, NUDGE_UP_DP = 6;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final float density;
    private final int screenW;
    private final SkyView sky;
    private final View clock;
    private final View empty;
    private final View[] cards = new View[PRINTS];
    private final ImageView[] photoViews = new ImageView[PRINTS];
    private final TextView[] dateViews = new TextView[PRINTS];
    private ExecutorService loader;
    /** Loader thread only. */
    private final List<File> queue = new ArrayList<>();
    private boolean dealt;
    /** Bumped on stop, so a decode that finishes afterwards is dropped instead of shown. */
    private int generation;

    /** A photo ready for a print. */
    private static final class Print {
        Bitmap bitmap;
        String date;
    }

    private final Runnable next = this::loadNext;

    /** {@code root} is R.layout.prints, already inflated. */
    Prints(Context context, View root) {
        this.context = context;
        density = context.getResources().getDisplayMetrics().density;
        screenW = context.getResources().getDisplayMetrics().widthPixels;
        sky = root.findViewById(R.id.sky);
        clock = root.findViewById(R.id.clockLine);
        empty = root.findViewById(R.id.empty);
        ((TextView) root.findViewById(R.id.emptyPath)).setText(Photos.folder(context).getPath());
        ViewGroup stage = root.findViewById(R.id.stage);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < PRINTS; i++) {
            cards[i] = inflater.inflate(R.layout.print, stage, false);
            // Drawn into a layer with a clear margin, the tilted print is filtered as one image: smooth edges.
            cards[i].setLayerType(View.LAYER_TYPE_HARDWARE, null);
            cards[i].setBackground(shadow(context, density));
            cards[i].findViewById(R.id.printCard).setClipToOutline(true);   // the photo takes the rounded corners
            photoViews[i] = cards[i].findViewById(R.id.printPhoto);
            dateViews[i] = cards[i].findViewById(R.id.printDate);
            cards[i].setTranslationX(screenW * 2f);   // off screen until the first deal
            stage.addView(cards[i]);
        }
        cards[1].bringToFront();   // the middle print lies on top of its neighbours
    }

    void start() {
        if (loader == null) loader = Executors.newSingleThreadExecutor();
        handler.post(next);
    }

    void stop() {
        generation++;
        handler.removeCallbacksAndMessages(null);
    }

    /** stop(), and the photos' memory freed now; the scene is not started again after this. */
    void release() {
        stop();
        if (loader != null) loader.shutdownNow();
        loader = null;
        for (ImageView v : photoViews) release(v);
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
                handler.postDelayed(next, SHOW_MS);
            });
        });
    }

    /**
     * Loader thread only (it owns the queue): the next photo in a shuffled round, decoded near its print size. A
     * photo already in this set ({@code used}) is not dealt twice, so with fewer photos than prints some stay empty.
     */
    private Print nextPrint(Set<File> used) {
        for (int tries = 0; tries < 4; tries++) {
            if (queue.isEmpty()) {
                queue.addAll(Photos.list(context));
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
            p.date = Photos.dateOf(file);
            return p;
        }
        return null;
    }

    /** Slides the prints on screen off to the left, one after another, then deals the new ones in from the right. */
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
        nudgeClock();
        sky.update();   // a new colour at most once a minute; the sweep comes round every 15 seconds
    }

    /** The top of the clock line, px, less how far nudgeClock() can lift it. */
    private int stripTop() {
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        if (clock.getHeight() == 0) return screenH - Math.round(120 * density);
        return clock.getTop() - Math.round(NUDGE_UP_DP * density);
    }

    /**
     * Lays the set out in three equal columns above the clock line: each print is as large as its column allows,
     * tilted as far as it may be, and sits in the middle of it with a little play.
     */
    private void deal(Print[] batch) {
        float side = SIDE_DP * density, jitter = JITTER_DP * density, jitterY = JITTER_Y_DP * density;
        float colGap = COLUMN_GAP_DP * density;
        int shadowRoom = context.getResources().getDimensionPixelSize(R.dimen.print_shadow_room);
        float areaTop = TOP_DP * density, areaBottom = stripTop() - STRIP_GAP_DP * density;
        float colW = (screenW - 2 * side - (PRINTS - 1) * colGap) / PRINTS;
        // The box a tilted print must fit, leaving room for the play either way.
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
            // A photo h tall and a*h wide, tilted, spans a*h*cos + h*sin across and a*h*sin + h*cos down: the
            // largest h that fits both ways.
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
            // The caption's fade spans the photo: set outright, since a FrameLayout with one match_parent child
            // keeps that child's width from the last measure, the previous photo's.
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
        clock.bringToFront();
    }

    /** A soft halo all round each print, a little deeper below, the same under every print. */
    private static PrintShadow shadow(Context context, float density) {
        float inset = context.getResources().getDimensionPixelSize(R.dimen.print_shadow_room);
        return new PrintShadow(inset, 16 * density, 22 * density, 4 * density, 0x73000000);
    }

    private void nudgeClock() {
        float d = density * NUDGE_DP;
        clock.animate().translationX((random.nextFloat() * 2 - 1) * d)
                .translationY(random.nextFloat() * -NUDGE_UP_DP * density).setStartDelay(0).setDuration(1500).start();
    }

    /** Drops the view's photo and frees its pixels now rather than at the next GC. */
    private static void release(ImageView view) {
        Drawable d = view.getDrawable();
        view.setImageDrawable(null);
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null && !b.isRecycled()) b.recycle();
        }
    }
}
