package io.github.nickld.frigateglance;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

public class FrigateApiTest {
    private static final String CONFIG = "{\"cameras\":{"
            + "\"driveway\":{\"detect\":{\"width\":1280,\"height\":720},\"live\":{\"streams\":{\"Main\":\"driveway_1\"}}},"
            + "\"shop\":{\"detect\":{\"width\":640,\"height\":480}}}}";

    private TinyHttp server;

    @After
    public void stop() throws IOException {
        if (server != null) server.close();
    }

    private static TinyHttp.Response loginOk(String token) {
        TinyHttp.Response r = new TinyHttp.Response(200, "{}");
        r.headers.put("Set-Cookie", "frigate_token=" + token + "; Path=/");
        return r;
    }

    @Test
    public void tokenFromSetCookie() {
        assertEquals("abc.def",
                FrigateApi.tokenFromSetCookie(List.of("other=1", "frigate_token=abc.def; Path=/; HttpOnly")));
        assertNull(FrigateApi.tokenFromSetCookie(List.of("other=1")));
    }

    @Test
    public void detectSizesFromConfig() throws Exception {
        assertArrayEquals(new int[] {1280, 720}, FrigateApi.detectSizes(new JSONObject(CONFIG)).get("driveway"));
        assertArrayEquals(new int[] {640, 480}, FrigateApi.detectSizes(new JSONObject(CONFIG)).get("shop"));
    }

    @Test
    public void liveStreamFromConfigOrCameraName() throws Exception {
        assertEquals("driveway_1", FrigateApi.liveStreams(new JSONObject(CONFIG)).get("driveway"));
        assertEquals("shop", FrigateApi.liveStreams(new JSONObject(CONFIG)).get("shop"));
    }

    @Test
    public void reLoginOnceOn401() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        AtomicInteger configCalls = new AtomicInteger();
        server = new TinyHttp()
                .on("/api/login", r -> loginOk("t" + logins.incrementAndGet()))
                .on("/api/config", r -> {
                    configCalls.incrementAndGet();
                    // The first token counts as expired: Frigate answers 401 until the second login.
                    String cookie = r.headers.get("cookie");
                    return cookie != null && cookie.contains("frigate_token=t2")
                            ? new TinyHttp.Response(200, CONFIG) : new TinyHttp.Response(401, "");
                })
                .start();
        FrigateApi api = new FrigateApi(server.url(), "viewer", "pw", "", pin -> {});
        assertTrue(api.config().has("cameras"));
        assertEquals(2, logins.get());
        assertEquals(2, configCalls.get());
    }

    @Test
    public void givesUpAfterSecond401() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        server = new TinyHttp()
                .on("/api/login", r -> loginOk("x" + logins.incrementAndGet()))
                .on("/api/config", r -> new TinyHttp.Response(401, ""))
                .start();
        FrigateApi api = new FrigateApi(server.url(), "viewer", "pw", "", pin -> {});
        try {
            api.config();
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("401"));
        }
        assertEquals(2, logins.get());
    }

    @Test
    public void badPasswordIsNamed() throws Exception {
        server = new TinyHttp().on("/api/login", r -> new TinyHttp.Response(401, "")).start();
        FrigateApi api = new FrigateApi(server.url(), "viewer", "bad", "", pin -> {});
        try {
            api.config();
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("username or password"));
        }
    }

    @Test
    public void noLoginWithoutUser() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        String[] query = new String[1];
        server = new TinyHttp()
                .on("/api/login", r -> { logins.incrementAndGet(); return new TinyHttp.Response(200, "{}"); })
                .on("/api/frontdoooor/latest.jpg", r -> { query[0] = r.query; return new TinyHttp.Response(200, "JPEG"); })
                .start();
        FrigateApi api = new FrigateApi(server.url(), "", "", "", pin -> {});
        assertEquals("JPEG", new String(api.latestJpeg("frontdoooor", 360), StandardCharsets.UTF_8));
        assertEquals("h=360", query[0]);
        assertEquals(0, logins.get());
    }

    @Test
    public void newCertificateIsRefusedUntilTrusted() throws Exception {
        List<String> saved = new java.util.ArrayList<>();
        FrigateApi api = new FrigateApi("https://frigate:8971", "", "", "old", saved::add);
        try {
            api.checkLeaf("new");
            fail("a different certificate was accepted");
        } catch (java.security.cert.CertificateException expected) {
            // refused, and offered for trusting
        }
        assertEquals("new", api.offeredPin());
        assertTrue(api.trustOffered());
        assertEquals("new", api.pin());
        assertEquals("", api.offeredPin());
        assertEquals(List.of("new"), saved);
        api.checkLeaf("new");
        assertEquals(false, api.trustOffered());
    }

    @Test
    public void firstCertificateIsPinned() throws Exception {
        List<String> saved = new java.util.ArrayList<>();
        FrigateApi api = new FrigateApi("https://frigate:8971", "", "", "", saved::add);
        api.checkLeaf("first");
        assertEquals("first", api.pin());
        assertEquals("", api.offeredPin());
        assertEquals(List.of("first"), saved);
    }
}
