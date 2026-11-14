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
        return readCache(cacheFile(context, day, competition), day, zone);
    }

    Result refresh(Context context, String apiKey, LocalDate day, ZoneId zone, Competition competition) {
        File cache = cacheFile(context, day, competition);
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
        Result saved = readCache(file, day, zone);
        return saved == null ? Result.failure(error) : saved.withError(error);
    }

    private static File cacheFile(Context context, LocalDate day, Competition competition) {
        File directory = new File(context.getFilesDir(), "match-cache");
        if (!directory.exists()) directory.mkdirs();
        // Keep the original Premier League cache name so existing saved results remain usable.
        String prefix = competition == Competition.PREMIER_LEAGUE ? "" : competition.code + "-";
        return new File(directory, prefix + day + ".json");
    }

    private static Result readCache(File file, LocalDate day, ZoneId zone) {
        if (!file.isFile()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = readLimited(input);
            return parseCache(new String(bytes, StandardCharsets.UTF_8), day, zone);
        } catch (IOException | JSONException | RuntimeException ignored) {
            return null;
        }
    }

    static Result parseCache(String json, LocalDate day, ZoneId zone) throws JSONException {
        JSONObject wrapper = new JSONObject(json);
        if (wrapper.optInt("version", 0) != CACHE_VERSION) return null;
        String payload = wrapper.getJSONObject("payload").toString();
        return new Result(MatchParser.parse(payload, day, zone), wrapper.optLong("savedAt"), true, null);
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
