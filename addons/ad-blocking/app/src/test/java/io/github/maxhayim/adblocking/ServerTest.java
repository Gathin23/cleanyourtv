package io.github.maxhayim.adblocking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Whole rounds against stand-in servers: the requests each API expects, in order, and the session closed after. */
public class ServerTest {
    private static TinyServer.Answer ok(String body) {
        return new TinyServer.Answer(200, body);
    }

    @Test
    public void pihole6RoundClosesItsSession() throws Exception {
        try (TinyServer s = new TinyServer(r -> {
            switch (r.line) {
                case "POST /api/auth": return ok(BlockerTest.PH6_AUTH);
                case "GET /api/stats/summary": return ok(BlockerTest.PH6_SUMMARY);
                case "GET /api/dns/blocking": return ok(BlockerTest.PH6_BLOCKING);
                default: return ok("{}");
            }
        })) {
            Server.Result r = new Server(new Config(Blocker.Kind.PIHOLE, s.url(), "", "pw")).stats();
            assertNull(r.problem);
            assertEquals(7497, r.stats.queries);
            assertEquals("pw", new org.json.JSONObject(s.seen.get(0).body).getString("password"));
            assertEquals("vFA+EP4MQ5JJvJg+3Q2Jnw=", s.seen.get(1).headers.get("x-ftl-sid"));
            assertEquals("DELETE /api/auth", s.seen.get(s.seen.size() - 1).line);
        }
    }

    @Test
    public void pihole6Pause() throws Exception {
        try (TinyServer s = new TinyServer(r -> r.line.equals("POST /api/auth") ? ok(BlockerTest.PH6_AUTH)
                : r.line.equals("GET /api/stats/summary") ? ok(BlockerTest.PH6_SUMMARY) : ok(BlockerTest.PH6_BLOCKING))) {
            new Server(new Config(Blocker.Kind.PIHOLE, s.url(), "", "pw")).pause(true);
            assertEquals("POST /api/dns/blocking", s.seen.get(1).line);
            assertTrue(s.seen.get(1).body.contains("\"timer\":300"));
        }
    }

    @Test
    public void wrongPihole6Password() throws Exception {
        try (TinyServer s = new TinyServer(r -> new TinyServer.Answer(401, BlockerTest.PH6_AUTH_WRONG))) {
            Server.Result r = new Server(new Config(Blocker.Kind.PIHOLE, s.url(), "", "bad")).stats();
            assertNull(r.stats);
            assertTrue(r.problem.contains("password"));
        }
    }

    @Test
    public void pihole5WhenThereIsNoV6Api() throws Exception {
        try (TinyServer s = new TinyServer(r -> r.line.startsWith("GET /admin/api.php?summaryRaw")
                ? ok(BlockerTest.PH5_SUMMARY) : new TinyServer.Answer(404, "not found"))) {
            Server.Result r = new Server(new Config(Blocker.Kind.PIHOLE, s.url(), "", "token")).stats();
            assertNull(r.problem);
            assertEquals(15329, r.stats.queries);
            assertEquals("GET /admin/api.php?summaryRaw&auth=token", s.seen.get(1).line);
        }
    }

    @Test
    public void adguardWithBasicAuth() throws Exception {
        try (TinyServer s = new TinyServer(r -> r.line.equals("GET /control/stats") ? ok(BlockerTest.AGH_STATS)
                : r.line.equals("GET /control/status") ? ok(BlockerTest.AGH_STATUS) : ok("OK"))) {
            Server.Result r = new Server(new Config(Blocker.Kind.ADGUARD, s.url(), "admin", "pass")).pause(true);
            assertNull(r.problem);
            assertEquals("POST /control/protection", s.seen.get(0).line);
            assertEquals("Basic YWRtaW46cGFzcw==", s.seen.get(0).headers.get("authorization"));
            assertEquals(3000, r.stats.blocked);
        }
    }

    @Test
    public void unreachable() {
        Server.Result r = new Server(new Config(Blocker.Kind.ADGUARD, "http://127.0.0.1:1", "a", "b")).stats();
        assertTrue(r.problem.contains("Can't reach"));
    }
}
