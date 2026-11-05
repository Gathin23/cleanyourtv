package io.github.anhost77.salon;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipInputStream;

/**
 * Tonight's prime time on the French TNT channels, for the launcher's left Home panel: content://...now/tv.
 * Read from the free XMLTV guide at xmltvfr.fr (one zip, about 1 MB, at most every 6 hours, only while Home asks).
 * For each channel: the first programme of 30 minutes or more starting between 20:50 and 22:00 tonight. Channel
 * logos are downloaded once, shrunk to 64 px high and kept in the app's files; the cursor carries them as PNG bytes.
 * The same read also keeps the whole guide, one file a day from yesterday to a week ahead (files/guide/yyyyMMdd.json,
 * programmes by their local start day), for {@link TonightActivity}'s tabs.
 */
final class TvTonight {
    static final Uri URI = Uri.parse("content://io.github.anhost77.salon.now/tv");
    private static final String FEED = "https://xmltvfr.fr/xmltv/xmltv_tnt.zip";
    /** All of France's channels (about 10 MB, 5 days): read only for the channels the TNT file does not carry. */
    private static final String FEED_FRANCE = "https://xmltvfr.fr/xmltv/xmltv_fr.zip";
    private static final String[] COLUMNS = {"channel", "time", "title", "kind", "logo"};
    /** TNT order; the news channels have no evening programme worth showing and are left out. */
    static final String[][] CHANNELS = {
            {"TF1.fr", "TF1"}, {"France2.fr", "France 2"}, {"France3.fr", "France 3"}, {"CanalPlus.fr", "Canal+"},
            {"France5.fr", "France 5"}, {"M6.fr", "M6"}, {"Arte.fr", "Arte"}, {"W9.fr", "W9"}, {"TMC.fr", "TMC"},
            {"NT1.fr", "TFX"}, {"France4.fr", "France 4"}, {"CStar.fr", "CStar"}, {"Gulli.fr", "Gulli"},
            {"TF1SeriesFilms.fr", "TF1 Séries Films"}, {"LEquipe21.fr", "L'Équipe"}, {"6ter.fr", "6ter"},
            {"Numero23.fr", "RMC Story"}, {"RMCDecouverte.fr", "RMC Découverte"}, {"Cherie25.fr", "RMC Life"},
            {"ParisPremiere.fr", "Paris Première"}, {"CanalPlusSport.fr", "Canal+ Sport"},
            {"CanalPlusCinema.fr", "Canal+ Cinéma"}, {"PlanetePlus.fr", "Planète+"},
            // The CANAL+ offer, from the France file.
            {"CanalPlusSeries.fr", "Canal+ Séries"}, {"CanalPlusGrandEcran.fr", "Canal+ Grand Écran"},
            {"CanalPlusBoxOffice.fr", "Canal+ Box Office"}, {"CanalPlusSport360.fr", "Canal+ Sport 360"},
            {"CanalPlusFoot.fr", "Canal+ Foot"}, {"CanalPlusDocs.fr", "Canal+ Docs"}, {"CanalPlusKIDS.fr", "Canal+ Kids"},
            {"CinePlusPremier.fr", "OCS"}, {"CinePlusFrisson.fr", "Ciné+ Frisson"},
            {"CinePlusEmotion.fr", "Ciné+ Émotion"}, {"CinePlusFamiz.fr", "Ciné+ Family"},
            {"CinePlusClassic.fr", "Ciné+ Classic"}, {"CinePlusClub.fr", "Ciné+ Festival"}};

    /** The genre groups of the guide's filter, in order; {@link #genre} files each programme under one. */
    static final String[] GENRES = {"Films", "Séries", "Sport", "Divertissement", "Documentaires", "Jeunesse", "Infos"};

    /** The genre group of a programme from all its XMLTV categories (lower case, joined); -1 for none. */
    static int genre(String cats) {
        String c = java.text.Normalizer.normalize(cats, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (c.contains("jeunesse") || c.contains("dessin anime") || c.contains("animation") && !c.contains("film")) return 5;
        if (c.contains("film") || c.contains("cinema") || c.contains("telefilm")) return 0;
        if (c.contains("serie") || c.contains("feuilleton") || c.contains("sitcom")) return 1;
        if (c.contains("sport") || c.contains("football") || c.contains("rugby") || c.contains("tennis")
                || c.contains("cyclisme") || c.contains("basket") || c.contains("handball") || c.contains("golf")) return 2;
        if (c.contains("documentaire") || c.contains("decouverte") || c.contains("histoire") || c.contains("nature")
                || c.contains("science")) return 4;
        if (c.contains("information") || c.contains("actualite") || c.contains("journal") || c.contains("meteo")
                || c.contains("politique") || c.contains("debat")) return 6;
        if (c.contains("divertissement") || c.contains("jeu") || c.contains("variete") || c.contains("tele-realite")
                || c.contains("telerealite") || c.contains("humour") || c.contains("musique") || c.contains("spectacle")
                || c.contains("talk")) return 3;
        return -1;
    }
    private static final long REFRESH_MS = 6 * 3_600_000L;
    private static final long RETRY_MS = 15 * 60_000L;
    private static final AtomicBoolean fetching = new AtomicBoolean();

    private TvTonight() { }

    static Cursor query(Context ctx) {
        SharedPreferences cache = ctx.getSharedPreferences("tv", 0);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new java.util.Date());
        long now = System.currentTimeMillis();
        boolean fresh = today.equals(cache.getString("day", "")) && now - cache.getLong("at", 0) < REFRESH_MS
                && cache.getInt("version", 0) == 6;
        // An old cache (another "version") is never fresh, so it is fetched again too, but after RETRY_MS like any
        // other try: skipping the wait there made every query fetch again while the guide could not be reached.
        if (!fresh && now - cache.getLong("tried", 0) > RETRY_MS) fetch(ctx, cache, today);
        MatrixCursor out = new MatrixCursor(COLUMNS);
        if (today.equals(cache.getString("day", ""))) {
            try {
                JSONArray rows = new JSONArray(cache.getString("rows", "[]"));
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    if (Settings.off(Settings.CHANNELS, r.optString("id"))) continue; // Réglages
                    out.addRow(new Object[] {r.getString("channel"), r.getString("time"), r.getString("title"),
                            r.optString("kind"), logo(ctx, r.optString("id"))});
                }
            } catch (Exception broken) {
                // Replaced by the next fetch.
            }
        }
        out.setNotificationUri(ctx.getContentResolver(), URI);
        return out;
    }

    private static byte[] logo(Context ctx, String id) {
        File f = new File(new File(ctx.getFilesDir(), "logos2"), id + ".png");
        try {
            return f.isFile() ? Files.readAllBytes(f.toPath()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void fetch(Context ctx, SharedPreferences cache, String today) {
        if (!fetching.compareAndSet(false, true)) return;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                cache.edit().putLong("tried", System.currentTimeMillis()).apply();
                Map<String, String> icons = new HashMap<>();
                JSONArray rows = read(app, icons);
                if (rows.length() > 0) {
                    cache.edit().putString("day", today).putString("rows", rows.toString()).putInt("version", 6)
                            .putLong("at", System.currentTimeMillis()).apply();
                    app.getContentResolver().notifyChange(URI, null);
                    if (saveLogos(app, icons)) app.getContentResolver().notifyChange(URI, null);
                }
            } catch (Exception offline) {
                // Keep the last guide; retried after RETRY_MS.
            } finally {
                fetching.set(false);
            }
        }, "tv-tonight").start();
    }

    /** Downloads the logos not yet on disk, 64 px high. True when one was added. */
    private static boolean saveLogos(Context ctx, Map<String, String> icons) {
        File dir = new File(ctx.getFilesDir(), "logos2");
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        boolean added = false;
        for (Map.Entry<String, String> e : icons.entrySet()) {
            File f = new File(dir, e.getKey() + ".png");
            if (f.isFile()) continue;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(e.getValue()).openConnection();
                c.setConnectTimeout(10_000);
                c.setReadTimeout(15_000);
                Bitmap big;
                try (InputStream in = c.getInputStream()) {
                    big = BitmapFactory.decodeStream(in);
                }
                if (big == null) continue;
                Bitmap cut = trim(big);
                if (cut != big) big.recycle();
                big = cut;
                int h = 64;
                int w = Math.max(1, Math.round(big.getWidth() * (h / (float) big.getHeight())));
                Bitmap small = Bitmap.createScaledBitmap(big, w, h, true);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    small.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                big.recycle();
                small.recycle();
                added = true;
            } catch (Exception skip) {
                // That channel keeps its name instead of a logo.
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return added;
    }

    /** The logo without its transparent margin (the guide's logos sit small in the middle of a square). */
    private static Bitmap trim(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int left = w, top = h, right = -1, bottom = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 24) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right < left || bottom < top) return b;
        return Bitmap.createBitmap(b, left, top, right - left + 1, bottom - top + 1);
    }

    /** Writes each day's file (through a temporary file, so a reader never sees half of one) and drops old days. */
    private static void saveDays(Context ctx, Map<String, JSONArray> days) {
        File dir = new File(ctx.getFilesDir(), "guide");
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        for (Map.Entry<String, JSONArray> e : days.entrySet()) {
            File f = new File(dir, e.getKey() + ".json");
            File tmp = new File(dir, e.getKey() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(e.getValue().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception skip) {
                continue;
            }
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
        }
        File[] all = dir.listFiles();
        if (all == null) return;
        for (File f : all) {
            String n = f.getName();
            if (n.endsWith(".json") && !days.containsKey(n.substring(0, n.length() - 5))) f.delete();
        }
    }

    /** The day file of the guide, programmes starting that local day in channel order then time; null if absent. */
    static File dayFile(Context ctx, String yyyyMMdd) {
        return new File(new File(ctx.getFilesDir(), "guide"), yyyyMMdd + ".json");
    }

    private static JSONArray read(Context ctx, Map<String, String> icons) throws Exception {
        Map<String, String> names = new HashMap<>();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < CHANNELS.length; i++) {
            names.put(CHANNELS[i][0], CHANNELS[i][1]);
            index.put(CHANNELS[i][0], i);
        }
        SimpleDateFormat dayKey = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        long keepFrom = midnight.getTimeInMillis() - 24 * 3_600_000L;
        long keepTo = midnight.getTimeInMillis() + 8 * 24 * 3_600_000L;
        Map<String, JSONArray> days = new HashMap<>();
        Calendar from = Calendar.getInstance();
        from.set(Calendar.HOUR_OF_DAY, 20);
        from.set(Calendar.MINUTE, 50);
        from.set(Calendar.SECOND, 0);
        long fromMs = from.getTimeInMillis();
        long toMs = fromMs + 70 * 60_000L;
        SimpleDateFormat stamp = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT);
        SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.FRANCE);
        Map<String, JSONObject> picked = new HashMap<>();

        java.util.Set<String> seen = new java.util.HashSet<>();
        long[] st = {fromMs, toMs, keepFrom, keepTo};
        parse(FEED, names, index, icons, days, picked, seen, null, st, dayKey, stamp, clock);
        java.util.Set<String> missing = new java.util.HashSet<>(names.keySet());
        missing.removeAll(seen);
        if (!missing.isEmpty()) {
            try {
                parse(FEED_FRANCE, names, index, icons, days, picked, seen, missing, st, dayKey, stamp, clock);
            } catch (Exception partial) {
                // The TNT channels are kept; the others wait for the next read.
            }
        }
        // Each day in channel order, then time (the France file comes after the TNT one).
        for (JSONArray day : days.values()) sortDay(day);
        saveDays(ctx, days);
        JSONArray rows = new JSONArray();
        for (String[] c : CHANNELS) {
            JSONObject r = picked.get(c[0]);
            if (r != null) rows.put(r);
        }
        return rows;
    }

    private static void sortDay(JSONArray day) throws Exception {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < day.length(); i++) list.add(day.getJSONObject(i));
        list.sort((a, b) -> a.optInt("c") != b.optInt("c") ? Integer.compare(a.optInt("c"), b.optInt("c"))
                : Long.compare(a.optLong("s"), b.optLong("s")));
        for (int i = 0; i < list.size(); i++) day.put(i, list.get(i));
    }

    /** One XMLTV zip: icons, the days' programmes and tonight's picks for the named channels ({@code only}, if
     *  given, limits it to those). {@code seen} collects the channels that had programmes. */
    private static void parse(String feed, Map<String, String> names, Map<String, Integer> index,
            Map<String, String> icons, Map<String, JSONArray> days, Map<String, JSONObject> picked,
            java.util.Set<String> seen, java.util.Set<String> only, long[] st, SimpleDateFormat dayKey,
            SimpleDateFormat stamp, SimpleDateFormat clock) throws Exception {
        long fromMs = st[0], toMs = st[1], keepFrom = st[2], keepTo = st[3];
        HttpURLConnection connection = (HttpURLConnection) new URL(feed).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(60_000);
        try (InputStream raw = connection.getInputStream(); ZipInputStream zip = new ZipInputStream(raw)) {
            if (zip.getNextEntry() == null) return;
            XmlPullParser p = Xml.newPullParser();
            p.setInput(zip, "UTF-8");
            String channel = null, title = null, kind = null, iconFor = null, sub = null, desc = null, image = null;
            StringBuilder cats = new StringBuilder();
            StringBuilder catNames = new StringBuilder(), actors = new StringBuilder(), directors = new StringBuilder();
            int actorCount = 0;
            String year = null;
            boolean premiere = false;
            long start = 0, stop = 0;
            boolean wanted = false;
            for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
                if (e == XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if ("channel".equals(tag)) {
                        iconFor = p.getAttributeValue(null, "id");
                    } else if ("icon".equals(tag) && iconFor != null && names.containsKey(iconFor)
                            && (only == null || only.contains(iconFor))) {
                        String src = p.getAttributeValue(null, "src");
                        if (src != null && src.startsWith("https://")) icons.put(iconFor, src);
                    } else if ("programme".equals(tag)) {
                        iconFor = null;
                        channel = p.getAttributeValue(null, "channel");
                        wanted = false;
                        title = null;
                        kind = null;
                        sub = null;
                        desc = null;
                        image = null;
                        cats.setLength(0);
                        catNames.setLength(0);
                        actors.setLength(0);
                        directors.setLength(0);
                        actorCount = 0;
                        year = null;
                        premiere = false;
                        if (names.containsKey(channel) && (only == null || only.contains(channel))) {
                            start = stamp.parse(p.getAttributeValue(null, "start")).getTime();
                            stop = stamp.parse(p.getAttributeValue(null, "stop")).getTime();
                            wanted = start >= keepFrom && start < keepTo && stop > start;
                        }
                    } else if (wanted && "title".equals(tag) && title == null) {
                        title = p.nextText();
                    } else if (wanted && "sub-title".equals(tag) && sub == null) {
                        sub = p.nextText();
                    } else if (wanted && "desc".equals(tag) && desc == null) {
                        desc = p.nextText();
                    } else if (wanted && "icon".equals(tag) && image == null) {
                        image = p.getAttributeValue(null, "src");
                    } else if (wanted && ("actor".equals(tag) || "presenter".equals(tag))) {
                        String a = p.nextText().trim();
                        if (actorCount++ < 6 && !a.isEmpty()) actors.append(actors.length() > 0 ? ", " : "").append(a);
                    } else if (wanted && "director".equals(tag)) {
                        String d0 = p.nextText().trim();
                        if (directors.length() < 60 && !d0.isEmpty()) directors.append(directors.length() > 0 ? ", " : "").append(d0);
                    } else if (wanted && "premiere".equals(tag)) {
                        premiere = true;
                    } else if (wanted && "date".equals(tag) && year == null) {
                        year = p.nextText().trim();
                    } else if (wanted && "category".equals(tag)) {
                        String k = p.nextText();
                        cats.append(k.toLowerCase(Locale.FRANCE)).append(" ");
                        if (catNames.indexOf(k) < 0) catNames.append(catNames.length() > 0 ? " · " : "").append(k);
                        if (kind == null || kind.length() > k.length()) kind = k;
                    }
                } else if (e == XmlPullParser.END_TAG && "channel".equals(p.getName())) {
                    iconFor = null;
                } else if (e == XmlPullParser.END_TAG && "programme".equals(p.getName()) && wanted && title != null) {
                    String d = desc == null ? "" : desc.trim();
                    if (d.length() > 700) d = d.substring(0, 697) + "…";
                    String key = dayKey.format(new java.util.Date(start));
                    JSONArray day = days.get(key);
                    if (day == null) days.put(key, day = new JSONArray());
                    seen.add(channel);
                    day.put(new JSONObject().put("c", index.get(channel)).put("s", start).put("e", stop)
                            .put("g", genre(cats.toString()))
                            .put("t", title.trim()).put("st", sub == null ? "" : sub.trim()).put("d", d)
                            .put("k", kind == null ? "" : kind).put("i", image == null ? "" : image)
                            .put("kk", catNames.toString()).put("a", actors.toString()).put("r", directors.toString())
                            .put("y", year == null ? "" : year)
                            .put("n", premiere || d.toLowerCase(Locale.FRANCE).contains("inédit")
                                    || (sub != null && sub.toLowerCase(Locale.FRANCE).contains("inédit")) ? 1 : 0));
                    boolean tonight = start >= fromMs && start < toMs && stop - start >= 30 * 60_000L;
                    if (tonight && !picked.containsKey(channel)) {
                        picked.put(channel, new JSONObject().put("id", channel).put("channel", names.get(channel))
                                .put("time", clock.format(new java.util.Date(start))).put("title", title.trim())
                                .put("kind", kind == null ? "" : kind).put("end", clock.format(new java.util.Date(stop)))
                                .put("sub", sub == null ? "" : sub.trim()).put("desc", d)
                                .put("image", image == null ? "" : image));
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
