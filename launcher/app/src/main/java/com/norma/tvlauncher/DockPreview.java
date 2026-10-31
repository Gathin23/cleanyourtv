package com.norma.tvlauncher;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads preview cards for a dock app:
 *  - Home slot (com.norma.tvlauncher): the last 5 recently-opened apps, shown as icon tiles.
 *  - Live TV (com.tcl.tv): current/upcoming programs from TvContract.Programs.
 *  - Streaming apps (Netflix, Apple TV+, etc.): from TvContract.PreviewPrograms.
 *
 * All work is done on a background thread (call load() off the main thread).
 * Returns at most MAX_CARDS cards, or an empty list when nothing is available — never throws.
 */
final class DockPreview {
    static final String TAG = "DockPreview";
    static final int MAX_CARDS = 5;

    // Poster thumbnail target size (decoded on a worker thread, bounded to keep RAM low).
    static final int POSTER_W_DP = 120;
    static final int POSTER_H_DP = 180;

    private DockPreview() {}

    /** One preview card. posterBitmap may be null (show a gradient placeholder). */
    static final class Item {
        final String title;
        /** Null → show accent-coloured gradient placeholder. */
        final Bitmap posterBitmap;
        /** ARGB accent used for the placeholder gradient and the bottom title bar. */
        final int accent;

        Item(String title, Bitmap posterBitmap, int accent) {
            this.title = title;
            this.posterBitmap = posterBitmap;
            this.accent = accent;
        }
    }

    /**
     * Load preview items for the given package name.
     * Must be called off the main thread. Returns an empty list on any failure.
     */
    static List<Item> load(Context ctx, String pkg, float density) {
        try {
            if (ctx.getPackageName().equals(pkg)) {
                return loadHomeItems(ctx, density);
            } else if ("com.tcl.tv".equals(pkg)) {
                return loadLiveTvItems(ctx, density);
            } else {
                return loadPreviewProgramItems(ctx, pkg, density);
            }
        } catch (Exception e) {
            Log.w(TAG, "load(" + pkg + "): " + e);
            return new ArrayList<>();
        }
    }

    // ── Home slot: last-opened app icons ──────────────────────────────────────

    private static List<Item> loadHomeItems(Context ctx, float density) {
        List<String> recent = LastOpened.recent(ctx, MAX_CARDS);
        PackageManager pm = ctx.getPackageManager();
        if (recent.isEmpty()) {
            // First run fallback: pick installed leanback apps
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
            for (ResolveInfo ri : resolved) {
                if (recent.size() >= MAX_CARDS) break;
                String p = ri.activityInfo.packageName;
                if (!ctx.getPackageName().equals(p) && !recent.contains(p)) {
                    recent.add(p);
                }
            }
        }
        List<Item> out = new ArrayList<>();
        int sz = dp(64, density);
        for (String pkg : recent) {
            try {
                Drawable icon = pm.getApplicationIcon(pkg);
                String label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
                Bitmap bmp = drawableToBitmap(icon, sz, sz);
                // Accent: sample from the icon using HeroArt's technique (shrink + average).
                int accent = sampleAccent(icon);
                out.add(new Item(label, bmp, accent));
            } catch (Exception e) {
                Log.v(TAG, "home icon for " + pkg + ": " + e);
            }
        }
        return out;
    }

    // ── Live TV: current programs per channel ─────────────────────────────────

    private static List<Item> loadLiveTvItems(Context ctx, float density) {
        List<Item> out = new ArrayList<>();
        ContentResolver cr = ctx.getContentResolver();
        long now = System.currentTimeMillis();

        // Query channels first (up to MAX_CARDS).
        List<Long> channelIds = new ArrayList<>();
        List<String> channelNames = new ArrayList<>();
        try (Cursor c = cr.query(TvContract.Channels.CONTENT_URI,
                new String[]{TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (c != null) {
                while (c.moveToNext() && channelIds.size() < MAX_CARDS) {
                    channelIds.add(c.getLong(0));
                    channelNames.add(c.getString(1));
                }
            }
        } catch (Exception e) {
            Log.v(TAG, "channels query: " + e);
        }

        int posterW = dp(POSTER_W_DP, density);
        int posterH = dp(POSTER_H_DP, density);

        for (int i = 0; i < channelIds.size(); i++) {
            long channelId = channelIds.get(i);
            String channelName = channelNames.get(i);
            // Current or next program on this channel.
            String programTitle = null;
            Uri channelProgramsUri = TvContract.buildProgramsUriForChannel(channelId, now, now + 3600_000L);
            try (Cursor c = cr.query(channelProgramsUri,
                    new String[]{TvContract.Programs.COLUMN_TITLE},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) programTitle = c.getString(0);
            } catch (Exception e) {
                Log.v(TAG, "programs query ch " + channelId + ": " + e);
            }
            String title = programTitle != null && !programTitle.isEmpty()
                    ? programTitle + "\n" + channelName
                    : channelName;
            // TV accent: warm red-orange like broadcast
            int accent = 0xFF_C0392B;
            Bitmap placeholder = makePlaceholder(posterW, posterH, accent, channelName);
            out.add(new Item(title, placeholder, accent));
        }
        return out;
    }

    // ── Streaming apps: TvContract.PreviewPrograms ────────────────────────────

    private static List<Item> loadPreviewProgramItems(Context ctx, String pkg, float density) {
        // API 26+ only; API 28 here so always available.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return new ArrayList<>();
        List<Item> out = new ArrayList<>();
        ContentResolver cr = ctx.getContentResolver();

        // Find channels owned by this package.
        List<Long> channelIds = new ArrayList<>();
        try (Cursor c = cr.query(TvContract.Channels.CONTENT_URI,
                new String[]{TvContract.Channels._ID},
                TvContract.Channels.COLUMN_PACKAGE_NAME + "=?",
                new String[]{pkg}, null)) {
            if (c != null) while (c.moveToNext()) channelIds.add(c.getLong(0));
        } catch (Exception e) {
            Log.v(TAG, "preview channels for " + pkg + ": " + e);
        }

        if (channelIds.isEmpty()) {
            // Fallback: query preview programs directly by package (works on some firmwares).
            return loadPreviewProgramsByPackage(ctx, pkg, density);
        }

        int posterW = dp(POSTER_W_DP, density);
        int posterH = dp(POSTER_H_DP, density);
        int accent = accentForApp(pkg);

        for (long channelId : channelIds) {
            if (out.size() >= MAX_CARDS) break;
            Uri uri = TvContract.buildPreviewProgramsUriForChannel(channelId);
            try (Cursor c = cr.query(uri,
                    new String[]{TvContract.PreviewPrograms.COLUMN_TITLE,
                            TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI},
                    null, null, null)) {
                if (c == null) continue;
                while (c.moveToNext() && out.size() < MAX_CARDS) {
                    String title = c.getString(0);
                    String artUri = c.getString(1);
                    Bitmap poster = loadUriOrNull(cr, artUri, posterW, posterH);
                    if (poster == null) poster = makePlaceholder(posterW, posterH, accent, title);
                    out.add(new Item(title != null ? title : "", poster, accent));
                }
            } catch (Exception e) {
                Log.v(TAG, "preview programs ch " + channelId + ": " + e);
            }
        }
        return out;
    }

    private static List<Item> loadPreviewProgramsByPackage(Context ctx, String pkg, float density) {
        List<Item> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return out;
        ContentResolver cr = ctx.getContentResolver();
        int posterW = dp(POSTER_W_DP, density);
        int posterH = dp(POSTER_H_DP, density);
        int accent = accentForApp(pkg);
        try (Cursor c = cr.query(TvContract.PreviewPrograms.CONTENT_URI,
                new String[]{TvContract.PreviewPrograms.COLUMN_TITLE,
                        TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI},
                TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME + "=?",
                new String[]{pkg}, null)) {
            if (c == null) return out;
            while (c.moveToNext() && out.size() < MAX_CARDS) {
                String title = c.getString(0);
                String artUri = c.getString(1);
                Bitmap poster = loadUriOrNull(cr, artUri, posterW, posterH);
                if (poster == null) poster = makePlaceholder(posterW, posterH, accent, title);
                out.add(new Item(title != null ? title : "", poster, accent));
            }
        } catch (Exception e) {
            Log.v(TAG, "preview programs fallback " + pkg + ": " + e);
        }
        // If still empty, return 5 gradient placeholders so the panel is never blank for known apps.
        if (out.isEmpty()) {
            for (int i = 0; i < MAX_CARDS; i++) {
                out.add(new Item("", makePlaceholder(posterW, posterH, accent, ""), accent));
            }
        }
        return out;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** App-brand accent colours for the gradient placeholder. */
    private static int accentForApp(String pkg) {
        if (pkg == null) return 0xFF_2D2D2D;
        switch (pkg) {
            case "com.netflix.ninja":                   return 0xFF_E50914; // Netflix red
            case "com.apple.atve.androidtv.appletv":   return 0xFF_0071E3; // Apple blue
            case "com.amazon.amazonvideo.livingroom":   return 0xFF_00A8E0; // Prime teal-blue
            case "com.google.android.youtube.tv":      return 0xFF_FF0000; // YouTube red
            case "in.startv.hotstar.dplus.tv":         return 0xFF_1F80E0; // Hotstar blue
            case "com.wbd.stream":                     return 0xFF_0045BA; // MAX navy
            case "com.vidio.android.tv":               return 0xFF_005FCC; // Vidio blue
            default:                                   return 0xFF_2D2D2D;
        }
    }

    /**
     * Decode a URI from the ContentResolver into a bounded bitmap.
     * Returns null on any failure (the caller shows a gradient placeholder instead).
     */
    private static Bitmap loadUriOrNull(ContentResolver cr, String uriStr, int targetW, int targetH) {
        if (uriStr == null || uriStr.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(uriStr);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is = cr.openInputStream(uri)) {
                if (is == null) return null;
                BitmapFactory.decodeStream(is, null, opts);
            }
            opts.inSampleSize = Math.max(1, Math.min(opts.outWidth / targetW, opts.outHeight / targetH));
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565; // 2 bytes/px, saves RAM on a 1 GB TV
            try (InputStream is = cr.openInputStream(uri)) {
                if (is == null) return null;
                Bitmap raw = BitmapFactory.decodeStream(is, null, opts);
                if (raw == null) return null;
                return Bitmap.createScaledBitmap(raw, targetW, targetH, true);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** A gradient placeholder rectangle with the accent colour and an optional short label. */
    static Bitmap makePlaceholder(int w, int h, int accent, String label) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Dark gradient: very dark at top, slightly lighter at bottom using the accent tint.
        int dark = blendColour(accent, 0xFF_000000, 0.82f);
        int mid  = blendColour(accent, 0xFF_000000, 0.60f);
        p.setShader(new LinearGradient(0, 0, 0, h, dark, mid, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return bmp;
    }

    /** Render a Drawable into a Bitmap of the given pixel dimensions. */
    private static Bitmap drawableToBitmap(Drawable d, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return bmp;
    }

    /** Sample the dominant colour from a Drawable (shrink + average, like HeroArt). */
    private static int sampleAccent(Drawable d) {
        try {
            Bitmap small = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 8, 8);
            d.draw(new Canvas(small));
            long r = 0, g = 0, b = 0;
            for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
                int px = small.getPixel(x, y);
                r += Color.red(px); g += Color.green(px); b += Color.blue(px);
            }
            small.recycle();
            return Color.rgb((int)(r/64), (int)(g/64), (int)(b/64));
        } catch (Exception e) {
            return 0xFF_2D2D2D;
        }
    }

    /** Linear blend: 0f = full a, 1f = full b. */
    private static int blendColour(int a, int b, float t) {
        int ra = Color.red(a), ga = Color.green(a), ba = Color.blue(a);
        int rb = Color.red(b), gb = Color.green(b), bb = Color.blue(b);
        return Color.rgb(
                Math.round(ra + (rb - ra) * t),
                Math.round(ga + (gb - ga) * t),
                Math.round(ba + (bb - ba) * t));
    }

    private static int dp(int dp, float density) {
        return Math.max(1, Math.round(dp * density));
    }
}
