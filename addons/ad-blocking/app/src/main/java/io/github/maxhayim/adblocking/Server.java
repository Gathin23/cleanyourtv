package io.github.maxhayim.adblocking;

import org.json.JSONException;

/**
 * One round with the blocker: today's numbers, or pausing and resuming. Pi-hole is tried as v6 first (a session
 * that is closed again straight after, so it never holds one of Pi-hole's limited seats) and as v5 when the v6
 * API isn't there. Blocking: call it off the main thread.
 */
final class Server {
    /** Stats, or the problem to show. */
    static final class Result {
        final Blocker.Stats stats;
        final String problem;

        Result(Blocker.Stats stats, String problem) {
            this.stats = stats;
            this.problem = problem;
        }
    }

    private final Config c;

    Server(Config c) {
        this.c = c;
    }

    Result stats() {
        return c.kind == Blocker.Kind.ADGUARD ? adguard(null) : pihole(null);
    }

    /** Pauses (true) or resumes blocking, then reads the numbers again. */
    Result pause(boolean pause) {
        return c.kind == Blocker.Kind.ADGUARD ? adguard(pause) : pihole(pause);
    }

    private Result adguard(Boolean pause) {
        String auth = "Authorization: " + Blocker.basicAuth(c.user, c.password);
        if (pause != null) {
            Http.Reply r = Http.request("POST", c.url + "/control/protection", Blocker.adguardPauseBody(pause), auth);
            if (!r.ok()) return fail(r.code);
        }
        Http.Reply stats = Http.request("GET", c.url + "/control/stats", null, auth);
        if (!stats.ok()) return fail(stats.code);
        Http.Reply status = Http.request("GET", c.url + "/control/status", null, auth);
        if (!status.ok()) return fail(status.code);
        try {
            return new Result(Blocker.adguard(stats.body, status.body), null);
        } catch (JSONException e) {
            return new Result(null, "That address answers, but not as AdGuard Home.");
        }
    }

    private Result pihole(Boolean pause) {
        Http.Reply login = Http.request("POST", c.url + "/api/auth", Blocker.piholeLoginBody(c.password), null);
        if (login.code == 404) return pihole5(pause);
        String sid;
        try {
            sid = login.code == 401 ? null : Blocker.piholeSid(login.body);
        } catch (JSONException e) {
            return pihole5(pause);
        }
        if (sid == null) return fail(login.code == 0 ? 0 : 401);
        String header = sid.isEmpty() ? null : "X-FTL-SID: " + sid;
        try {
            if (pause != null) {
                Http.Reply r = Http.request("POST", c.url + "/api/dns/blocking", Blocker.piholePauseBody(pause), header);
                if (!r.ok()) return fail(r.code);
            }
            Http.Reply summary = Http.request("GET", c.url + "/api/stats/summary", null, header);
            if (!summary.ok()) return fail(summary.code);
            Http.Reply blocking = Http.request("GET", c.url + "/api/dns/blocking", null, header);
            if (!blocking.ok()) return fail(blocking.code);
            return new Result(Blocker.pihole6(summary.body, blocking.body), null);
        } catch (JSONException e) {
            return new Result(null, "That address answers, but not as Pi-hole.");
        } finally {
            if (header != null) Http.request("DELETE", c.url + "/api/auth", null, header);
        }
    }

    private Result pihole5(Boolean pause) {
        if (pause != null) {
            Http.Reply r = Http.request("GET", c.url + Blocker.pihole5Pause(pause, c.password), null, null);
            if (!r.ok()) return fail(r.code);
        }
        Http.Reply r = Http.request("GET", c.url + Blocker.pihole5Summary(c.password), null, null);
        if (!r.ok()) return fail(r.code);
        try {
            return new Result(Blocker.pihole5(r.body), null);
        } catch (JSONException e) {
            return new Result(null, "Pi-hole refused the API token (Settings, API in Pi-hole's web page).");
        }
    }

    private Result fail(int code) {
        return new Result(null, Blocker.problem(code, c.kind));
    }
}
