package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A theme's own font: font.ttf next to config.json (tools/theme.sh pushes it) replaces Outfit in the text the
 * launcher draws, at the weight each view asked for, with the theme's font_features (e.g. "ss08"). A variable
 * font is built once per weight. Without font.ttf, or with one Android cannot read, nothing changes.
 */
final class ThemeFont {
    static final String FILE_NAME = "font.ttf";
    /** A bigger file is refused rather than mapped on a 1 GB TV; Latin variable fonts are well under 1 MB. */
    private static final long MAX_BYTES = 8L << 20;
    private static final String TAG = "TvHome.ThemeFont";

    private static File loaded;
    private static long loadedStamp;
    private static final SparseArray<Typeface> byWeight = new SparseArray<>();
    /** The bundled UI typefaces by the weight they stand for: their own getWeight() reports Outfit's 100. */
    private static final Map<Typeface, Integer> uiWeights = new HashMap<>();
    private static String features = "";

    private ThemeFont() {
    }

    /** Call before setContentView: text views this activity inflates take the theme font. */
    static void install(Activity activity, Config cfg) {
        install(activity, activity.getLayoutInflater(), cfg);
    }

    /** The same for a window that is not an activity's, such as the screensaver's. */
    static void install(Context ctx, LayoutInflater inflater, Config cfg) {
        if (!load(ctx, cfg)) return;
        if (inflater.getFactory2() != null) return;
        inflater.setFactory2(new LayoutInflater.Factory2() {
            @Override
            public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
                return onCreateView(name, context, attrs);
            }

            @Override
            public View onCreateView(String name, Context context, AttributeSet attrs) {
                View v = create(name, context, attrs);
                if (v instanceof TextView) style((TextView) v);
                return v;
            }
        });
    }

    /** Applies the theme font to a text view built in code; no-op without one. */
    static void style(TextView t) {
        if (loaded == null) return;
        Typeface tf = typeface(weightOf(t.getTypeface()));
        if (tf != null) t.setTypeface(tf);
        if (!features.isEmpty()) t.setFontFeatureSettings(features);
    }

    /** Exactly what inflation would make for these tags; null lets the inflater carry on for the rest. */
    private static View create(String name, Context context, AttributeSet attrs) {
        if ("TextView".equals(name)) return new TextView(context, attrs);
        if ("TextClock".equals(name)) return new TextClock(context, attrs);
        if ("Button".equals(name)) return new Button(context, attrs);
        return null;
    }

    private static int weightOf(Typeface current) {
        if (current == null) return 400;
        Integer ui = uiWeights.get(current);
        if (ui != null) return ui;
        return Build.VERSION.SDK_INT >= 28 ? weight(current.getWeight()) : 400;
    }

    private static boolean load(Context ctx, Config cfg) {
        File dir = Config.dir(ctx);
        File file = dir == null ? null : new File(dir, FILE_NAME);
        if (file == null || !file.isFile()) return drop();
        if (file.length() > MAX_BYTES) {
            Log.w(TAG, FILE_NAME + " is over " + (MAX_BYTES >> 20) + " MB; keeping the bundled font");
            return drop();
        }
        long stamp = file.length() ^ file.lastModified();
        if (!file.equals(loaded) || stamp != loadedStamp) {
            byWeight.clear();
            loaded = file;
            loadedStamp = stamp;
        }
        if (uiWeights.isEmpty()) {
            uiWeights.put(UiFont.get(ctx, R.string.font_body), 400);
            uiWeights.put(UiFont.get(ctx, R.string.font_regular), 400);
            uiWeights.put(UiFont.get(ctx, R.string.font_light), 300);
            uiWeights.put(UiFont.get(ctx, R.string.font_extralight), 200);
            uiWeights.put(UiFont.get(ctx, R.string.font_medium), 500);
        }
        features = features(cfg.fontFeatures);
        if (typeface(400) != null) return true;
        return drop();
    }

    /** Back to the bundled font, letting go of a font that was loaded before. */
    private static boolean drop() {
        loaded = null;
        byWeight.clear();
        features = "";
        return false;
    }

    private static Typeface typeface(int weight) {
        Typeface tf = byWeight.get(weight);
        if (tf != null || loaded == null) return tf;
        try {
            // null when the file is not a font Android can read
            tf = new Typeface.Builder(loaded).setWeight(weight).setFontVariationSettings("'wght' " + weight).build();
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot read " + loaded.getName() + ": " + e);
            tf = null;
        }
        if (tf != null) byWeight.put(weight, tf);
        return tf;
    }

    /** The nearest hundred from 100 to 900, the weights the font is built at. */
    static int weight(int w) {
        if (w <= 0) return 400;
        return Math.max(100, Math.min(900, Math.round(w / 100f) * 100));
    }

    /** "ss08, cv05" as Android's font feature settings ("'ss08', 'cv05'"); anything but 4-character tags is refused. */
    static String features(String spec) {
        if (spec == null || spec.trim().isEmpty()) return "";
        List<String> tags = new ArrayList<>();
        for (String part : spec.split(",")) {
            String tag = part.trim();
            if (!tag.matches("[A-Za-z0-9]{4}")) return "";
            tags.add("'" + tag + "'");
        }
        return String.join(", ", tags);
    }
}
