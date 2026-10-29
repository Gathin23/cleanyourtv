package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight, zero-dependency background fetcher for live and upcoming sports fixtures.
 * Queries public ESPN REST scoreboards with local 2-hour caching.
 * Prioritizes user favorites (Mohun Bagan, East Bengal, Indian Super League, Barcelona, etc.),
 * keeps LIVE events pinned to top, and sorts all upcoming fixtures chronologically by Date & Time.
 */
public final class SportsService {
    private static final String TAG = "TvHome.Sports";
    private static final String CACHE_FILE = "sports_cache_v3.json";
    private static final long CACHE_TTL_MS = 2 * 3600 * 1000L; // 2 hours

    public static class Fixture implements Comparable<Fixture> {
        public String id;
        public String sport; // "SOCCER", "TENNIS", "F1", "CRICKET"
        public String competition; // "La Liga", "Champions League", "F1", "Indian Super League", etc.
        public String title; // "FC Barcelona vs Real Madrid"
        public String statusText; // "LIVE" or "Today 19:30" or "Sat, 31 Oct 19:30"
        public boolean isLive;
        public long timestamp; // Epoch millis UTC for chronological sorting
        public int priority; // Lower = higher display priority
        public String targetPackage; // e.g. "com.jio.media.ondemand", "in.startv.hotstar", "com.sonyliv"
        public String appLabel; // "JioCinema", "Hotstar", "Sony LIV", "Apple TV", "YouTube"
        public String searchQuery; // e.g. for YouTube highlights

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("sport", sport);
                obj.put("competition", competition);
                obj.put("title", title);
                obj.put("statusText", statusText);
                obj.put("isLive", isLive);
                obj.put("timestamp", timestamp);
                obj.put("priority", priority);
                obj.put("targetPackage", targetPackage);
                obj.put("appLabel", appLabel);
                obj.put("searchQuery", searchQuery);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static Fixture fromJson(JSONObject obj) {
            Fixture f = new Fixture();
            f.id = obj.optString("id");
            f.sport = obj.optString("sport");
            f.competition = obj.optString("competition");
            f.title = obj.optString("title");
            f.statusText = obj.optString("statusText");
            f.isLive = obj.optBoolean("isLive");
            f.timestamp = obj.optLong("timestamp", 0);
            f.priority = obj.optInt("priority", 100);
            f.targetPackage = obj.optString("targetPackage");
            f.appLabel = obj.optString("appLabel");
            f.searchQuery = obj.optString("searchQuery");
            return f;
        }

        @Override
        public int compareTo(Fixture other) {
            // 1. Live items always come first
            if (this.isLive != other.isLive) {
                return this.isLive ? -1 : 1;
            }
            // If both are live, rank by favorite priority
            if (this.isLive) {
                return Integer.compare(this.priority, other.priority);
            }
            // 2. Upcoming items: Order strictly chronologically by Date & Time (earlier kickoff/race first)
            if (this.timestamp > 0 && other.timestamp > 0 && this.timestamp != other.timestamp) {
                return Long.compare(this.timestamp, other.timestamp);
            }
            // 3. Same kickoff time or missing timestamp: break ties by favorite priority
            return Integer.compare(this.priority, other.priority);
        }
    }

    public interface Listener {
        void onFixturesLoaded(List<Fixture> fixtures);
        void onError(String message);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SportsService(Context context) {
        this.context = context.getApplicationContext();
    }

    public void loadFixtures(boolean forceRefresh, Listener listener) {
        File cache = new File(context.getCacheDir(), CACHE_FILE);
        if (!forceRefresh && cache.exists() && (System.currentTimeMillis() - cache.lastModified() < CACHE_TTL_MS)) {
            List<Fixture> cachedList = loadFromCache(cache);
            if (cachedList != null && !cachedList.isEmpty()) {
                Log.i(TAG, "Loaded " + cachedList.size() + " fixtures from local cache");
                listener.onFixturesLoaded(cachedList);
                return;
            }
        }

        executor.execute(() -> {
            List<Fixture> fixtures = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            try {
                // 1. Indian Super League: query today and upcoming 4 days to capture Mohun Bagan & East Bengal matches
                SimpleDateFormat ymd = new SimpleDateFormat("yyyyMMdd", Locale.US);
                ymd.setTimeZone(TimeZone.getTimeZone("UTC"));
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

                fetchSoccerScoreboard("https://site.api.espn.com/apis/site/v2/sports/soccer/ind.1/scoreboard", "Indian Super League", fixtures, seenIds);
                for (int i = 1; i <= 4; i++) {
                    Calendar next = (Calendar) cal.clone();
                    next.add(Calendar.DAY_OF_YEAR, i);
                    fetchSoccerScoreboard("https://site.api.espn.com/apis/site/v2/sports/soccer/ind.1/scoreboard?dates=" + ymd.format(next.getTime()), "Indian Super League", fixtures, seenIds);
                }
                Log.i(TAG, "Fixtures after ISL: " + fixtures.size());

                // 2. European & International Soccer
                fetchSoccerScoreboard("https://site.api.espn.com/apis/site/v2/sports/soccer/esp.1/scoreboard", "La Liga", fixtures, seenIds);
                fetchSoccerScoreboard("https://site.api.espn.com/apis/site/v2/sports/soccer/uefa.champions/scoreboard", "Champions League", fixtures, seenIds);
                fetchSoccerScoreboard("https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard", "Premier League", fixtures, seenIds);
                fetchSoccerScoreboard("https://site.api.espn.com/apis/site/v2/sports/soccer/usa.1/scoreboard", "MLS", fixtures, seenIds);
                fetchSoccerScoreboard("https://site.api.espn.com/apis/site/v2/sports/soccer/afc.cup/scoreboard", "AFC Champions League Two", fixtures, seenIds);
                Log.i(TAG, "Fixtures after European Soccer: " + fixtures.size());

                // 3. Formula 1
                fetchF1Scoreboard("https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard", fixtures, seenIds);
                Log.i(TAG, "Fixtures after F1: " + fixtures.size());

                // 4. Tennis (ATP & Grand Slams)
                fetchTennisScoreboard("https://site.api.espn.com/apis/site/v2/sports/tennis/atp/scoreboard", fixtures, seenIds);
                Log.i(TAG, "Fixtures after Tennis: " + fixtures.size());

                // 5. Cricket
                fetchCricketScorepanel("https://site.web.api.espn.com/apis/site/v2/sports/cricket/scorepanel", fixtures, seenIds);
                Log.i(TAG, "Fixtures after Cricket: " + fixtures.size());

                // Sort: LIVE items first, then chronologically by Date & Time, then user priority
                Collections.sort(fixtures);

                if (!fixtures.isEmpty()) {
                    saveToCache(cache, fixtures);
                }

                final List<Fixture> result = fixtures;
                mainHandler.post(() -> listener.onFixturesLoaded(result));
            } catch (Exception e) {
                Log.w(TAG, "Failed to load sports feed: " + e.getMessage());
                List<Fixture> fallback = loadFromCache(cache);
                if (fallback != null && !fallback.isEmpty()) {
                    mainHandler.post(() -> listener.onFixturesLoaded(fallback));
                } else {
                    mainHandler.post(() -> listener.onError(e.getMessage()));
                }
            }
        });
    }

    private void fetchSoccerScoreboard(String urlStr, String leagueName, List<Fixture> out, Set<String> seenIds) {
        try {
            String jsonStr = httpGet(urlStr);
            if (jsonStr == null) return;
            JSONObject root = new JSONObject(jsonStr);
            JSONArray events = root.optJSONArray("events");
            if (events == null) return;

            long now = System.currentTimeMillis();

            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.getJSONObject(i);
                String id = ev.optString("id");
                if (seenIds.contains("soc_" + id)) continue;

                String name = ev.optString("name");
                String dateStr = ev.optString("date");

                JSONArray competitions = ev.optJSONArray("competitions");
                if (competitions == null || competitions.length() == 0) continue;
                JSONObject comp = competitions.getJSONObject(0);

                JSONObject status = comp.optJSONObject("status");
                JSONObject statusType = status != null ? status.optJSONObject("type") : null;
                String state = statusType != null ? statusType.optString("state", "pre") : "pre";
                boolean isLive = "in".equalsIgnoreCase(state);

                long ts = parseTimestamp(dateStr);
                // Filter out ended / finished matches
                if (!isLive && ("post".equalsIgnoreCase(state) || "completed".equalsIgnoreCase(state))) {
                    continue;
                }
                if (!isLive && ts > 0 && ts < (now - 3 * 3600 * 1000L)) {
                    continue;
                }

                // Parse competitors
                JSONArray competitors = comp.optJSONArray("competitors");
                String homeTeam = "";
                String awayTeam = "";
                if (competitors != null && competitors.length() >= 2) {
                    JSONObject c1 = competitors.getJSONObject(0);
                    JSONObject c2 = competitors.getJSONObject(1);
                    JSONObject t1 = c1.optJSONObject("team");
                    JSONObject t2 = c2.optJSONObject("team");
                    String n1 = t1 != null ? t1.optString("displayName", "") : "";
                    String n2 = t2 != null ? t2.optString("displayName", "") : "";
                    if ("home".equalsIgnoreCase(c1.optString("homeAway"))) {
                        homeTeam = n1;
                        awayTeam = n2;
                    } else {
                        homeTeam = n2;
                        awayTeam = n1;
                    }
                }

                String title = (!homeTeam.isEmpty() && !awayTeam.isEmpty())
                        ? (homeTeam + " vs " + awayTeam) : name;

                Fixture f = new Fixture();
                f.id = "soc_" + id;
                seenIds.add(f.id);

                f.sport = "SOCCER";
                f.competition = leagueName;
                f.title = title;
                f.isLive = isLive;
                f.timestamp = ts;
                f.statusText = isLive ? "LIVE" : formatDateTime(ts);
                f.searchQuery = title + " highlights";

                // Broadcaster mapping
                String lower = (title + " " + leagueName).toLowerCase(Locale.ROOT);
                if ("Indian Super League".equalsIgnoreCase(leagueName) || lower.contains("mohun bagan") || lower.contains("east bengal")) {
                    f.targetPackage = "com.jio.media.ondemand";
                    f.appLabel = "JioCinema / YouTube";
                } else if ("Premier League".equals(leagueName)) {
                    f.targetPackage = "in.startv.hotstar";
                    f.appLabel = "Hotstar";
                } else if ("Champions League".equals(leagueName)) {
                    f.targetPackage = "com.sonyliv";
                    f.appLabel = "Sony LIV";
                } else if ("La Liga".equals(leagueName)) {
                    f.targetPackage = "com.jio.media.jiotvplus";
                    f.appLabel = "JioTV+";
                } else if ("MLS".equals(leagueName) || containsIgnoreCase(title, "messi") || containsIgnoreCase(title, "inter miami")) {
                    f.targetPackage = "com.apple.atve.androidtv.appletv";
                    f.appLabel = "Apple TV";
                } else {
                    f.targetPackage = "com.google.android.youtube.tv";
                    f.appLabel = "YouTube";
                }

                // Priority ranking: Mohun Bagan, East Bengal, ISL are #1 priority
                int p = 50;
                if (lower.contains("mohun bagan") || lower.contains("east bengal")
                        || lower.contains("mbsg") || lower.contains("kolkata")
                        || "Indian Super League".equalsIgnoreCase(leagueName) || lower.contains("indian super league") || lower.contains("isl")) {
                    p = 5; // Top priority
                } else if (lower.contains("barcelona") || lower.contains("barca") || lower.contains("messi")
                        || lower.contains("inter miami") || lower.contains("real madrid") || lower.contains("arsenal")
                        || lower.contains("man city") || lower.contains("manchester city") || lower.contains("bayern")
                        || lower.contains("argentina")) {
                    p = 10;
                } else if ("Champions League".equals(leagueName)) {
                    p = 20;
                } else if ("Premier League".equals(leagueName) || "La Liga".equals(leagueName)) {
                    p = 25;
                }
                f.priority = p;

                out.add(f);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error fetching soccer " + leagueName + ": " + e.getMessage());
        }
    }

    private void fetchF1Scoreboard(String urlStr, List<Fixture> out, Set<String> seenIds) {
        try {
            String jsonStr = httpGet(urlStr);
            if (jsonStr == null) return;
            JSONObject root = new JSONObject(jsonStr);
            JSONArray events = root.optJSONArray("events");
            if (events == null) return;

            long now = System.currentTimeMillis();

            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.getJSONObject(i);
                String id = ev.optString("id");
                if (seenIds.contains("f1_" + id)) continue;

                String name = ev.optString("name");
                String dateStr = ev.optString("date");

                JSONObject status = ev.optJSONObject("status");
                JSONObject statusType = status != null ? status.optJSONObject("type") : null;
                String state = statusType != null ? statusType.optString("state", "pre") : "pre";
                boolean isLive = "in".equalsIgnoreCase(state);

                long ts = parseTimestamp(dateStr);
                if (!isLive && ("post".equalsIgnoreCase(state) || "completed".equalsIgnoreCase(state))) continue;
                if (!isLive && ts > 0 && ts < (now - 3 * 3600 * 1000L)) continue;

                Fixture f = new Fixture();
                f.id = "f1_" + id;
                seenIds.add(f.id);

                f.sport = "F1";
                f.competition = "Formula 1";
                f.title = name;
                f.isLive = isLive;
                f.timestamp = ts;
                f.statusText = isLive ? "LIVE" : formatDateTime(ts);
                f.targetPackage = "com.fizzysoftware.fancode";
                f.appLabel = "FanCode / YouTube";
                f.searchQuery = name + " f1 race highlights";
                f.priority = 22;
                out.add(f);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error fetching F1: " + e.getMessage());
        }
    }

    private void fetchTennisScoreboard(String urlStr, List<Fixture> out, Set<String> seenIds) {
        try {
            String jsonStr = httpGet(urlStr);
            if (jsonStr == null) return;
            JSONObject root = new JSONObject(jsonStr);
            JSONArray events = root.optJSONArray("events");
            if (events == null) return;

            long now = System.currentTimeMillis();

            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.getJSONObject(i);
                String id = ev.optString("id");
                if (seenIds.contains("ten_" + id)) continue;

                String name = ev.optString("name");
                String dateStr = ev.optString("date");

                JSONObject status = ev.optJSONObject("status");
                JSONObject statusType = status != null ? status.optJSONObject("type") : null;
                String state = statusType != null ? statusType.optString("state", "pre") : "pre";
                boolean isLive = "in".equalsIgnoreCase(state);

                long ts = parseTimestamp(dateStr);
                if (!isLive && ("post".equalsIgnoreCase(state) || "completed".equalsIgnoreCase(state))) continue;
                if (!isLive && ts > 0 && ts < (now - 3 * 3600 * 1000L)) continue;

                Fixture f = new Fixture();
                f.id = "ten_" + id;
                seenIds.add(f.id);

                f.sport = "TENNIS";
                f.competition = "ATP Tour / Grand Slam";
                f.title = name;
                f.isLive = isLive;
                f.timestamp = ts;
                f.statusText = isLive ? "LIVE" : formatDateTime(ts);

                String lower = name.toLowerCase(Locale.ROOT);
                boolean isSlam = lower.contains("wimbledon") || lower.contains("us open")
                        || lower.contains("australian") || lower.contains("roland garros") || lower.contains("french");

                if (lower.contains("wimbledon") || lower.contains("us open")) {
                    f.targetPackage = "in.startv.hotstar";
                    f.appLabel = "Hotstar";
                } else if (lower.contains("australian") || lower.contains("roland garros")) {
                    f.targetPackage = "com.sonyliv";
                    f.appLabel = "Sony LIV";
                } else {
                    f.targetPackage = "com.google.android.youtube.tv";
                    f.appLabel = "YouTube";
                }

                f.searchQuery = name + " tennis highlights";
                f.priority = isSlam ? 15 : 28;
                out.add(f);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error fetching tennis: " + e.getMessage());
        }
    }

    private void fetchCricketScorepanel(String urlStr, List<Fixture> out, Set<String> seenIds) {
        try {
            String jsonStr = httpGet(urlStr);
            if (jsonStr == null) return;
            JSONObject root = new JSONObject(jsonStr);
            JSONArray scores = root.optJSONArray("scores");
            if (scores == null) return;

            long now = System.currentTimeMillis();

            for (int s = 0; s < scores.length(); s++) {
                JSONObject sc = scores.getJSONObject(s);
                JSONArray events = sc.optJSONArray("events");
                if (events == null) continue;

                for (int i = 0; i < events.length(); i++) {
                    JSONObject ev = events.getJSONObject(i);
                    String id = ev.optString("id");
                    if (seenIds.contains("cric_" + id)) continue;

                    String name = ev.optString("name");
                    String dateStr = ev.optString("date");

                    JSONObject status = ev.optJSONObject("status");
                    JSONObject statusType = status != null ? status.optJSONObject("type") : null;
                    String state = statusType != null ? statusType.optString("state", "pre") : "pre";
                    boolean isLive = "in".equalsIgnoreCase(state);

                    long ts = parseTimestamp(dateStr);
                    if (!isLive && ("post".equalsIgnoreCase(state) || "completed".equalsIgnoreCase(state))) continue;
                    if (!isLive && ts > 0 && ts < (now - 4 * 3600 * 1000L)) continue;

                    Fixture f = new Fixture();
                    f.id = "cric_" + id;
                    seenIds.add(f.id);

                    f.sport = "CRICKET";
                    f.competition = "Cricket";
                    f.title = name;
                    f.isLive = isLive;
                    f.timestamp = ts;
                    f.statusText = isLive ? "LIVE" : formatDateTime(ts);
                    f.targetPackage = "in.startv.hotstar";
                    f.appLabel = "Hotstar";
                    f.searchQuery = name + " cricket highlights";

                    int p = 45;
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (lower.contains("india") || lower.contains("kohli") || lower.contains("rcb") || lower.contains("bengaluru")) {
                        p = 18;
                    }
                    f.priority = p;

                    out.add(f);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error fetching cricket: " + e.getMessage());
        }
    }

    private String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 CleanYourTV/1.0");
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "HTTP " + code + " for " + urlStr);
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "httpGet failed for " + urlStr + ": " + e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static long parseTimestamp(String isoDateStr) {
        if (isoDateStr == null || isoDateStr.isEmpty()) return 0;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mmXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = parser.parse(isoDateStr);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static String formatDateTime(long timestamp) {
        if (timestamp <= 0) return "Upcoming";
        long now = System.currentTimeMillis();

        Calendar matchCal = Calendar.getInstance();
        matchCal.setTimeInMillis(timestamp);

        Calendar nowCal = Calendar.getInstance();
        nowCal.setTimeInMillis(now);

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = timeFmt.format(new Date(timestamp));

        boolean isToday = matchCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
                && matchCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR);

        Calendar tomCal = Calendar.getInstance();
        tomCal.setTimeInMillis(now + 24 * 3600 * 1000L);
        boolean isTomorrow = matchCal.get(Calendar.YEAR) == tomCal.get(Calendar.YEAR)
                && matchCal.get(Calendar.DAY_OF_YEAR) == tomCal.get(Calendar.DAY_OF_YEAR);

        if (isToday) {
            return "Today " + timeStr;
        } else if (isTomorrow) {
            return "Tomorrow " + timeStr;
        } else {
            SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, d MMM HH:mm", Locale.getDefault());
            return dateFmt.format(new Date(timestamp));
        }
    }

    private static boolean containsIgnoreCase(String src, String what) {
        return src != null && what != null && src.toLowerCase(Locale.ROOT).contains(what.toLowerCase(Locale.ROOT));
    }

    private List<Fixture> loadFromCache(File file) {
        try {
            if (!file.exists()) return null;
            FileInputStream fis = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            fis.read(bytes);
            fis.close();
            String jsonStr = new String(bytes, StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(jsonStr);
            List<Fixture> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                list.add(Fixture.fromJson(array.getJSONObject(i)));
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveToCache(File file, List<Fixture> fixtures) {
        try {
            JSONArray array = new JSONArray();
            for (Fixture f : fixtures) {
                array.put(f.toJson());
            }
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(array.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception ignored) {
        }
    }
}
