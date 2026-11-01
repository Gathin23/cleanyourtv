package io.github.justinnewman.gameday;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FetcherTest {
    private static final long HOUR = 3_600_000L;
    private static final long NOW = 1_800_000_000_000L;
    private static final String ICS = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:20270403T000000Z\r\nSUMMARY:🏒 A @ B\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A one-route HTTP/1.0 server on a plain socket: the JDK's HttpServer is not on Android's test classpath. */
    private ServerSocket server;
    private Thread serving;
    private volatile int status = 200;
    private volatile String type = "text/calendar";
    private volatile byte[] body = ICS.getBytes(StandardCharsets.UTF_8);

    @Before public void start() throws IOException {
        server = new ServerSocket(0);
        serving = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
                    String request = in.readLine();
                    for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                        // headers: not needed
                    }
                    OutputStream o = s.getOutputStream();
                    // "/old" moved to "/cal.ics"; "/loop" moves to itself forever.
                    String path = request == null ? "" : request.split(" ")[1];
                    if (path.equals("/old") || path.equals("/loop")) {
                        String to = path.equals("/old") ? "/cal.ics" : "/loop";
                        o.write(("HTTP/1.0 301 Moved\r\nLocation: " + to + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                                .getBytes(StandardCharsets.ISO_8859_1));
                        o.flush();
                        continue;
                    }
                    o.write(("HTTP/1.0 " + status + " X\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    o.write(body);
                    o.flush();
                } catch (IOException closed) {
                    return;
                }
            }
        });
        serving.start();
    }

    @After public void stop() throws Exception {
        server.close();
        serving.join(2000);
    }

    private Calendar calendar() {
        String link = "http://127.0.0.1:" + server.getLocalPort() + "/cal.ics";
        return new Calendar(Links.id(link), "Wild", link, Sport.byLabel("Hockey"), 12);
    }

    @Test public void dueRules() {
        assertTrue(Fetcher.due(null, NOW));
        assertFalse(Fetcher.due(new FetchStatus(NOW - 23 * HOUR, 0, null, 5), NOW));
        assertTrue(Fetcher.due(new FetchStatus(NOW - 25 * HOUR, 0, null, 5), NOW));
        assertFalse(Fetcher.due(new FetchStatus(0, NOW - HOUR / 2, "No answer", 0), NOW));
        assertTrue(Fetcher.due(new FetchStatus(0, NOW - 2 * HOUR, "No answer", 0), NOW));
    }

    @Test public void goodFetchIsCached() throws Exception {
        File dir = tmp.newFolder();
        FetchStatus s = new Fetcher().fetch(calendar(), dir, null, NOW);
        assertNull(s.error);
        assertEquals(1, s.entries);
        assertEquals(NOW, s.lastSuccessMs);
        assertEquals(ICS, new String(Files.readAllBytes(Fetcher.cacheFile(dir, calendar().id).toPath()), StandardCharsets.UTF_8));
    }

    @Test public void htmlBodyIsReportedAsNotACalendar() throws Exception {
        type = "text/html";
        body = "<!DOCTYPE html><html>Sign in</html>".getBytes(StandardCharsets.UTF_8);
        File dir = tmp.newFolder();
        FetchStatus s = new Fetcher().fetch(calendar(), dir, null, NOW);
        assertEquals("Not a calendar link", s.error);
        assertEquals(NOW, s.lastFailureMs);
        assertFalse(Fetcher.cacheFile(dir, calendar().id).exists());
    }

    @Test public void failureKeepsLastGoodCopy() throws Exception {
        File dir = tmp.newFolder();
        Fetcher f = new Fetcher();
        FetchStatus good = f.fetch(calendar(), dir, null, NOW);
        status = 403;
        body = "nope".getBytes(StandardCharsets.UTF_8);
        FetchStatus bad = f.fetch(calendar(), dir, good, NOW + 25 * HOUR);
        assertEquals("Link refused (403)", bad.error);
        assertEquals(NOW, bad.lastSuccessMs);
        assertEquals(NOW + 25 * HOUR, bad.lastFailureMs);
        assertEquals(1, bad.entries);
        assertEquals(ICS, new String(Files.readAllBytes(Fetcher.cacheFile(dir, calendar().id).toPath()), StandardCharsets.UTF_8));
    }

    @Test public void oversizeIsRefused() throws Exception {
        byte[] big = new byte[Fetcher.MAX_BYTES + 1024 * 1024];
        byte[] head = ICS.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(head, 0, big, 0, head.length);
        body = big;
        FetchStatus s = new Fetcher().fetch(calendar(), tmp.newFolder(), null, NOW);
        assertEquals("Over 5 MB", s.error);
    }

    private Calendar at(String path) {
        String link = "http://127.0.0.1:" + server.getLocalPort() + path;
        return new Calendar(Links.id(link), "Wild", link, Sport.byLabel("Hockey"), 12);
    }

    @Test public void redirectIsFollowed() throws Exception {
        FetchStatus s = new Fetcher().fetch(at("/old"), tmp.newFolder(), null, NOW);
        assertNull(s.error);
        assertEquals(1, s.entries);
    }

    @Test public void redirectLoopGivesUp() throws Exception {
        FetchStatus s = new Fetcher().fetch(at("/loop"), tmp.newFolder(), null, NOW);
        assertEquals("Too many redirects", s.error);
    }

    @Test public void nextHopRules() throws Exception {
        java.net.URL http = new java.net.URL("http://a.example/x");
        java.net.URL https = new java.net.URL("https://a.example/x");
        assertEquals("https://b.example/y", Fetcher.nextHop(http, "https://b.example/y").toString());
        assertEquals("http://a.example/z", Fetcher.nextHop(http, "/z").toString());
        assertEquals("https://a.example/z", Fetcher.nextHop(https, "/z").toString());
        assertNull("never from https down to http", Fetcher.nextHop(https, "http://b.example/y"));
        assertNull(Fetcher.nextHop(https, "ftp://b.example/y"));
        assertNull(Fetcher.nextHop(https, null));
    }

    @Test public void noAnswerIsReported() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {
            String link = "http://127.0.0.1:" + silent.getLocalPort() + "/cal.ics";
            Calendar c = new Calendar(Links.id(link), "Wild", link, Sport.byLabel("Hockey"), 12);
            FetchStatus s = new Fetcher(500, 500).fetch(c, tmp.newFolder(), null, NOW);
            assertEquals("No answer", s.error);
        }
    }

    @Test public void statusRoundTrip() {
        FetchStatus s = new FetchStatus(NOW, NOW + 1, "Link refused (403)", 214);
        FetchStatus back = FetchStatus.fromJson(s.toJson());
        assertEquals(NOW, back.lastSuccessMs);
        assertEquals(NOW + 1, back.lastFailureMs);
        assertEquals("Link refused (403)", back.error);
        assertEquals(214, back.entries);
        assertNull(FetchStatus.fromJson("garbage"));
    }
}
