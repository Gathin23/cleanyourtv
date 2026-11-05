package io.github.anhost77.salon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The Movie Database (themoviedb.org), with the owner's personal token (Settings.tmdbToken(), from
 * work/tmdb.properties): trailers for a title, and what the streaming platforms in France added lately (TMDB's
 * watch-provider data, from JustWatch). Only titles and ids are sent; nothing about the household.
 */
final class Tmdb {
    /** Platform name, TMDB provider id (watch_region FR), the TV app's package. */
    static final String[][] PLATFORMS = {
            {"Netflix", "8", "com.netflix.ninja"},
            {"Prime Video", "119", "com.amazon.amazonvideo.livingroom"},
            {"Disney+", "337", "com.disney.disneyplus"},
            {"Canal+", "381", "com.canal.android.canal"},
            {"Paramount+", "531", "com.cbs.ca"},
            {"TF1+", "1754", "fr.tf1.mytf1"},
            {"M6+", "147", "fr.m6.m6replay"},
            {"Apple TV", "350", "com.apple.atve.androidtv.appletv"},
            {"Arte", "234", "tv.arte.plus7"}};

    /** Providers the CANAL+ offer includes inside myCANAL: Paramount+, Apple TV (HBO Max is not in this household's offer). */
    static final java.util.Set<String> VIA_MYCANAL = new java.util.HashSet<>(java.util.Arrays.asList("531", "350"));

    static final class Title {
        int id;
        boolean tv;
        String name, overview, poster, backdrop, date;
        double vote;
    }

    private Tmdb() { }

    static boolean enabled() {
        return !Settings.tmdbToken().isEmpty();
    }

    /** The YouTube key of the best trailer for a programme title (French first, then any), or null. */
    static String trailerKey(String title) throws IOException {
        try {
            JSONArray results = get("/search/multi?language=fr-FR&include_adult=false&query="
                    + URLEncoder.encode(clean(title), "UTF-8")).optJSONArray("results");
            for (int i = 0; results != null && i < Math.min(3, results.length()); i++) {
                JSONObject r = results.getJSONObject(i);
                String type = r.optString("media_type");
                if (!"movie".equals(type) && !"tv".equals(type)) continue;
                String key = videoKey(type, r.getInt("id"));
                if (key != null) return key;
            }
            return null;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    static String videoKey(String type, int id) throws IOException {
        try {
            for (String lang : new String[] {"fr-FR", "en-US"}) {
                JSONArray v = get("/" + type + "/" + id + "/videos?language=" + lang).optJSONArray("results");
                String best = null;
                for (int i = 0; v != null && i < v.length(); i++) {
                    JSONObject o = v.getJSONObject(i);
                    if (!"YouTube".equals(o.optString("site"))) continue;
                    if ("Trailer".equals(o.optString("type"))) return o.getString("key");
                    if (best == null && "Teaser".equals(o.optString("type"))) best = o.getString("key");
                }
                if (best != null) return best;
            }
            return null;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    /** The styles of the Nouveautés page: name, TMDB film genres, TMDB series genres ("" = none for series). */
    static final String[][] STYLES = {
            {"Action", "28", "10759"}, {"Comédie", "35", "35"}, {"Drame", "18", "18"}, {"Thriller", "53", "9648"},
            {"Science-fiction", "878", "10765"}, {"Animation", "16", "16"}, {"Famille", "10751", "10751|10762"},
            {"Horreur", "27", ""}, {"Romance", "10749", ""}, {"Documentaire", "99", "99"}, {"Policier", "80", "80"}};

    /**
     * A platform's films or series in France for one style (null = all): first what came out in the last 120 days,
     * newest first, then the rest of its catalogue, most watched first (TMDB knows what a platform carries, not when
     * it added it, so older films it added lately are found in the second part).
     */
    static List<Title> catalogue(String providerId, boolean tv, String[] style) throws IOException {
        String genres = style == null ? "" : style[tv ? 2 : 1];
        List<Title> out = new ArrayList<>();
        if (style != null && genres.isEmpty()) return out;
        SimpleDateFormat d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        Calendar now = Calendar.getInstance();
        String to = d.format(now.getTime());
        now.add(Calendar.DAY_OF_MONTH, -120);
        String from = d.format(now.getTime());
        String date = tv ? "first_air_date" : "primary_release_date";
        String base = "/discover/" + (tv ? "tv" : "movie") + "?language=fr-FR&watch_region=FR&with_watch_providers="
                + providerId + "&with_watch_monetization_types=flatrate|free|ads"
                + (genres.isEmpty() ? "" : "&with_genres=" + genres);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        add(out, seen, base + "&sort_by=" + date + ".desc&" + date + ".gte=" + from + "&" + date + ".lte=" + to
                + "&vote_count.gte=3", tv, 12);
        for (int page = 1; page <= 2; page++) add(out, seen, base + "&sort_by=popularity.desc&page=" + page, tv, 40);
        return out;
    }

    /** Films and series matching a search, best first. */
    static List<Title> search(String query) throws IOException {
        List<Title> out = new ArrayList<>();
        try {
            JSONArray r = get("/search/multi?language=fr-FR&include_adult=false&query="
                    + URLEncoder.encode(query.trim(), "UTF-8")).optJSONArray("results");
            for (int i = 0; r != null && i < r.length(); i++) {
                JSONObject o = r.getJSONObject(i);
                String type = o.optString("media_type");
                if ("movie".equals(type) || "tv".equals(type)) {
                    Title t = title(o, "tv".equals(type));
                    if (!t.poster.isEmpty()) out.add(t);
                }
            }
        } catch (org.json.JSONException e) {
            out.clear();
        }
        return out;
    }

    /**
     * "Vous aimerez aussi": TMDB's recommendations for a programme of the guide, found by its title (a film or a
     * series as {@code tv} says, else whichever matches first). Titles with a French summary only.
     */
    static List<Title> recommendations(String programme, Boolean tv) throws IOException {
        List<Title> out = new ArrayList<>();
        try {
            String type = tv == null ? "multi" : tv ? "tv" : "movie";
            JSONArray r = get("/search/" + type + "?language=fr-FR&include_adult=false&query="
                    + URLEncoder.encode(clean(programme), "UTF-8")).optJSONArray("results");
            if (r == null || r.length() == 0) return out;
            JSONObject first = r.getJSONObject(0);
            boolean isTv = tv != null ? tv : "tv".equals(first.optString("media_type"));
            if (tv == null && !"tv".equals(first.optString("media_type")) && !"movie".equals(first.optString("media_type"))) return out;
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            seen.add(first.getInt("id"));
            add(out, seen, "/" + (isTv ? "tv" : "movie") + "/" + first.getInt("id") + "/recommendations?language=fr-FR", isTv, 14);
            if (out.size() < 6) {
                add(out, seen, "/" + (isTv ? "tv" : "movie") + "/" + first.getInt("id") + "/similar?language=fr-FR", isTv, 14 - out.size());
            }
        } catch (org.json.JSONException e) {
            out.clear();
        }
        return out;
    }

    /** What is popular this week on any of the given platforms in France, films and series alternated. */
    static List<Title> picks(String providerIds, int max) throws IOException {
        List<Title> films = new ArrayList<>(), series = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        String q = "?language=fr-FR&watch_region=FR&with_watch_monetization_types=flatrate|free|ads&sort_by=popularity.desc"
                + "&vote_count.gte=20&with_watch_providers=" + providerIds;
        add(films, seen, "/discover/movie" + q, false, max);
        add(series, seen, "/discover/tv" + q, true, max);
        List<Title> out = new ArrayList<>();
        for (int i = 0; out.size() < max && (i < films.size() || i < series.size()); i++) {
            if (i < films.size()) out.add(films.get(i));
            if (i < series.size() && out.size() < max) out.add(series.get(i));
        }
        return out;
    }

    /** A title's whole sheet in one call: details, cast, videos, French providers and recommendations. */
    static JSONObject details(int id, boolean tv) throws IOException {
        return get("/" + (tv ? "tv" : "movie") + "/" + id + "?language=fr-FR&include_video_language=fr,en"
                + "&append_to_response=credits,videos,watch/providers,recommendations");
    }

    static Title fromJson(JSONObject o, boolean tv) throws org.json.JSONException {
        return title(o, tv);
    }

    /** A streaming service's own id for a TMDB title, from Wikidata (P1874 Netflix, P7595/P7596 Disney+), or null. */
    static String wikidataId(int tmdbId, boolean tv, String property) {
        String q = "SELECT ?v WHERE { ?i wdt:" + (tv ? "P4983" : "P4947") + " \"" + tmdbId + "\" . ?i wdt:" + property
                + " ?v } LIMIT 1";
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://query.wikidata.org/sparql?format=json&query="
                    + URLEncoder.encode(q, "UTF-8")).openConnection();
            c.setConnectTimeout(6_000);
            c.setReadTimeout(8_000);
            c.setRequestProperty("User-Agent", "TvGuideHome/1.0 (personal TV app)");
            c.setRequestProperty("Accept", "application/sparql-results+json");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                for (int n; (n = in.read(b)) != -1; ) out.write(b, 0, n);
                JSONArray rows = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8))
                        .getJSONObject("results").getJSONArray("bindings");
                return rows.length() == 0 ? null : rows.getJSONObject(0).getJSONObject("v").getString("value");
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * The address of a TMDB title on a platform, from JustWatch (search by name, kept only when the TMDB id matches):
     * the first offer whose JustWatch package starts with one of {@code packages} (netflix, canal, sixplay…), or null.
     * Unofficial GraphQL endpoint of justwatch.com; checked 05/11/2026: Netflix ids for the 30 most watched titles,
     * myCANAL (mycanal.fr / canalplus.com …/h/{id}) and M6+ (m6.fr/…-p_{id}) open the title page on the TV.
     */
    static String justWatchUrl(int tmdbId, boolean tv, String name, String... packages) {
        HttpURLConnection c = null;
        try {
            JSONObject vars = new JSONObject().put("c", "FR")
                    .put("f", new JSONObject().put("searchQuery", name));
            JSONObject body = new JSONObject().put("variables", vars).put("query",
                    "query($c:Country!,$f:TitleFilter){popularTitles(country:$c,first:10,filter:$f){edges{node{"
                            + "objectType content(country:$c,language:\"fr\"){externalIds{tmdbId}}"
                            + " offers(country:$c,platform:WEB){package{technicalName} standardWebURL}}}}}");
            c = (HttpURLConnection) new URL("https://apis.justwatch.com/graphql").openConnection();
            c.setConnectTimeout(6_000);
            c.setReadTimeout(8_000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "TvGuideHome/1.0 (personal TV app)");
            try (java.io.OutputStream o = c.getOutputStream()) {
                o.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            String json;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                for (int n; (n = in.read(b)) != -1; ) out.write(b, 0, n);
                json = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
            JSONArray edges = new JSONObject(json).getJSONObject("data").getJSONObject("popularTitles")
                    .getJSONArray("edges");
            for (int i = 0; i < edges.length(); i++) {
                JSONObject n = edges.getJSONObject(i).getJSONObject("node");
                if (tv != "SHOW".equals(n.optString("objectType"))) continue;
                JSONObject ids = n.optJSONObject("content") == null ? null
                        : n.getJSONObject("content").optJSONObject("externalIds");
                if (ids == null || !String.valueOf(tmdbId).equals(ids.optString("tmdbId"))) continue;
                JSONArray offers = n.optJSONArray("offers");
                for (int k = 0; offers != null && k < offers.length(); k++) {
                    JSONObject o = offers.getJSONObject(k);
                    JSONObject pk = o.optJSONObject("package");
                    String tech = pk == null ? "" : pk.optString("technicalName");
                    String url = o.optString("standardWebURL").trim();
                    for (String want : packages) {
                        if (tech.startsWith(want) && url.startsWith("https://")) return url;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** The TMDB providers that stream a title in France (subscription, free or with ads). */
    static java.util.Set<String> providers(Title t) throws IOException {
        java.util.Set<String> out = new java.util.HashSet<>();
        try {
            JSONObject fr = get("/" + (t.tv ? "tv" : "movie") + "/" + t.id + "/watch/providers")
                    .optJSONObject("results");
            fr = fr == null ? null : fr.optJSONObject("FR");
            if (fr == null) return out;
            for (String kind : new String[] {"flatrate", "free", "ads"}) {
                JSONArray a = fr.optJSONArray(kind);
                for (int i = 0; a != null && i < a.length(); i++) out.add(String.valueOf(a.getJSONObject(i).getInt("provider_id")));
            }
        } catch (org.json.JSONException e) {
            out.clear();
        }
        return out;
    }

    private static void add(List<Title> out, java.util.Set<Integer> seen, String path, boolean tv, int max)
            throws IOException {
        try {
            JSONArray r = get(path).optJSONArray("results");
            int n = 0;
            for (int i = 0; r != null && i < r.length() && n < max; i++) {
                Title t = title(r.getJSONObject(i), tv);
                // No French summary: usually a foreign title nobody here is looking for.
                if (t.poster.isEmpty() || t.overview.isEmpty() || !seen.add(t.id)) continue;
                out.add(t);
                n++;
            }
        } catch (org.json.JSONException e) {
            // That page is skipped.
        }
    }

    private static Title title(JSONObject o, boolean tv) throws org.json.JSONException {
        Title t = new Title();
        t.id = o.getInt("id");
        t.tv = tv;
        t.name = o.optString(tv ? "name" : "title");
        t.overview = o.optString("overview");
        t.poster = o.isNull("poster_path") ? "" : o.optString("poster_path");
        t.backdrop = o.isNull("backdrop_path") ? "" : o.optString("backdrop_path");
        t.date = o.optString(tv ? "first_air_date" : "release_date");
        t.vote = o.optDouble("vote_average", 0);
        return t;
    }

    static String image(String path, String size) {
        return path == null || path.isEmpty() ? "" : "https://image.tmdb.org/t/p/" + size + path;
    }

    /** "Les Disparus des Argonnes - Saison 1" → "Les Disparus des Argonnes". */
    static String clean(String title) {
        return title.replaceAll("\\s*[-–:]\\s*(Saison|Episode|Épisode)\\s*\\d+.*$", "").trim();
    }

    private static JSONObject get(String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.themoviedb.org/3" + path).openConnection();
        c.setConnectTimeout(8_000);
        c.setReadTimeout(10_000);
        c.setRequestProperty("Authorization", "Bearer " + Settings.tmdbToken());
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) != -1; ) out.write(b, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        } finally {
            c.disconnect();
        }
    }
}
