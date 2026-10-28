package com.gathin.artframe;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

/** One saved piece: its picture file, where it came from, and how the owner wants it shown. */
final class Artwork {
    static final int ADJUST_MIN = -50;
    static final int ADJUST_MAX = 50;
    /** Matte values; "auto" follows the global setting. */
    /** A piece's matte colour: "auto" follows Settings, "none" shows no matte. */
    static final String[] MATTES = {"auto", "none", "white", "cream", "light-grey", "warm-grey", "charcoal", "black",
            "navy", "forest", "burgundy"};
    static final String[] SIZES = Matte.withAuto(Matte.SIZES);
    static final String[] TEXTURES = Matte.withAuto(Matte.TEXTURES);
    static final String[] DEPTHS = Matte.withAuto(Matte.DEPTHS);

    String id = "";
    String file = "";
    String title = "";
    String artist = "";
    String date = "";
    /** starter, upload, aic, cma or met. */
    String source = "";
    String url = "";
    String credit = "";
    int width;
    int height;
    int brightness;
    int contrast;
    String matte = "auto";
    String matteSize = "auto";
    String matteTexture = "auto";
    String depth = "auto";
    /** Fill the screen, cropping the picture to the screen's shape, instead of fitting it inside a matte. */
    boolean fill;
    /** With a matte: show the whole picture (the matte wider where needed) instead of cropping it to the opening. */
    boolean whole;
    /** With fill: where the crop sits along the picture's longer side, 0 (left/top) to 1 (right/bottom). */
    float focus = 0.5f;
    final Set<String> collections = new LinkedHashSet<>();
    long added;

    String byline() {
        if (artist.isEmpty()) return date;
        return date.isEmpty() ? artist : artist + ", " + date;
    }

    static int clampAdjust(int v) {
        return Math.max(ADJUST_MIN, Math.min(ADJUST_MAX, v));
    }

    static float clampFocus(float f) {
        return Float.isNaN(f) ? 0.5f : Math.max(0f, Math.min(1f, f));
    }

    /**
     * The part of a w x h picture shown when it fills a screenW x screenH screen: the screen's shape, as large as
     * fits, slid along the cropped side by focus. Returns {left, top, right, bottom} in picture pixels.
     */
    static int[] fillCrop(int w, int h, int screenW, int screenH, float focus) {
        float target = screenW / (float) screenH;
        if (w / (float) h > target) {
            int cw = Math.max(1, Math.round(h * target));
            int left = Math.round((w - cw) * clampFocus(focus));
            return new int[]{left, 0, left + cw, h};
        }
        int ch = Math.max(1, Math.round(w / target));
        int top = Math.round((h - ch) * clampFocus(focus));
        return new int[]{0, top, w, top + ch};
    }

    static String nextMatte(String current, int step) {
        int i = 0;
        for (int k = 0; k < MATTES.length; k++) if (MATTES[k].equals(current)) i = k;
        return MATTES[Math.floorMod(i + step, MATTES.length)];
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id).put("file", file).put("title", title).put("artist", artist).put("date", date)
                .put("source", source).put("url", url).put("credit", credit)
                .put("width", width).put("height", height)
                .put("brightness", brightness).put("contrast", contrast).put("matte", matte).put("matte_size", matteSize).put("matte_texture", matteTexture).put("depth", depth).put("fill", fill).put("whole", whole).put("focus", (double) focus)
                .put("added", added).put("collections", new JSONArray(collections));
        return o;
    }

    static Artwork fromJson(JSONObject o) {
        Artwork a = new Artwork();
        a.id = o.optString("id");
        a.file = o.optString("file");
        a.title = o.optString("title");
        a.artist = o.optString("artist");
        a.date = o.optString("date");
        a.source = o.optString("source");
        a.url = o.optString("url");
        a.credit = o.optString("credit");
        a.width = o.optInt("width");
        a.height = o.optInt("height");
        a.brightness = clampAdjust(o.optInt("brightness"));
        a.contrast = clampAdjust(o.optInt("contrast"));
        a.matte = o.optString("matte", "auto");
        boolean known = false;
        for (String m : MATTES) known |= m.equals(a.matte);
        if (!known) a.matte = "auto";
        a.matteSize = o.optString("matte_size", "auto");
        if (!Matte.known(SIZES, a.matteSize)) a.matteSize = "auto";
        a.matteTexture = o.optString("matte_texture", "auto");
        if (!Matte.known(TEXTURES, a.matteTexture)) a.matteTexture = "auto";
        a.depth = o.optString("depth", "auto");
        if (!Matte.known(DEPTHS, a.depth)) a.depth = "auto";
        a.added = o.optLong("added");
        a.fill = o.optBoolean("fill");
        a.whole = o.optBoolean("whole");
        a.focus = clampFocus((float) o.optDouble("focus", 0.5));
        JSONArray c = o.optJSONArray("collections");
        if (c != null) for (int i = 0; i < c.length(); i++) {
            String name = c.optString(i).trim();
            if (!name.isEmpty()) a.collections.add(name);
        }
        return a;
    }
}
