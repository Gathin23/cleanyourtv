package com.gathin.artframe;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The saved art, in the app's own storage: files/art/&lt;id&gt;.jpg (up to 3840x2160), files/thumbs/&lt;id&gt;.jpg and
 * files/library.json. One instance per process; every method is synchronized, and every change is written at once.
 */
final class Library {
    static final String TAG = "ArtFrame";
    static final String STARTER = "Starter";
    static final String UPLOADS = "My uploads";
    static final String MUSEUM = "Saved finds";

    /** What the art button (and the card) shows: everything, one collection, or one piece. */
    static final String DEFAULT_ALL = "all";
    static final String DEFAULT_COLLECTION = "collection";
    static final String DEFAULT_PIECE = "piece";

    private static Library instance;

    private final File dir;
    private final File artDir;
    private final File thumbDir;
    private final List<Artwork> items = new ArrayList<>();
    private final Set<String> collections = new LinkedHashSet<>();
    private String defaultKind = DEFAULT_ALL;
    private String defaultId = "";
    private boolean starterImported;
    private long version;

    static synchronized Library get(Context context) {
        if (instance == null) instance = new Library(context.getApplicationContext().getFilesDir());
        return instance;
    }

    private Library(File dir) {
        this.dir = dir;
        artDir = new File(dir, "art");
        thumbDir = new File(dir, "thumbs");
        artDir.mkdirs();
        thumbDir.mkdirs();
        load();
    }

    // --- reading -----------------------------------------------------------------------------------------

    /** Bumped on every change, so screens can tell when to reload. */
    synchronized long version() {
        return version;
    }

    synchronized List<Artwork> all() {
        return new ArrayList<>(items);
    }

    synchronized List<Artwork> inCollection(String name) {
        List<Artwork> out = new ArrayList<>();
        for (Artwork a : items) if (a.collections.contains(name)) out.add(a);
        return out;
    }

    synchronized Artwork find(String id) {
        for (Artwork a : items) if (a.id.equals(id)) return a;
        return null;
    }

    synchronized List<String> collections() {
        return new ArrayList<>(collections);
    }

    File picture(Artwork a) {
        return new File(artDir, a.file);
    }

    File thumb(Artwork a) {
        return new File(thumbDir, a.file);
    }

    synchronized String defaultKind() {
        return defaultKind;
    }

    synchronized String defaultId() {
        return defaultId;
    }

    /** Words for the art button's choice, for the screens that show it. */
    synchronized String describeDefault(Context c) {
        if (DEFAULT_PIECE.equals(defaultKind)) {
            Artwork a = find(defaultId);
            if (a != null) return c.getString(R.string.default_piece, a.title);
        }
        if (DEFAULT_COLLECTION.equals(defaultKind) && collections.contains(defaultId)) {
            return c.getString(R.string.default_collection, defaultId);
        }
        return c.getString(R.string.default_all);
    }

    /** The pieces the art button plays, in library order. */
    synchronized List<Artwork> defaultPlaylist() {
        if (DEFAULT_PIECE.equals(defaultKind)) {
            Artwork a = find(defaultId);
            if (a != null) return Collections.singletonList(a);
        }
        if (DEFAULT_COLLECTION.equals(defaultKind) && collections.contains(defaultId)) {
            List<Artwork> list = inCollection(defaultId);
            if (!list.isEmpty()) return list;
        }
        return all();
    }

    // --- changing ----------------------------------------------------------------------------------------

    synchronized void setDefault(String kind, String id) {
        defaultKind = kind;
        defaultId = id == null ? "" : id;
        save();
    }

    synchronized void update(Artwork a) {
        save();
    }

    synchronized void delete(Artwork a) {
        items.remove(a);
        picture(a).delete();
        thumb(a).delete();
        if (DEFAULT_PIECE.equals(defaultKind) && defaultId.equals(a.id)) defaultKind = DEFAULT_ALL;
        save();
    }

    synchronized boolean addCollection(String name) {
        name = cleanName(name);
        if (name.isEmpty() || collections.contains(name)) return false;
        collections.add(name);
        save();
        return true;
    }

    synchronized void deleteCollection(String name) {
        collections.remove(name);
        for (Artwork a : items) a.collections.remove(name);
        if (DEFAULT_COLLECTION.equals(defaultKind) && defaultId.equals(name)) defaultKind = DEFAULT_ALL;
        save();
    }

    static String cleanName(String name) {
        if (name == null) return "";
        String s = name.replaceAll("[\\p{Cntrl}]", "").trim();
        return s.length() > 40 ? s.substring(0, 40).trim() : s;
    }

    /**
     * Adds a picture from a stream (a download, an upload or an asset). The stream is copied to a temporary file,
     * checked to be a picture, fitted to 4K and stored. Throws when it is not a picture or bigger than maxBytes.
     */
    Artwork add(InputStream in, long maxBytes, Artwork meta, File cacheDir) throws IOException {
        File tmp = File.createTempFile("art-in", ".img", cacheDir);
        try {
            copy(in, tmp, maxBytes);
            return addFile(tmp, meta);
        } finally {
            tmp.delete();
        }
    }

    /** Stores an already downloaded or received file (not deleted here). */
    Artwork addFile(File src, Artwork meta) throws IOException {
        String id = UUID.randomUUID().toString();
        meta.id = id;
        meta.file = id + ".jpg";
        meta.added = System.currentTimeMillis();
        int[] size = Images.store(src, new File(artDir, meta.file), new File(thumbDir, meta.file));
        meta.width = size[0];
        meta.height = size[1];
        synchronized (this) {
            collections.addAll(meta.collections);
            items.add(meta);
            save();
        }
        return meta;
    }

    static void copy(InputStream in, File dst, long maxBytes) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        try (OutputStream out = new FileOutputStream(dst)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) throw new IOException("too large");
                out.write(buf, 0, n);
            }
        }
    }

    // --- starter set ---------------------------------------------------------------------------------------

    synchronized boolean needsStarter() {
        return !starterImported;
    }

    /**
     * On the first start, the bundled paintings fetch-starter.sh put in assets/starter. Slow (each is fitted and
     * re-encoded), so callers run it off the main thread; a second call while one runs waits, then returns.
     */
    void importStarter(Context context) {
        synchronized (starterLock) {
            if (!needsStarter()) return;
            importStarterLocked(context);
        }
    }

    private final Object starterLock = new Object();

    private void importStarterLocked(Context context) {
        try {
            JSONArray list;
            try (InputStream in = context.getAssets().open("starter/starter.json")) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                list = new JSONObject(out.toString("UTF-8")).getJSONArray("artworks");
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                Artwork a = new Artwork();
                a.title = o.optString("title");
                a.artist = o.optString("artist");
                a.date = o.optString("date");
                a.source = "starter";
                a.url = o.optString("url");
                a.credit = o.optString("credit");
                a.collections.add(STARTER);
                a.fill = o.optBoolean("fill", true);
                try (InputStream in = context.getAssets().open("starter/" + o.getString("file"))) {
                    add(in, 64L << 20, a, context.getCacheDir());
                } catch (IOException missing) {
                    Log.i(TAG, "starter picture not bundled: " + o.optString("file"));
                }
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "no starter set: " + e);
        }
        synchronized (this) {
            starterImported = true;
            save();
        }
    }

    // --- storage -------------------------------------------------------------------------------------------

    private void load() {
        File f = new File(dir, "library.json");
        if (!f.isFile()) return;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            JSONArray arr = o.optJSONArray("artworks");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                Artwork a = Artwork.fromJson(arr.getJSONObject(i));
                if (a.id.isEmpty() || !a.file.matches("[0-9a-f-]{36}\\.jpg")) continue;
                if (new File(artDir, a.file).isFile()) items.add(a);
            }
            JSONArray cols = o.optJSONArray("collections");
            if (cols != null) for (int i = 0; i < cols.length(); i++) {
                String name = cleanName(cols.optString(i));
                if (!name.isEmpty()) collections.add(name);
            }
            for (Artwork a : items) collections.addAll(a.collections);
            defaultKind = o.optString("defaultKind", DEFAULT_ALL);
            defaultId = o.optString("defaultId", "");
            starterImported = o.optBoolean("starterImported");
        } catch (IOException | JSONException e) {
            Log.w(TAG, "library.json unreadable, kept as library.json.bad: " + e);
            f.renameTo(new File(dir, "library.json.bad"));
        }
    }

    private void save() {
        version++;
        try {
            JSONObject o = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Artwork a : items) arr.put(a.toJson());
            o.put("version", 1).put("artworks", arr).put("collections", new JSONArray(collections))
                    .put("defaultKind", defaultKind).put("defaultId", defaultId)
                    .put("starterImported", starterImported);
            File tmp = new File(dir, "library.json.tmp");
            Files.write(tmp.toPath(), o.toString(1).getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(new File(dir, "library.json"))) Log.w(TAG, "library.json not replaced");
        } catch (IOException | JSONException e) {
            Log.w(TAG, "library.json not written: " + e);
        }
    }
}
