package io.github.maxhayim.smarthome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** The requests as Home Assistant's REST API expects them, against a stand-in server on this machine. */
public class HaClientTest {
    private ServerSocket server;
    private Thread loop;
    private final AtomicReference<String> auth = new AtomicReference<>(), body = new AtomicReference<>(),
            type = new AtomicReference<>();

    /** A one-request-at-a-time HTTP/1.1 stand-in: enough for HttpURLConnection, nothing more. */
    @Before
    public void start() throws Exception {
        server = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
        loop = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    in.readLine();
                    int length = 0;
                    String a = null, ct = null;
                    for (String h; (h = in.readLine()) != null && !h.isEmpty(); ) {
                        String lower = h.toLowerCase();
                        if (lower.startsWith("authorization:")) a = h.substring(14).trim();
                        if (lower.startsWith("content-type:")) ct = h.substring(13).trim();
                        if (lower.startsWith("content-length:")) length = Integer.parseInt(h.substring(15).trim());
                    }
                    char[] b = new char[length];
                    for (int n = 0; n < length; ) n += in.read(b, n, length - n);
                    auth.set(a);
                    type.set(ct);
                    body.set(new String(b));
                    boolean ok = "Bearer good".equals(a);
                    byte[] out = (ok ? HaTest.STATES : "{\"message\":\"401: Unauthorized\"}").getBytes(StandardCharsets.UTF_8);
                    OutputStream o = s.getOutputStream();
                    o.write(("HTTP/1.1 " + (ok ? "200 OK" : "401 Unauthorized") + "\r\nContent-Type: application/json\r\n"
                            + "Content-Length: " + out.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    o.write(out);
                    o.flush();
                } catch (Exception ignored) {
                    // Closed by stop(), or a test's dropped connection.
                }
            }
        });
        loop.start();
    }

    @After
    public void stop() throws Exception {
        server.close();
        loop.join(2000);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getLocalPort();
    }

    @Test
    public void readsStatesWithTheToken() throws Exception {
        HaClient.Reply r = new HaClient(base(), "good").get("/api/states");
        assertEquals(200, r.code);
        assertEquals("Bearer good", auth.get());
        assertEquals(13, Ha.parse(r.body).size());
    }

    @Test
    public void postsServiceCallsAsJson() {
        HaClient.Reply r = new HaClient(base(), "good").post("/api/services/scene/turn_on", "{\"entity_id\":\"scene.good_night\"}");
        assertTrue(r.ok());
        assertEquals("application/json", type.get());
        assertEquals("{\"entity_id\":\"scene.good_night\"}", body.get());
    }

    @Test
    public void aRefusedTokenIsReported() {
        HaClient.Reply r = new HaClient(base(), "bad").get("/api/states");
        assertEquals(401, r.code);
        assertTrue(Ha.problem(r.code).contains("token"));
    }

    @Test
    public void anUnreachableServerIsCodeZero() throws Exception {
        String gone = base();
        stop();
        assertEquals(0, new HaClient(gone, "good").get("/api/states").code);
    }


}
