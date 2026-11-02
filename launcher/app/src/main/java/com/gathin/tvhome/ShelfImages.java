package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Artwork for the shelf's cards: https (HttpURLConnection) and content:// or android.resource:// (ContentResolver),
 * decoded down to card size, kept in a small memory cache and fetched by two background threads. Everything public
 * runs on the main thread, and a callback runs there too.
 */
final class ShelfImages {

    interface Callback {
        void onImage(Bitmap bitmap);
    }

    private static final int CACHE_CARDS = 30;
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private final Context context;
    private final Handler main;
    private final LruCache<String, Bitmap> cache;
    private final Map<String, List<Callback>> waiting = new HashMap<>();
    private final ExecutorService threads = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "shelf-art");
        t.setDaemon(true);
        return t;
    });

    ShelfImages(Context context, Handler main) {
        this(context, main, CACHE_CARDS);
    }

    /** A cache of this many images: 30 cards, or 2 or 3 full-screen backdrops. */
    ShelfImages(Context context, Handler main, int cacheEntries) {
        cache = new LruCache<>(cacheEntries);
        this.context = context.getApplicationContext();
        this.main = main;
    }

    Bitmap cached(String uri) {
        return uri == null ? null : cache.get(uri);
    }

    /** Fetches uri decoded to at least width x height unless it is cached; the callback then runs on the main thread. */
    void load(String uri, int width, int height, Callback callback) {
        Bitmap hit = cached(uri);
        if (hit != null) {
            callback.onImage(hit);
            return;
        }
        List<Callback> list = waiting.get(uri);
        if (list != null) {
            list.add(callback);
            return;
        }
        list = new ArrayList<>();
        list.add(callback);
        waiting.put(uri, list);
        try {
            threads.execute(() -> {
                Bitmap bmp = fetch(uri, width, height);
                main.post(() -> {
                    if (bmp != null) cache.put(uri, bmp);
                    List<Callback> done = waiting.remove(uri);
                    if (done != null && bmp != null) for (Callback c : done) c.onImage(bmp);
                });
            });
        } catch (RuntimeException e) {
            waiting.remove(uri);
        }
    }

    void clear() {
        cache.evictAll();
    }

    void shutdown() {
        threads.shutdownNow();
        cache.evictAll();
    }

    private Bitmap fetch(String uri, int width, int height) {
        try {
            byte[] data = readBytes(uri);
            if (data == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= width && o.outHeight / (sample * 2) >= height) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private byte[] readBytes(String uri) throws IOException {
        Uri parsed = Uri.parse(uri);
        String scheme = parsed.getScheme();
        if (scheme == null) return null;
        HttpURLConnection http = null;
        InputStream in;
        if (scheme.equals("https") || scheme.equals("http")) {
            http = (HttpURLConnection) new URL(uri).openConnection();
            http.setConnectTimeout(5000);
            http.setReadTimeout(8000);
            if (http.getResponseCode() != HttpURLConnection.HTTP_OK) {
                http.disconnect();
                return null;
            }
            in = http.getInputStream();
        } else if (scheme.equals("content") || scheme.equals("android.resource")) {
            in = context.getContentResolver().openInputStream(parsed);
        } else {
            return null;
        }
        if (in == null) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) return null;
            }
            return out.toByteArray();
        } finally {
            in.close();
            if (http != null) http.disconnect();
        }
    }
}
