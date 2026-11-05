package io.github.maxhayim.adblocking;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** A one-request-at-a-time HTTP/1.1 stand-in for a blocker: enough for HttpURLConnection, nothing more. */
final class TinyServer implements AutoCloseable {
    /** What arrived: "METHOD /path", the headers, the body. */
    static final class Request {
        final String line;
        final Map<String, String> headers;
        final String body;

        Request(String line, Map<String, String> headers, String body) {
            this.line = line;
            this.headers = headers;
            this.body = body;
        }
    }

    /** Status code and body for a request. */
    static final class Answer {
        final int code;
        final String body;

        Answer(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    final List<Request> seen = Collections.synchronizedList(new ArrayList<>());
    private final ServerSocket socket;
    private final Thread loop;

    TinyServer(Function<Request, Answer> handler) throws Exception {
        socket = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
        loop = new Thread(() -> {
            while (!socket.isClosed()) {
                try (Socket s = socket.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String[] first = in.readLine().split(" ");
                    Map<String, String> h = new java.util.HashMap<>();
                    for (String l; (l = in.readLine()) != null && !l.isEmpty(); ) {
                        int c = l.indexOf(':');
                        h.put(l.substring(0, c).trim().toLowerCase(), l.substring(c + 1).trim());
                    }
                    int length = Integer.parseInt(h.getOrDefault("content-length", "0"));
                    char[] b = new char[length];
                    for (int n = 0; n < length; ) n += in.read(b, n, length - n);
                    Request r = new Request(first[0] + " " + first[1], h, new String(b));
                    seen.add(r);
                    Answer a = handler.apply(r);
                    byte[] out = a.body.getBytes(StandardCharsets.UTF_8);
                    OutputStream o = s.getOutputStream();
                    o.write(("HTTP/1.1 " + a.code + " X\r\nContent-Type: application/json\r\nContent-Length: " + out.length
                            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    o.write(out);
                    o.flush();
                } catch (Exception ignored) {
                    // Closed, or a dropped connection.
                }
            }
        });
        loop.start();
    }

    String url() {
        return "http://127.0.0.1:" + socket.getLocalPort();
    }

    @Override
    public void close() throws Exception {
        socket.close();
        loop.join(2000);
    }
}
