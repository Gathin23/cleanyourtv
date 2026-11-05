package io.github.anhost77.salon;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "À voir" for the launcher's Home widget: content://...now/picks, what is popular this week on the household's
 * platforms (TMDB). Read at most every 12 hours, only while Home asks; small posters kept in files/posters and
 * carried in the cursor as JPEG bytes. Columns: id, tv (0/1), name, poster.
 */
final class Picks {
    static final Uri URI = Uri.parse("content://io.github.anhost77.salon.now/picks");
    private static final String[] COLUMNS = {"id", "tv", "name", "poster"};
    private static final long REFRESH_MS = 12 * 3_600_000L;
    private static final long RETRY_MS = 20 * 60_000L;
    private static final AtomicBoolean fetching = new AtomicBoolean();

    private Picks() { }

    static Cursor query(Context ctx) {
        MatrixCursor out = new MatrixCursor(COLUMNS);
        if (!Tmdb.enabled()) return out;
        SharedPreferences cache = ctx.getSharedPreferences("picks", 0);
        long now = System.currentTimeMillis();
        if (now - cache.getLong("at", 0) > REFRESH_MS && now - cache.getLong("tried", 0) > RETRY_MS) fetch(ctx, cache);
        try {
            JSONArray rows = new JSONArray(cache.getString("rows", "[]"));
            File dir = new File(ctx.getFilesDir(), "posters");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                File f = new File(dir, r.getString("file"));
                if (!f.isFile()) continue;
                out.addRow(new Object[] {r.getInt("id"), r.getInt("tv"), r.getString("name"), Files.readAllBytes(f.toPath())});
            }
        } catch (Exception broken) {
            // Replaced by the next fetch.
        }
        out.setNotificationUri(ctx.getContentResolver(), URI);
        return out;
    }

    private static void fetch(Context ctx, SharedPreferences cache) {
        if (!fetching.compareAndSet(false, true)) return;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                cache.edit().putLong("tried", System.currentTimeMillis()).apply();
                List<String> ids = new ArrayList<>();
                for (String[] p : Platforms.household(app)) ids.add(p[1]);
                if (ids.isEmpty()) return;
                List<Tmdb.Title> titles = Tmdb.picks(TextUtils.join("|", ids), 12);
                File dir = new File(app.getFilesDir(), "posters");
                if (!dir.isDirectory() && !dir.mkdirs()) return;
                JSONArray rows = new JSONArray();
                for (Tmdb.Title t : titles) {
                    String file = (t.tv ? "tv" : "m") + t.id + ".jpg";
                    File f = new File(dir, file);
                    if (!f.isFile() && !save(Tmdb.image(t.poster, "w154"), f)) continue;
                    rows.put(new JSONObject().put("id", t.id).put("tv", t.tv ? 1 : 0).put("name", t.name).put("file", file));
                }
                if (rows.length() == 0) return;
                cache.edit().putString("rows", rows.toString()).putLong("at", System.currentTimeMillis()).apply();
                File[] old = dir.listFiles();
                String keep = rows.toString();
                if (old != null) for (File o : old) if (!keep.contains("\"" + o.getName() + "\"")) o.delete();
                app.getContentResolver().notifyChange(URI, null);
            } catch (Exception offline) {
                // Retried after RETRY_MS.
            } finally {
                fetching.set(false);
            }
        }, "picks").start();
    }

    private static boolean save(String url, File f) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8_000);
            c.setReadTimeout(10_000);
            Bitmap b;
            try (InputStream in = c.getInputStream()) {
                b = BitmapFactory.decodeStream(in);
            }
            if (b == null) return false;
            try (FileOutputStream out = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.JPEG, 88, out);
            }
            b.recycle();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
