package io.github.cleanyourtv.topicphotos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Freely licensed photos from Wikimedia Commons, found by the owner's topics and kept on the TV: files/photos/
 * holds the JPEGs and index.json their credit and details. The screen's resolution, read from the device, goes into
 * the search (no smaller file comes back) and into the download (Commons scales it to the screen's width). A set is
 * fetched again when the topics, the time of day or the screen change, or after {@link #MAX_AGE_MS}; until then,
 * and while a fetch fails (no network), the photos on disk are used. Place names come from the photo's coordinates through
 * OpenStreetMap, asked once per photo when it first comes on screen.
 */
final class Photos {

    static final class Photo {
        String id = "", file = "", title = "", description = "", place = "", author = "", license = "", taken = "";
        int width, height;
        /** NaN when the photo has no coordinates. */
        double lat = Double.NaN, lon = Double.NaN;
        /** The place was asked for already (or there are no coordinates to ask with). */
        boolean placed;

        JSONObject toJson() throws org.json.JSONException {
            return new JSONObject().put("id", id).put("file", file).put("title", title).put("description", description)
                    .put("place", place).put("author", author).put("license", license).put("taken", taken)
                    .put("width", width).put("height", height).put("placed", placed)
                    .put("lat", Double.isNaN(lat) ? JSONObject.NULL : lat)
                    .put("lon", Double.isNaN(lon) ? JSONObject.NULL : lon);
        }

        static Photo fromJson(JSONObject o) {
            Photo p = new Photo();
            p.id = o.optString("id");
            p.file = o.optString("file");
            p.title = o.optString("title");
            p.description = o.optString("description");
            p.place = o.optString("place");
            p.author = o.optString("author");
            p.license = o.optString("license");
            p.taken = o.optString("taken");
            p.width = o.optInt("width");
            p.height = o.optInt("height");
            p.placed = o.optBoolean("placed");
            p.lat = o.optDouble("lat", Double.NaN);
            p.lon = o.optDouble("lon", Double.NaN);
            return p;
        }
    }

    static final long MAX_AGE_MS = 3 * 24 * 3600_000L;
    static final int COUNT = 30;
    private static final String COMMONS = "https://commons.wikimedia.org/w/api.php";
    private static final String NOMINATIM = "https://nominatim.openstreetmap.org/reverse";
    /** Wikimedia and OpenStreetMap ask every client to name itself. */
    private static final String AGENT = "TopicPhotos/1.0 (Android TV add-on for Clean Your TV)";
    private static final String TAG = "TopicPhotos";
    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private Photos() {
    }

    private static File dir(Context c) {
        return new File(c.getFilesDir(), "photos");
    }

    /** The saved photos whose files are on disk, in the order they were found. */
    static List<Photo> load(Context c) {
        List<Photo> out = new ArrayList<>();
        JSONArray arr = readIndex(c).optJSONArray("photos");
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Photo p = Photo.fromJson(o);
            if (file(c, p).isFile()) out.add(p);
        }
        return out;
    }

    static File file(Context c, Photo p) {
        return new File(dir(c), p.file);
    }

    /** The screen in pixels, landscape. */
    static int[] screen(Context c) {
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return new int[]{Math.max(dm.widthPixels, dm.heightPixels), Math.min(dm.widthPixels, dm.heightPixels)};
    }

    /** Commons serves set widths only: the smallest of them that still covers the screen. */
    static int commonsWidth(int screenWidth) {
        return screenWidth <= 1280 ? 1280 : screenWidth <= 1920 ? 1920 : 3840;
    }

    /** The search terms for this moment: each topic, with "daylight" or "night" added when that is on. */
    static List<String> queries(List<String> topics, String daypart) {
        List<String> out = new ArrayList<>();
        for (String t : topics) out.add(daypart.isEmpty() ? t : t + ("day".equals(daypart) ? " daylight" : " night"));
        return out;
    }

    /** Whether a Commons file can be a full-screen photo: a landscape JPEG at least the screen's size. */
    static boolean fits(String mime, int w, int h, int screenW, int screenH) {
        return "image/jpeg".equals(mime) && w >= screenW && h >= screenH && w >= h * 1.3 && w <= h * 2.4;
    }

    /**
     * Fetches the set again in the background when it is missing, stale or was made for other topics, then runs
     * {@code done} on the main thread once new photos are saved. Does nothing while a fetch runs; with no network the
     * fetch fails, is logged, and is tried again the next time.
     */
    static void refresh(Context c, Runnable done) {
        Context app = c.getApplicationContext();
        List<String> queries = queries(Settings.topics(app), Settings.daypart(app));
        int[] screen = screen(app);
        String terms = screen[0] + "x" + screen[1] + "|" + String.join("|", queries);
        JSONObject index = readIndex(app);
        boolean fresh = terms.equals(index.optString("terms"))
                && System.currentTimeMillis() - index.optLong("fetched") < MAX_AGE_MS && !load(app).isEmpty();
        if (fresh || !RUNNING.compareAndSet(false, true)) return;
        NET.execute(() -> {
            try {
                if (fetch(app, queries, terms, screen) && done != null) new Handler(Looper.getMainLooper()).post(done);
            } catch (Exception e) {
                Log.w(TAG, "fetch: " + e);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    /**
     * One search per topic with the screen's size in it, at most three photos by one author per topic so a single
     * uploader's series does not fill the set, then the topics taken in turns.
     */
    private static boolean fetch(Context c, List<String> queries, String terms, int[] screen) throws Exception {
        int width = commonsWidth(screen[0]);
        List<List<Photo>> lists = new ArrayList<>();
        List<List<String>> urlLists = new ArrayList<>();
        for (String q : queries) {
            JSONObject res = new JSONObject(get(COMMONS + "?action=query&format=json&generator=search"
                    + "&gsrnamespace=6&gsrlimit=40&prop=imageinfo&iiprop=url|size|mime|extmetadata&iiurlwidth=" + width
                    + "&iiextmetadatafilter=ImageDescription|Artist|LicenseShortName|DateTimeOriginal|GPSLatitude|GPSLongitude"
                    + "&gsrsearch=" + URLEncoder.encode(q + " filetype:bitmap filew:>" + (screen[0] - 1)
                    + " fileh:>" + (screen[1] - 1), "UTF-8")));
            JSONObject query = res.optJSONObject("query");
            JSONObject pages = query == null ? null : query.optJSONObject("pages");
            List<JSONObject> found = new ArrayList<>();
            if (pages != null) for (Iterator<String> it = pages.keys(); it.hasNext(); ) found.add(pages.getJSONObject(it.next()));
            found.sort((a, b) -> Integer.compare(a.optInt("index"), b.optInt("index")));   // the search's own ranking
            List<Photo> list = new ArrayList<>();
            List<String> urls = new ArrayList<>();
            Map<String, Integer> perAuthor = new HashMap<>();
            for (JSONObject page : found) {
                JSONArray infos = page.optJSONArray("imageinfo");
                JSONObject ii = infos == null ? null : infos.optJSONObject(0);
                if (ii == null || ii.optString("thumburl").isEmpty()) continue;
                int w = ii.optInt("width"), h = ii.optInt("height");
                if (!fits(ii.optString("mime"), w, h, screen[0], screen[1])) continue;
                JSONObject m = ii.optJSONObject("extmetadata");
                Photo p = new Photo();
                p.id = "c" + page.optLong("pageid");
                p.file = p.id + ".jpg";
                p.author = meta(m, "Artist");
                Integer n = perAuthor.get(p.author);
                if (n != null && n >= 3) continue;
                perAuthor.put(p.author, n == null ? 1 : n + 1);
                p.title = page.optString("title").replaceFirst("^File:", "").replaceFirst("\\.[A-Za-z]+$", "");
                p.description = clip(meta(m, "ImageDescription"), 200);
                p.license = meta(m, "LicenseShortName");
                String taken = meta(m, "DateTimeOriginal");
                p.taken = taken.matches("\\d{4}[-:]\\d{2}[-:]\\d{2}.*") ? taken.substring(0, 10).replace(':', '-') : "";
                p.width = w;
                p.height = h;
                try {
                    p.lat = Double.parseDouble(meta(m, "GPSLatitude"));
                    p.lon = Double.parseDouble(meta(m, "GPSLongitude"));
                } catch (NumberFormatException e) {
                    p.lat = p.lon = Double.NaN;
                }
                p.placed = Double.isNaN(p.lat);
                list.add(p);
                urls.add(ii.optString("thumburl"));
            }
            lists.add(list);
            urlLists.add(urls);
        }
        File dir = dir(c);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no folder " + dir);
        List<Photo> old = load(c);
        JSONArray out = new JSONArray();
        Set<String> keep = new HashSet<>(), seen = new HashSet<>();
        for (int round = 0; round < 40 && out.length() < COUNT; round++) {
            boolean any = false;
            for (int i = 0; i < lists.size() && out.length() < COUNT; i++) {
                if (round >= lists.get(i).size()) continue;
                any = true;
                Photo p = lists.get(i).get(round);
                if (!seen.add(p.id)) continue;
                File f = new File(dir, p.file);
                if (!f.isFile()) {
                    try {
                        download(urlLists.get(i).get(round), f);
                    } catch (IOException e) {
                        Log.w(TAG, "download " + p.id + ": " + e);
                        continue;
                    }
                }
                for (Photo before : old) if (before.id.equals(p.id) && before.placed) p = before;
                keep.add(p.file);
                out.put(p.toJson());
            }
            if (!any) break;
        }
        if (out.length() == 0) return false;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.getName().endsWith(".jpg") && !keep.contains(f.getName()) && !f.delete()) Log.w(TAG, "kept " + f);
        }
        writeIndex(c, new JSONObject().put("terms", terms).put("fetched", System.currentTimeMillis()).put("photos", out));
        return true;
    }

    /**
     * The place a photo was taken, from its coordinates through OpenStreetMap's reverse lookup, in the TV's language.
     * One request per photo, kept in the index. Blocking: call it off the main thread.
     */
    static Photo place(Context c, Photo p) {
        if (p.placed) return p;
        try {
            String lang = c.getResources().getConfiguration().getLocales().get(0).toLanguageTag();
            JSONObject o = new JSONObject(get(NOMINATIM + "?format=jsonv2&zoom=10&lat=" + p.lat + "&lon=" + p.lon
                    + "&accept-language=" + URLEncoder.encode(lang, "UTF-8")));
            JSONObject a = o.optJSONObject("address");
            List<String> parts = new ArrayList<>();
            if (a != null) {
                for (String k : new String[]{"city", "town", "village", "municipality", "county", "state", "country"}) {
                    String v = a.optString(k).trim();
                    if (!v.isEmpty() && !parts.contains(v) && parts.size() < 3) parts.add(v);
                }
            }
            p.place = String.join(", ", parts);
            p.placed = true;
            mergePlace(c, p.id, p.place);
        } catch (Exception e) {
            Log.w(TAG, "place " + p.id + ": " + e);
        }
        return p;
    }

    /** A text field of Commons' extmetadata without its HTML, or "". */
    private static String meta(JSONObject m, String key) {
        JSONObject f = m == null ? null : m.optJSONObject(key);
        String v = f == null ? "" : f.optString("value");
        return android.text.Html.fromHtml(v, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                .replaceAll("\\s+", " ").trim();
    }

    static String clip(String s, int max) {
        String t = s.trim();
        if (t.isEmpty()) return "";
        t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setConnectTimeout(15_000);
        http.setReadTimeout(20_000);
        http.setRequestProperty("User-Agent", AGENT);
        try {
            if (http.getResponseCode() != 200) throw new IOException("HTTP " + http.getResponseCode());
            try (InputStream in = http.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    if (out.size() > 4 * 1024 * 1024) throw new IOException("response too large");
                }
                return out.toString("UTF-8");
            }
        } finally {
            http.disconnect();
        }
    }

    private static void download(String url, File to) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setConnectTimeout(15_000);
        http.setReadTimeout(30_000);
        http.setRequestProperty("User-Agent", AGENT);
        File part = new File(to.getPath() + ".part");
        try {
            if (http.getResponseCode() != 200) throw new IOException("HTTP " + http.getResponseCode());
            try (InputStream in = http.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16384];
                long total = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > 8L * 1024 * 1024) throw new IOException("over 8 MB");
                    out.write(buf, 0, n);
                }
            }
            if (!part.renameTo(to)) throw new IOException("rename " + part);
        } finally {
            http.disconnect();
            if (part.exists() && !part.delete()) Log.w(TAG, "left " + part);
        }
    }

    /**
     * Writes a looked-up place into the index: read, change only that photo's place, write, all under writeIndex's
     * lock, so a refresh published meanwhile is not overwritten with an older index. A photo no longer in it is
     * left out. The lookup itself stays outside the lock.
     */
    static void mergePlace(Context c, String id, String place) throws Exception {
        synchronized (Photos.class) {
            JSONObject index = readIndex(c);
            JSONArray arr = index.optJSONArray("photos");
            boolean found = false;
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (id.equals(o.optString("id"))) {
                    o.put("place", place).put("placed", true);
                    found = true;
                }
            }
            if (found) writeIndex(c, index);
        }
    }

    private static JSONObject readIndex(Context c) {
        try {
            File f = new File(dir(c), "index.json");
            return new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static synchronized void writeIndex(Context c, JSONObject index) throws IOException {
        File dir = dir(c), tmp = new File(dir, "index.json.tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(index.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(new File(dir, "index.json"))) throw new IOException("rename index");
    }
}
