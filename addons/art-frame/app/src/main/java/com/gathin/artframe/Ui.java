package com.gathin.artframe;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared look for the screens: a dark gallery wall, warm gold focus, large type readable from the sofa. */
final class Ui {
    static final int BG = 0xFF111214;
    static final int PANEL = 0xFF1F2125;
    static final int ACCENT = 0xFFE0B354;
    static final int INK = 0xFF1A1712;
    static final int TEXT = 0xFFEDEAE4;
    static final int MUTED = 0xFF9A968E;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable shape(Context c, int color, float radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeColor != 0) g.setStroke(dp(c, 3), strokeColor);
        return g;
    }

    /** Focused: gold; selected (a chosen source or collection): gold outline; otherwise the panel colour. */
    static StateListDrawable pill(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, shape(c, ACCENT, 26, 0));
        s.addState(new int[]{android.R.attr.state_selected}, shape(c, PANEL, 26, ACCENT));
        s.addState(new int[]{}, shape(c, PANEL, 26, 0));
        return s;
    }

    static ColorStateList pillText() {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{INK, TEXT});
    }

    static TextView button(Context c, CharSequence label, View.OnClickListener click) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTextColor(pillText());
        b.setBackground(pill(c));
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(c, 22), dp(c, 12), dp(c, 22), dp(c, 12));
        b.setFocusable(true);
        b.setClickable(true);
        b.setSingleLine(true);
        if (click != null) b.setOnClickListener(click);
        return b;
    }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    static TextView heading(Context c, CharSequence s) {
        TextView t = text(c, s, 30, TEXT);
        t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        return t;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams gap(Context c, int rightDp, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(c, rightDp), dp(c, bottomDp));
        return p;
    }

    static void screen(Activity a, View content) {
        content.setBackgroundColor(BG);
        a.setContentView(content);
    }

    // --- thumbnails ---------------------------------------------------------------------------------------

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 << 20) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getAllocationByteCount();
        }
    };
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Loads a local thumbnail file or a museum thumbnail URL into the view; stale loads are dropped. */
    static void thumb(ImageView view, String key, File file, String url) {
        view.setTag(key);
        Bitmap hit = CACHE.get(key);
        view.setImageBitmap(hit);
        if (hit != null) return;
        LOADER.execute(() -> {
            Bitmap bm = null;
            try {
                if (file != null) {
                    bm = BitmapFactory.decodeFile(file.getPath());
                } else if (url != null && !url.isEmpty()) {
                    byte[] data = Http.bytes(url, 4L << 20);
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    o.inSampleSize = Images.sampleSize(o.outWidth, o.outHeight, Images.THUMB_W, Images.THUMB_H);
                    o.inJustDecodeBounds = false;
                    bm = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                }
            } catch (Exception ignored) {
                // A missing thumbnail leaves the tile blank.
            }
            Bitmap result = bm;
            MAIN.post(() -> {
                if (result == null) return;
                CACHE.put(key, result);
                if (key.equals(view.getTag())) view.setImageBitmap(result);
            });
        });
    }

    static void forget(String key) {
        CACHE.remove(key);
    }
}
