package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * History of what was watched, built from other apps' media sessions: newest first, one entry per
 * app + title, artwork kept as small JPEGs in the app's files folder.
 */
final class Recents {

    private static final int MAX = 8;
    private static final String SKIP_LIVE_TV = "com.tcl.tv";
    /** Music apps: their sessions (Spotify Connect from the PC, for one) are not "watched" items. */
    private static final java.util.Set<String> MUSIC = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.spotify.tv.android", "com.spotify.music", "com.google.android.youtube.tvmusic",
            "com.google.android.apps.youtube.music", "com.amazon.music.tv", "deezer.android.tv",
            "com.deezer.android.tv", "com.apple.android.music", "com.soundcloud.android", "com.tidal.android.tv"));

    static boolean isMusic(String pkg) {
        return MUSIC.contains(pkg);
    }

    private Recents() {
    }

    static synchronized void record(Context ctx, MediaController mc) {
        String pkg = mc.getPackageName();
        if (pkg.equals(ctx.getPackageName()) || pkg.equals(SKIP_LIVE_TV) || isMusic(pkg)) return;
        MediaMetadata md = mc.getMetadata();
        if (md == null) return;
        CharSequence title = md.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.length() == 0) return;
        CharSequence sub = md.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if (sub == null) sub = md.getText(MediaMetadata.METADATA_KEY_ARTIST);
        long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        // Apps such as YouTube send a thumbnail link or just the video id instead of a bitmap.
        String artUrl = "";
        for (String k : new String[]{MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
            String u = md.getString(k);
            if (u != null && u.startsWith("http")) {
                artUrl = u;
                break;
            }
        }
        String mediaId = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (mediaId == null) mediaId = "";
        PlaybackState ps = mc.getPlaybackState();
        long pos = ps == null ? 0 : ps.getPosition();

        String key = Integer.toHexString((pkg + "|" + title).hashCode());
        Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null && !YOUTUBE.equals(pkg)) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        File artFile = new File(ctx.getFilesDir(), "art2_" + key + ".jpg");
        if (art != null && !artFile.exists()) {
            int w = Math.min(1280, art.getWidth()), h = Math.max(1, w * art.getHeight() / Math.max(1, art.getWidth()));
            try (FileOutputStream out = new FileOutputStream(artFile)) {
                Bitmap.createScaledBitmap(art, w, h, true).compress(Bitmap.CompressFormat.JPEG, 88, out);
            } catch (Exception ignored) {
            }
        }

        try {
            JSONArray old = load(ctx);
            JSONArray next = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("key", key);
            o.put("pkg", pkg);
            o.put("title", title.toString());
            o.put("sub", sub == null ? "" : sub.toString());
            o.put("dur", dur);
            // Keep the furthest known position when a session reports 0 after stopping.
            long keepPos = pos;
            String keepUrl = artUrl, keepId = mediaId;
            for (int i = 0; i < old.length(); i++) {
                JSONObject e = old.getJSONObject(i);
                if (!key.equals(e.optString("key"))) continue;
                if (pos <= 0) keepPos = e.optLong("pos");
                if (keepUrl.isEmpty()) keepUrl = e.optString("artUrl");
                if (keepId.isEmpty()) keepId = e.optString("mediaId");
            }
            o.put("artUrl", keepUrl);
            o.put("mediaId", keepId);
            o.put("pos", keepPos);
            o.put("at", System.currentTimeMillis());
            next.put(o);
            for (int i = 0; i < old.length() && next.length() < MAX; i++) {
                JSONObject e = old.getJSONObject(i);
                if (key.equals(e.optString("key"))) continue;
                next.put(e);
            }
            prefs(ctx).edit().putString("list", next.toString()).apply();
            cleanArt(ctx, next);
            if (YOUTUBE.equals(pkg) && keepId.isEmpty()) {
                findYoutubeId(ctx.getApplicationContext(), key, title.toString(), sub == null ? "" : sub.toString());
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * An item played in the launcher's own player. It has no media session, so the player records it
     * here directly; "play" holds what PlayerActivity needs to resume it.
     */
    static synchronized void recordDirect(Context ctx, String key, String title, String sub, String art,
                                          long pos, long dur, String play) {
        try {
            String k = "p" + Integer.toHexString(key.hashCode());
            JSONArray old = load(ctx), next = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("key", k);
            o.put("pkg", ctx.getPackageName());
            o.put("title", title);
            o.put("sub", sub);
            o.put("dur", dur);
            o.put("pos", pos);
            o.put("at", System.currentTimeMillis());
            o.put("backdrop", art);
            o.put("artUrl", art);
            o.put("play", play);
            next.put(o);
            for (int i = 0; i < old.length() && next.length() < MAX; i++) {
                JSONObject e = old.getJSONObject(i);
                if (k.equals(e.optString("key"))) continue;
                next.put(e);
            }
            prefs(ctx).edit().putString("list", next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Episodes/movies watched to the end ("tt123:2:5" or "tt123"), newest first, for the check mark. */
    static synchronized void markWatched(Context ctx, String vid) {
        if (vid == null || vid.isEmpty()) return;
        try {
            JSONArray old = watched(ctx), next = new JSONArray().put(vid);
            for (int i = 0; i < old.length() && next.length() < 3000; i++) {
                if (!vid.equals(old.optString(i))) next.put(old.optString(i));
            }
            prefs(ctx).edit().putString("watched", next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized void unmarkWatched(Context ctx, String vid) {
        try {
            JSONArray old = watched(ctx), next = new JSONArray();
            for (int i = 0; i < old.length(); i++) if (!vid.equals(old.optString(i))) next.put(old.optString(i));
            prefs(ctx).edit().putString("watched", next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Where each episode/movie stopped ({vid: [pos, dur, at]}), for the bar on tiles; done removes it. */
    static synchronized void saveProgress(Context ctx, String vid, long pos, long dur, boolean done) {
        if (vid == null || vid.isEmpty()) return;
        try {
            JSONObject all = progress(ctx);
            all.remove(vid);
            if (!done) all.put(vid, new JSONArray().put(pos).put(dur).put(System.currentTimeMillis()));
            if (all.length() > 600) {   // drop the oldest
                String oldest = null; long at = Long.MAX_VALUE;
                for (java.util.Iterator<String> it = all.keys(); it.hasNext(); ) {
                    String k = it.next(); long t = all.getJSONArray(k).optLong(2);
                    if (t < at) { at = t; oldest = k; }
                }
                if (oldest != null) all.remove(oldest);
            }
            prefs(ctx).edit().putString("progress", all.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized JSONObject progress(Context ctx) {
        try {
            return new JSONObject(prefs(ctx).getString("progress", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static synchronized JSONArray watched(Context ctx) {
        try {
            return new JSONArray(prefs(ctx).getString("watched", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** The history with each entry's artwork inlined as a data URI ("" when none). */
    static synchronized JSONArray list(Context ctx) {
        JSONArray all = load(ctx), arr = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject e = all.optJSONObject(i);
            if (e == null || isMusic(e.optString("pkg"))) continue;
            // Built-in player entries that were only an add-on notice clip.
            if (e.has("play") && (e.optLong("dur") < 120_000 || e.optString("play").contains("/exceptions/"))) continue;
            arr.put(e);
        }
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject e = arr.getJSONObject(i);
                File f = new File(ctx.getFilesDir(), "art2_" + e.optString("key") + ".jpg");
                e.put("art", f.exists() ? dataUri(f) : "");
                if (!YOUTUBE.equals(e.optString("pkg")) && !e.has("backdrop") && LOOKED_UP.add("bd:" + e.optString("key"))) {
                    findBackdrop(ctx.getApplicationContext(), e.optString("key"), e.optString("title"));
                }
                if (YOUTUBE.equals(e.optString("pkg")) && e.optString("mediaId").isEmpty() && LOOKED_UP.add(e.optString("key"))) {
                    findYoutubeId(ctx.getApplicationContext(), e.optString("key"), e.optString("title"), e.optString("sub"));
                }
            } catch (Exception ignored) {
            }
        }
        return arr;
    }

    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final java.util.Set<String> LOOKED_UP = new java.util.HashSet<>();

    /**
     * YouTube for TV publishes only title and channel, no thumbnail or id. Look the video up on
     * youtube.com once, in the background, and store the id; the page builds the thumbnail URL from it.
     */
    private static void findYoutubeId(Context ctx, String key, String title, String channel) {
        new Thread(() -> {
            try {
                String q = java.net.URLEncoder.encode(title + " " + channel, "UTF-8");
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL("https://www.youtube.com/results?search_query=" + q).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept-Language", "pt-BR,en;q=0.8");
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36");
                StringBuilder sb = new StringBuilder();
                try (java.io.Reader r = new java.io.InputStreamReader(c.getInputStream(), "UTF-8")) {
                    char[] buf = new char[8192];
                    int n;
                    // The first result can sit past 800 KB of page; stop as soon as an id shows up.
                    while ((n = r.read(buf)) > 0 && sb.length() < 3_000_000) {
                        sb.append(buf, 0, n);
                        if (sb.indexOf("\"videoId\":\"", Math.max(0, sb.length() - n - 12)) >= 0) {
                            char[] more = new char[64];
                            int k = r.read(more);
                            if (k > 0) sb.append(more, 0, k);
                            break;
                        }
                    }
                }
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\"videoId\":\"([A-Za-z0-9_-]{11})\"").matcher(sb);
                if (!m.find()) return;
                String id = m.group(1);
                synchronized (Recents.class) {
                    JSONArray arr = load(ctx);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject e = arr.getJSONObject(i);
                        if (key.equals(e.optString("key"))) e.put("mediaId", id);
                    }
                    prefs(ctx).edit().putString("list", arr.toString()).apply();
                }
            } catch (Exception ignored) {
            }
        }, "yt-thumb").start();
    }

    /**
     * Movies and series: look the title up in Stremio's public Cinemeta catalogue and keep its large
     * background image, much sharper than the small frame apps put in their media session.
     * Stores "" when nothing matches, so the lookup runs once per item.
     */
    private static void findBackdrop(Context ctx, String key, String title) {
        new Thread(() -> {
            String url = "";
            try {
                // Drop episode suffixes such as "Show - S1E3" or "Show: Episode 3" before searching.
                String q = title.replaceAll("(?i)\\s*[-:|]\\s*(S\\d+\\s*E\\d+|T\\d+\\s*E\\d+|Ep(is[oó]dio|isode)?\\s*\\d+).*$", "").trim();
                for (String type : new String[]{"movie", "series"}) {
                    String body = get("https://v3-cinemeta.strem.io/catalog/" + type + "/top/search="
                            + java.net.URLEncoder.encode(q, "UTF-8").replace("+", "%20") + ".json");
                    JSONArray metas = new JSONObject(body).optJSONArray("metas");
                    if (metas == null || metas.length() == 0) continue;
                    JSONObject m = metas.getJSONObject(0);
                    String name = m.optString("name");
                    if (!similar(name, q)) continue;
                    String bg = m.optString("background");
                    if (bg.isEmpty()) continue;
                    url = bg.replace("/background/medium/", "/background/large/");
                    break;
                }
            } catch (Exception ignored) {
            }
            synchronized (Recents.class) {
                try {
                    JSONArray arr = load(ctx);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject e = arr.getJSONObject(i);
                        if (key.equals(e.optString("key"))) e.put("backdrop", url);
                    }
                    prefs(ctx).edit().putString("list", arr.toString()).apply();
                } catch (Exception ignored) {
                }
            }
        }, "backdrop").start();
    }

    private static boolean similar(String a, String b) {
        String x = a.toLowerCase().replaceAll("[^a-z0-9]", ""), y = b.toLowerCase().replaceAll("[^a-z0-9]", "");
        return !x.isEmpty() && !y.isEmpty() && (x.startsWith(y) || y.startsWith(x));
    }

    private static String get(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        StringBuilder sb = new StringBuilder();
        try (java.io.Reader r = new java.io.InputStreamReader(c.getInputStream(), "UTF-8")) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0 && sb.length() < 2_000_000) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static JSONArray load(Context ctx) {
        try {
            return new JSONArray(prefs(ctx).getString("list", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("recents", Context.MODE_PRIVATE);
    }

    private static void cleanArt(Context ctx, JSONArray keep) {
        StringBuilder keys = new StringBuilder();
        for (int i = 0; i < keep.length(); i++) keys.append(keep.optJSONObject(i).optString("key")).append(',');
        File[] files = ctx.getFilesDir().listFiles((d, n) -> n.startsWith("art2_"));
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            String k = n.startsWith("art2_") ? n.substring(5, n.length() - 4) : "";
            if (keys.indexOf(k + ",") < 0) f.delete();
        }
    }

    private static String dataUri(File f) throws Exception {
        byte[] buf = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int n = 0;
            while (n < buf.length) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
        }
        return "data:image/jpeg;base64," + Base64.encodeToString(buf, Base64.NO_WRAP);
    }
}
