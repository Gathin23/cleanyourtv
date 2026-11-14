package com.gathin.matchday;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

final class MatchRepository {
    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    static final int CACHE_VERSION = 2;
    private static final String COOLDOWN_PREFS = "matchday_network";
    private static final String NEXT_BATCH_ALLOWED_AT = "next_batch_allowed_at";
    private static final Object BATCH_LOCK = new Object();

    Result readCached(Context context, LocalDate day, ZoneId zone, Competition competition) {
        return readCached(context, day, zone, competition, DataProvider.FOOTBALL_DATA);
    }

    Result readCached(Context context, LocalDate day, ZoneId zone, Competition competition, DataProvider provider) {
        return readCache(cacheFile(context, day, competition, provider), day, zone, provider);
    }

    Result refresh(Context context, String apiKey, LocalDate day, ZoneId zone, Competition competition) {
        return refresh(context, apiKey, day, zone, competition, DataProvider.FOOTBALL_DATA);
    }

    Result refresh(Context context, String apiKey, LocalDate day, ZoneId zone, Competition competition,
                   DataProvider provider) {
        File cache = cacheFile(context, day, competition, provider);
        if (provider == DataProvider.SPORT_SCORE) return refreshSportScore(cache, day, zone, competition);
        String token = apiKey == null ? "" : apiKey.trim();
        if (token.isEmpty()) {
            Result saved = readCache(cache, day, zone);
            return saved == null ? Result.failure("Add your football-data.org API key in Settings.")
                    : saved.withError("Add an API key to refresh these saved results.");
        }

        HttpURLConnection connection = null;
        try {
            String[] range = MatchParser.utcDateRange(day, zone);
            String address = "https://api.football-data.org/v4/competitions/" + competition.code + "/matches"
                    + "?dateFrom=" + range[0] + "&dateTo=" + range[1];
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(CONNECT_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-Auth-Token", token);
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return withCacheOrError(cache, day, zone, MatchParser.httpError(code));
            byte[] bytes = readLimited(connection.getInputStream());
            String payload = new String(bytes, StandardCharsets.UTF_8);
            List<Match> matches = MatchParser.parse(payload, day, zone);
            long fetchedAt = System.currentTimeMillis();
            writeCache(cache, payload, fetchedAt);
            return new Result(matches, fetchedAt, false, null);
        } catch (IOException | JSONException | RuntimeException exception) {
            return withCacheOrError(cache, day, zone, "Could not reach or read football-data.org.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private Result refreshSportScore(File cache, LocalDate day, ZoneId zone, Competition competition) {
        HttpURLConnection connection = null;
        org.json.JSONArray combined = new org.json.JSONArray();
        try {
            for (String utcDate : MatchParser.utcCalendarDates(day, zone)) {
                String address = "https://sportscore.com/api/v1/fixtures/?sport=football&date=" + utcDate
                        + "&competition=" + competition.sportScoreSlug + "&limit=200";
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(CONNECT_TIMEOUT_MS);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    return withCacheOrError(cache, day, zone, DataProvider.SPORT_SCORE,
                            MatchParser.sportScoreHttpError(code));
                }
                String payload = new String(readLimited(connection.getInputStream()), StandardCharsets.UTF_8);
                JSONObject response = new JSONObject(payload);
                org.json.JSONArray fixtures = response.optJSONArray("matches");
                if (fixtures == null) throw new JSONException("Missing matches array");
                for (int i = 0; i < fixtures.length(); i++) combined.put(fixtures.get(i));
                connection.disconnect();
                connection = null;
            }
            JSONObject payload = new JSONObject().put("matches", combined);
            List<Match> matches = MatchParser.parseSportScore(payload.toString(), day, zone);
            long fetchedAt = System.currentTimeMillis();
            writeCache(cache, payload.toString(), fetchedAt);
            return new Result(matches, fetchedAt, false, null);
        } catch (IOException | JSONException | RuntimeException exception) {
            return withCacheOrError(cache, day, zone, DataProvider.SPORT_SCORE,
                    "Could not reach or read SportScore.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    boolean tryStartNetworkBatch(Context context) {
        synchronized (BATCH_LOCK) {
            long now = System.currentTimeMillis();
            android.content.SharedPreferences preferences = context.getSharedPreferences(
                    COOLDOWN_PREFS, Context.MODE_PRIVATE);
            long nextAllowedAt = preferences.getLong(NEXT_BATCH_ALLOWED_AT, 0L);
            if (RequestCooldown.isCoolingDown(now, nextAllowedAt)) return false;
            return preferences.edit()
                    .putLong(NEXT_BATCH_ALLOWED_AT, RequestCooldown.nextAllowedAt(now))
                    .commit();
        }
    }

    long networkCooldownRemaining(Context context) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                COOLDOWN_PREFS, Context.MODE_PRIVATE);
        return RequestCooldown.remainingMillis(System.currentTimeMillis(),
                preferences.getLong(NEXT_BATCH_ALLOWED_AT, 0L));
    }

    private static Result withCacheOrError(File file, LocalDate day, ZoneId zone, String error) {
        return withCacheOrError(file, day, zone, DataProvider.FOOTBALL_DATA, error);
    }

    private static Result withCacheOrError(File file, LocalDate day, ZoneId zone, DataProvider provider, String error) {
        Result saved = readCache(file, day, zone, provider);
        return saved == null ? Result.failure(error) : saved.withError(error);
    }

    private static File cacheFile(Context context, LocalDate day, Competition competition, DataProvider provider) {
        File directory = new File(context.getFilesDir(), "match-cache");
        if (!directory.exists()) directory.mkdirs();
        return new File(directory, cacheFileName(day, competition, provider));
    }

    static String cacheFileName(LocalDate day, Competition competition, DataProvider provider) {
        // Keep the original Premier League cache name so existing saved results remain usable.
        String prefix = competition == Competition.PREMIER_LEAGUE ? "" : competition.code + "-";
        if (provider == DataProvider.SPORT_SCORE) prefix = "sportscore-" + prefix;
        return prefix + day + ".json";
    }

    private static Result readCache(File file, LocalDate day, ZoneId zone) {
        return readCache(file, day, zone, DataProvider.FOOTBALL_DATA);
    }

    private static Result readCache(File file, LocalDate day, ZoneId zone, DataProvider provider) {
        if (!file.isFile()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = readLimited(input);
            return parseCache(new String(bytes, StandardCharsets.UTF_8), day, zone, provider);
        } catch (IOException | JSONException | RuntimeException ignored) {
            return null;
        }
    }

    static Result parseCache(String json, LocalDate day, ZoneId zone) throws JSONException {
        return parseCache(json, day, zone, DataProvider.FOOTBALL_DATA);
    }

    static Result parseCache(String json, LocalDate day, ZoneId zone, DataProvider provider) throws JSONException {
        JSONObject wrapper = new JSONObject(json);
        if (wrapper.optInt("version", 0) != CACHE_VERSION) return null;
        String payload = wrapper.getJSONObject("payload").toString();
        List<Match> matches = provider == DataProvider.SPORT_SCORE
                ? MatchParser.parseSportScore(payload, day, zone) : MatchParser.parse(payload, day, zone);
        return new Result(matches, wrapper.optLong("savedAt"), true, null);
    }

    private static void writeCache(File file, String payload, long savedAt) {
        File temporary = new File(file.getPath() + ".tmp");
        try {
            JSONObject wrapper = new JSONObject().put("version", CACHE_VERSION).put("savedAt", savedAt)
                    .put("payload", new JSONObject(payload));
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(wrapper.toString().getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.getFD().sync();
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                try {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException replaceFailure) {
                    replaceFailure.addSuppressed(atomicMoveFailure);
                    throw replaceFailure;
                }
            }
        } catch (IOException | JSONException ignored) {
            temporary.delete();
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) != -1) {
                if (output.size() + read > MAX_RESPONSE_BYTES) throw new IOException("Response too large");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    static final class Result {
        final List<Match> matches;
        final long updatedAt;
        final boolean fromCache;
        final String error;

        Result(List<Match> matches, long updatedAt, boolean fromCache, String error) {
            this.matches = matches;
            this.updatedAt = updatedAt;
            this.fromCache = fromCache;
            this.error = error;
        }

        static Result failure(String message) {
            return new Result(java.util.Collections.emptyList(), 0, false, message);
        }

        Result withError(String message) {
            return new Result(matches, updatedAt, true, message);
        }
    }
}
