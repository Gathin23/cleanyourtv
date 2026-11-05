package io.github.maxhayim.adblocking;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * The three APIs this app reads, with no Android types and no network, so the JVM tests cover them against the
 * documented responses: Pi-hole v6 (https://docs.pi-hole.net/api/), Pi-hole v5 (/admin/api.php) and AdGuard Home
 * (https://github.com/AdguardTeam/AdGuardHome/tree/master/openapi).
 */
final class Blocker {
    /** How long Pause stops blocking, in seconds. */
    static final int PAUSE_SECONDS = 300;

    enum Kind { PIHOLE, ADGUARD }

    /** Today's numbers and whether blocking is on. */
    static final class Stats {
        final long queries;
        final long blocked;
        final double percent;
        final boolean enabled;

        Stats(long queries, long blocked, double percent, boolean enabled) {
            this.queries = queries;
            this.blocked = blocked;
            this.percent = percent;
            this.enabled = enabled;
        }
    }

    private Blocker() {
    }

    // ---------------------------------------------------------------- Pi-hole v6

    static String piholeLoginBody(String password) {
        return "{\"password\":" + JSONObject.quote(password == null ? "" : password) + "}";
    }

    /** The session id from POST /api/auth, "" when the Pi-hole has no password, null when refused. */
    static String piholeSid(String json) throws JSONException {
        JSONObject s = new JSONObject(json).optJSONObject("session");
        if (s == null || !s.optBoolean("valid", false)) return null;
        String sid = s.optString("sid", "");
        return "null".equals(sid) ? "" : sid;
    }

    /** GET /api/stats/summary and GET /api/dns/blocking, together. */
    static Stats pihole6(String summary, String blocking) throws JSONException {
        JSONObject q = new JSONObject(summary).getJSONObject("queries");
        long total = q.optLong("total", 0), blocked = q.optLong("blocked", 0);
        double pct = q.has("percent_blocked") ? q.optDouble("percent_blocked", 0) : percent(blocked, total);
        boolean on = "enabled".equals(new JSONObject(blocking).optString("blocking", ""));
        return new Stats(total, blocked, pct, on);
    }

    static String piholePauseBody(boolean pause) {
        return "{\"blocking\":" + !pause + ",\"timer\":" + (pause ? String.valueOf(PAUSE_SECONDS) : "null") + "}";
    }

    // ---------------------------------------------------------------- Pi-hole v5

    static String pihole5Summary(String token) {
        return "/admin/api.php?summaryRaw&auth=" + urlEncode(token);
    }

    static String pihole5Pause(boolean pause, String token) {
        return "/admin/api.php?" + (pause ? "disable=" + PAUSE_SECONDS : "enable") + "&auth=" + urlEncode(token);
    }

    static Stats pihole5(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (!o.has("dns_queries_today")) throw new JSONException("not a Pi-hole v5 summary (wrong API token?)");
        long total = o.optLong("dns_queries_today", 0), blocked = o.optLong("ads_blocked_today", 0);
        return new Stats(total, blocked, o.optDouble("ads_percentage_today", percent(blocked, total)),
                "enabled".equals(o.optString("status", "")));
    }

    // ---------------------------------------------------------------- AdGuard Home

    static String basicAuth(String user, String password) {
        String raw = (user == null ? "" : user) + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** GET /control/stats and GET /control/status, together. */
    static Stats adguard(String stats, String status) throws JSONException {
        JSONObject s = new JSONObject(stats);
        long total = s.optLong("num_dns_queries", 0), blocked = s.optLong("num_blocked_filtering", 0);
        return new Stats(total, blocked, percent(blocked, total), new JSONObject(status).optBoolean("protection_enabled", false));
    }

    static String adguardPauseBody(boolean pause) {
        return "{\"enabled\":" + !pause + (pause ? ",\"duration\":" + PAUSE_SECONDS * 1000L : "") + "}";
    }

    // ---------------------------------------------------------------- wording

    static double percent(long part, long total) {
        return total <= 0 ? 0 : part * 100.0 / total;
    }

    static String count(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    static String pct(double p) {
        return String.format(Locale.ROOT, "%.1f %%", p);
    }

    static String problem(int code, Kind kind) {
        if (code == 401 || code == 403) return kind == Kind.ADGUARD ? "AdGuard Home refused the username or password."
                : "Pi-hole refused the password (use an app password if two-factor login is on).";
        if (code == 404) return "That address answers, but not as " + (kind == Kind.ADGUARD ? "AdGuard Home." : "Pi-hole.");
        if (code <= 0) return "Can't reach the server at that address.";
        return "The server answered with error " + code + ".";
    }

    /** "http://192.168.1.2" from what was typed: a scheme added when missing, trailing slashes and /admin dropped. */
    static String normalizeUrl(String typed) {
        if (typed == null) return "";
        String u = typed.trim();
        if (u.isEmpty()) return "";
        if (!u.contains("://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith("/admin")) u = u.substring(0, u.length() - 6);
        return u;
    }

    static boolean validUrl(String url) {
        return url.matches("https?://[^\\s/?#]+(:[0-9]{1,5})?(/[^\\s?#]*)?");
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }
}
