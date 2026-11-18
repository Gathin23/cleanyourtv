package com.cleanyourtv.masjidtimes;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The mosque's year-long timetable from my-masjid.com, kept in the app's own files. Fetched when missing or older
 * than 12 hours; a failed fetch keeps the last good copy and waits 15 minutes before trying again.
 */
final class Timetable {
    private Timetable() {}

    private static final String BASE = "https://time.my-masjid.com/api/TimingsInfoScreen/GetMasjidTimings?GuidId=";
    private static final String FILE = "timetable.json";
    private static final long STALE_MS = 12L * 60 * 60 * 1000;
    private static final long RETRY_MS = 15L * 60 * 1000;
    private static final AtomicBoolean fetching = new AtomicBoolean();
    private static volatile long lastAttempt;
    static volatile String lastError = "";

    static String guid(Context ctx) {
        return ctx.getString(R.string.masjid_guid).trim();
    }

    static JSONObject load(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.isFile()) return null;
        try {
            return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    static long updatedAt(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        return f.isFile() ? f.lastModified() : 0;
    }

    /** Starts a fetch in the background when the copy is missing or old; tells Home when it lands. */
    static void refreshIfStale(Context ctx, boolean force) {
        long now = System.currentTimeMillis();
        boolean stale = now - updatedAt(ctx) > STALE_MS;
        if (!force && (!stale || now - lastAttempt < RETRY_MS)) return;
        if (guid(ctx).isEmpty() || !fetching.compareAndSet(false, true)) return;
        lastAttempt = now;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                fetch(app);
                lastError = "";
                PrayerProvider.changed(app);
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                fetching.set(false);
            }
        }, "timetable").start();
    }

    private static void fetch(Context ctx) throws IOException, JSONException {
        URL url = new URL(BASE + URLEncoder.encode(guid(ctx), "UTF-8"));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("my-masjid.com answered " + code);
            String body;
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                body = out.toString("UTF-8");
            }
            JSONObject model = new JSONObject(body).getJSONObject("model");
            if (model.optJSONArray("salahTimings") == null || model.getJSONArray("salahTimings").length() == 0) {
                throw new IOException("no timetable for this mosque");
            }
            File dir = ctx.getFilesDir();
            File tmp = new File(dir, FILE + ".tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(model.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(new File(dir, FILE))) throw new IOException("could not save the timetable");
        } finally {
            c.disconnect();
        }
    }

    /** The timetable rows for today and the next two days (the API gives one row per day of the year). */
    static List<Prayers.Day> days(JSONObject model, LocalDate today) {
        List<Prayers.Day> out = new ArrayList<>();
        JSONArray rows = model.optJSONArray("salahTimings");
        if (rows == null) return out;
        for (LocalDate d : Prayers.window(today)) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.optJSONObject(i);
                if (r == null || r.optInt("day") != d.getDayOfMonth() || r.optInt("month") != d.getMonthValue()) continue;
                out.add(new Prayers.Day(d,
                        new String[] {text(r, "fajr"), text(r, "zuhr"), text(r, "asr"), text(r, "maghrib"), text(r, "isha")},
                        new String[] {text(r, "iqamah_Fajr"), text(r, "iqamah_Zuhr"), text(r, "iqamah_Asr"),
                                text(r, "iqamah_Maghrib"), text(r, "iqamah_Isha")}));
                break;
            }
        }
        return out;
    }

    /** A field as text; "" for a missing or JSON-null one (optString would answer "null"). */
    private static String text(JSONObject r, String key) {
        return r.isNull(key) ? "" : r.optString(key, "").trim();
    }

    static Prayers.Event next(JSONObject model, ZonedDateTime now) {
        JSONObject settings = model.optJSONObject("masjidSettings");
        String jumah = settings == null ? null : text(settings, "jumahTime");
        boolean twelve = settings != null && settings.optBoolean("displayTimeIn12HourFormat", false);
        return Prayers.next(days(model, now.toLocalDate()), jumah, twelve, now);
    }
}
