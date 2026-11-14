package com.gathin.matchday;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class MatchParser {
    private MatchParser() { }

    static List<Match> parse(String json, LocalDate selectedDate, ZoneId zone) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray fixtures = root.optJSONArray("matches");
        if (fixtures == null) throw new JSONException("Missing matches array");

        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < fixtures.length(); i++) {
            JSONObject item = fixtures.optJSONObject(i);
            if (item == null) continue;
            Instant kickoff = parseInstant(item.optString("utcDate", null));
            if (kickoff == null || !kickoff.atZone(zone).toLocalDate().equals(selectedDate)) continue;

            JSONObject score = item.optJSONObject("score");
            JSONObject fullTime = score == null ? null : score.optJSONObject("fullTime");
            matches.add(new Match(kickoff, item.optString("status", "UNKNOWN"),
                    readTeam(item.optJSONObject("homeTeam")), readTeam(item.optJSONObject("awayTeam")),
                    numberOrNull(fullTime, "home"), numberOrNull(fullTime, "away")));
        }
        matches.sort(Comparator.comparing(match -> match.kickoff));
        return matches;
    }

    static List<Match> parseSportScore(String json, LocalDate selectedDate, ZoneId zone) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray fixtures = root.optJSONArray("matches");
        if (fixtures == null) throw new JSONException("Missing matches array");
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < fixtures.length(); i++) {
            JSONObject item = fixtures.optJSONObject(i);
            if (item == null) continue;
            Instant kickoff = parseInstant(item.optString("time", null));
            if (kickoff == null || !kickoff.atZone(zone).toLocalDate().equals(selectedDate)) continue;
            String homeLogo = nonEmpty(item.optString("home_logo", null));
            String awayLogo = nonEmpty(item.optString("away_logo", null));
            matches.add(new Match(kickoff, sportScoreStatus(item.optString("status", ""),
                    item.optString("status_text", "")),
                    sportScoreTeam(item.optString("home", null), homeLogo),
                    sportScoreTeam(item.optString("away", null), awayLogo),
                    integerOrNull(item.opt("home_score")), integerOrNull(item.opt("away_score"))));
        }
        matches.sort(Comparator.comparing(match -> match.kickoff));
        return matches;
    }

    static String[] utcCalendarDates(LocalDate localDate, ZoneId zone) {
        Instant start = localDate.atStartOfDay(zone).toInstant();
        Instant end = localDate.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
        LocalDate first = start.atZone(ZoneId.of("UTC")).toLocalDate();
        LocalDate last = end.atZone(ZoneId.of("UTC")).toLocalDate();
        return first.equals(last) ? new String[] { first.toString() }
                : new String[] { first.toString(), last.toString() };
    }

    static String sportScoreHttpError(int statusCode) {
        if (statusCode == 429) return "SportScore rate limit reached. Wait before refreshing.";
        if (statusCode == 401 || statusCode == 403) return "SportScore refused the request (HTTP " + statusCode + ").";
        return "SportScore returned HTTP " + statusCode + ".";
    }

    private static Match.Team sportScoreTeam(String name, String logo) {
        String label = nonEmpty(name);
        if (label == null) label = "Unknown team";
        int id = logo == null ? -1 : (logo.hashCode() & 0x7fffffff);
        return new Match.Team(id, label, null, logo);
    }

    private static Integer integerOrNull(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.valueOf((String) value); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static String sportScoreStatus(String status, String statusText) {
        String normalized = (status + " " + statusText).toLowerCase(Locale.ROOT);
        if (normalized.contains("postpon")) return "POSTPONED";
        if (normalized.contains("cancel")) return "CANCELLED";
        if (normalized.contains("suspend")) return "SUSPENDED";
        if (normalized.contains("award")) return "AWARDED";
        if (normalized.contains("half time") || normalized.contains("halftime") || normalized.contains("paused")) return "PAUSED";
        if (normalized.contains("live") || normalized.contains("in play") || normalized.contains("in_progress")) return "IN_PLAY";
        if (normalized.contains("finish") || normalized.equals("ft")) return "FINISHED";
        if (normalized.contains("upcoming") || normalized.contains("scheduled") || normalized.contains("not started")) return "SCHEDULED";
        return "UNKNOWN";
    }

    static String[] utcDateRange(LocalDate localDate, ZoneId zone) {
        Instant start = localDate.atStartOfDay(zone).toInstant();
        Instant lastIncluded = localDate.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
        LocalDate firstUtcDate = start.atZone(ZoneId.of("UTC")).toLocalDate();
        LocalDate lastUtcDate = lastIncluded.atZone(ZoneId.of("UTC")).toLocalDate();
        // football-data excludes dateTo, so advance past the last UTC date touched by this local day.
        return new String[] { firstUtcDate.toString(), lastUtcDate.plusDays(1).toString() };
    }

    static String httpError(int statusCode) {
        switch (statusCode) {
            case 401: return "The API key was rejected. Check it in Settings.";
            case 403: return "This key cannot access match data. Check your football-data.org plan.";
            case 429: return "The API rate limit was reached. Wait before refreshing.";
            default: return "football-data.org returned HTTP " + statusCode + ".";
        }
    }

    private static Match.Team readTeam(JSONObject team) {
        if (team == null) return new Match.Team(-1, "Unknown team", null, null);
        String name = nonEmpty(team.optString("name", null));
        if (name == null) name = "Unknown team";
        return new Match.Team(team.optInt("id", -1), name,
                nonEmpty(team.optString("shortName", null)), nonEmpty(team.optString("crest", null)));
    }

    private static Integer numberOrNull(JSONObject value, String key) {
        if (value == null) return null;
        Object raw = value.opt(key);
        return raw instanceof Number ? ((Number) raw).intValue() : null;
    }

    private static String nonEmpty(String value) {
        return value == null || value.isEmpty() || "null".equals(value) ? null : value;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
