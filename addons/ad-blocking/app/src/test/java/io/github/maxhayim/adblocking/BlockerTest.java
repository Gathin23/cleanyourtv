package io.github.maxhayim.adblocking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/** Against the response shapes the Pi-hole and AdGuard Home APIs document. */
public class BlockerTest {
    static final String PH6_AUTH = "{\"session\":{\"valid\":true,\"totp\":false,\"sid\":\"vFA+EP4MQ5JJvJg+3Q2Jnw=\",\"csrf\":\"Ux87YTIiMOf/GKCefVIOMw=\",\"validity\":300,\"message\":\"correct password\"},\"took\":0.003}";
    static final String PH6_AUTH_NO_PASSWORD = "{\"session\":{\"valid\":true,\"totp\":false,\"sid\":null,\"validity\":-1,\"message\":\"no password set\"},\"took\":0.001}";
    static final String PH6_AUTH_WRONG = "{\"session\":{\"valid\":false,\"totp\":false,\"sid\":null,\"validity\":-1,\"message\":\"password incorrect\"},\"took\":0.002}";
    static final String PH6_SUMMARY = "{\"queries\":{\"total\":7497,\"blocked\":3465,\"percent_blocked\":34.5,\"unique_domains\":445,\"forwarded\":4574,\"cached\":9765},\"clients\":{\"active\":10,\"total\":22},\"gravity\":{\"domains_being_blocked\":104603,\"last_update\":1725194639},\"took\":0.003}";
    static final String PH6_BLOCKING = "{\"blocking\":\"enabled\",\"timer\":null,\"took\":0.003}";
    static final String PH5_SUMMARY = "{\"domains_being_blocked\":121860,\"dns_queries_today\":15329,\"ads_blocked_today\":2213,\"ads_percentage_today\":14.436,\"unique_domains\":1543,\"status\":\"enabled\"}";
    static final String AGH_STATS = "{\"time_units\":\"hours\",\"num_dns_queries\":24000,\"num_blocked_filtering\":3000,\"num_replaced_safebrowsing\":0,\"avg_processing_time\":0.02}";
    static final String AGH_STATUS = "{\"version\":\"v0.107.52\",\"dns_port\":53,\"http_port\":3000,\"protection_enabled\":true,\"running\":true}";

    @Test
    public void piholeSessions() throws Exception {
        assertEquals("vFA+EP4MQ5JJvJg+3Q2Jnw=", Blocker.piholeSid(PH6_AUTH));
        assertEquals("no password: no session id needed", "", Blocker.piholeSid(PH6_AUTH_NO_PASSWORD));
        assertNull(Blocker.piholeSid(PH6_AUTH_WRONG));
        assertEquals("secret", new JSONObject(Blocker.piholeLoginBody("secret")).getString("password"));
    }

    @Test
    public void pihole6Numbers() throws Exception {
        Blocker.Stats s = Blocker.pihole6(PH6_SUMMARY, PH6_BLOCKING);
        assertEquals(7497, s.queries);
        assertEquals(3465, s.blocked);
        assertEquals(34.5, s.percent, 0.001);
        assertTrue(s.enabled);
        assertFalse(Blocker.pihole6(PH6_SUMMARY, "{\"blocking\":\"disabled\",\"timer\":120}").enabled);
    }

    @Test
    public void pihole6Pause() throws Exception {
        JSONObject p = new JSONObject(Blocker.piholePauseBody(true));
        assertFalse(p.getBoolean("blocking"));
        assertEquals(300, p.getInt("timer"));
        assertTrue(new JSONObject(Blocker.piholePauseBody(false)).getBoolean("blocking"));
    }

    @Test
    public void pihole5() throws Exception {
        Blocker.Stats s = Blocker.pihole5(PH5_SUMMARY);
        assertEquals(15329, s.queries);
        assertEquals(2213, s.blocked);
        assertTrue(s.enabled);
        assertEquals("/admin/api.php?summaryRaw&auth=a%2Bb", Blocker.pihole5Summary("a+b"));
        assertEquals("/admin/api.php?disable=300&auth=t", Blocker.pihole5Pause(true, "t"));
        assertEquals("/admin/api.php?enable&auth=t", Blocker.pihole5Pause(false, "t"));
    }

    @Test(expected = org.json.JSONException.class)
    public void pihole5WithoutTokenIsRefused() throws Exception {
        Blocker.pihole5("{}");
    }

    @Test
    public void adguard() throws Exception {
        Blocker.Stats s = Blocker.adguard(AGH_STATS, AGH_STATUS);
        assertEquals(24000, s.queries);
        assertEquals(3000, s.blocked);
        assertEquals(12.5, s.percent, 0.001);
        assertTrue(s.enabled);
        assertEquals("Basic YWRtaW46cGFzcw==", Blocker.basicAuth("admin", "pass"));
        JSONObject p = new JSONObject(Blocker.adguardPauseBody(true));
        assertFalse(p.getBoolean("enabled"));
        assertEquals(300000, p.getLong("duration"));
        assertFalse(new JSONObject(Blocker.adguardPauseBody(false)).has("duration"));
    }

    @Test
    public void wording() {
        assertEquals("12,345", Blocker.count(12345));
        assertEquals("34.5 %", Blocker.pct(34.5));
        assertEquals(0, Blocker.percent(5, 0), 0);
        assertTrue(Blocker.problem(401, Blocker.Kind.PIHOLE).contains("password"));
        assertTrue(Blocker.problem(0, Blocker.Kind.ADGUARD).contains("Can't reach"));
    }

    @Test
    public void addresses() {
        assertEquals("http://192.168.1.2", Blocker.normalizeUrl("192.168.1.2/admin/"));
        assertEquals("http://pi.hole:8080", Blocker.normalizeUrl("http://pi.hole:8080"));
        assertTrue(Blocker.validUrl("http://192.168.1.2:3000"));
        assertFalse(Blocker.validUrl("http://"));
    }
}
