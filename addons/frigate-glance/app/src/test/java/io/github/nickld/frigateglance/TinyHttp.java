package io.github.nickld.frigateglance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** A one-request-per-connection HTTP/1.1 server for tests, because com.sun.net.httpserver is not on the Android test classpath. */
final class TinyHttp implements AutoCloseable {
    static final class Request {
        String method, path, query;
        final Map<String, String> headers = new HashMap<>();
    }

    static final class Response {
        int code = 200;
        String body = "";
        final Map<String, String> headers = new HashMap<>();

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    interface Handler {
        Response handle(Request r);
    }

    private final ServerSocket server;
    private final Map<String, Handler> handlers = new HashMap<>();
    private final Thread thread;

    TinyHttp() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        thread = new Thread(this::loop, "TinyHttp");
        thread.setDaemon(true);
    }

    TinyHttp on(String path, Handler h) {
        handlers.put(path, h);
        return this;
    }

    TinyHttp start() {
        thread.start();
        return this;
    }

    String url() {
        return "http://127.0.0.1:" + server.getLocalPort();
    }

    private void loop() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                serve(s);
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
        String line = in.readLine();
        if (line == null) return;
        Request r = new Request();
        String[] parts = line.split(" ");
        r.method = parts[0];
        String target = parts[1];
        int q = target.indexOf('?');
        r.path = q < 0 ? target : target.substring(0, q);
        r.query = q < 0 ? null : target.substring(q + 1);
        int length = 0;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int c = line.indexOf(':');
            String name = line.substring(0, c).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(c + 1).trim();
            r.headers.put(name, value);
            if (name.equals("content-length")) length = Integer.parseInt(value);
        }
        for (int i = 0; i < length; i++) in.read();
        Handler h = handlers.get(r.path);
        Response resp = h == null ? new Response(404, "") : h.handle(r);
        byte[] body = resp.body.getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder("HTTP/1.1 " + resp.code + " X\r\n");
        for (Map.Entry<String, String> e : resp.headers.entrySet()) head.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\nConnection: close\r\n\r\n");
        OutputStream out = s.getOutputStream();
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
