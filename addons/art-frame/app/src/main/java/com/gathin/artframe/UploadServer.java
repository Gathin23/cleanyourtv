package com.gathin.artframe;

import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A small web page for sending pictures from a phone on the same home network. It runs only while the upload
 * screen is open, answers only addresses on the local network, and every request must carry the random code from
 * the QR on the TV. Each picture is posted as the raw request body and stored like any other.
 */
final class UploadServer {
    static final long MAX_BYTES = 60L << 20;

    interface Listener {
        void received(Artwork a);
        void failed(String name, String why);
    }

    private final Library library;
    private final File cacheDir;
    private final Listener listener;
    private final String token;
    /** Set by the upload screen: pictures shaped like the screen fill it. */
    double screenRatio = 16 / 9.0;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private ServerSocket socket;
    private Thread acceptor;

    UploadServer(Library library, File cacheDir, Listener listener) {
        this.library = library;
        this.cacheDir = cacheDir;
        this.listener = listener;
        byte[] b = new byte[9];
        new SecureRandom().nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        token = s.toString();
    }

    String token() {
        return token;
    }

    /** Starts on the first free port from 8790; returns the port. */
    int start() throws IOException {
        IOException last = null;
        for (int port = 8790; port < 8800; port++) {
            try {
                socket = new ServerSocket(port);
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (socket == null) throw last != null ? last : new IOException("no port");
        acceptor = new Thread(this::acceptLoop, "upload-server");
        acceptor.start();
        return socket.getLocalPort();
    }

    void stop() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // Closing anyway.
        }
        workers.shutdownNow();
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try {
                Socket s = socket.accept();
                InetAddress from = s.getInetAddress();
                if (!(from.isSiteLocalAddress() || from.isLinkLocalAddress() || from.isLoopbackAddress())) {
                    s.close();
                    continue;
                }
                s.setSoTimeout(30000);
                workers.execute(() -> serve(s));
            } catch (IOException e) {
                if (!socket.isClosed()) Log.w(Library.TAG, "upload accept: " + e);
            }
        }
    }

    private void serve(Socket s) {
        try (Socket sock = s; InputStream in = sock.getInputStream(); OutputStream out = sock.getOutputStream()) {
            String head = readHead(in);
            if (head == null) return;
            String[] lines = head.split("\r\n");
            String[] req = lines[0].split(" ");
            if (req.length < 2) return;
            String method = req[0];
            Uri uri = Uri.parse("http://tv" + req[1]);
            String path = uri.getPath() == null ? "" : uri.getPath();
            long length = -1;
            for (String l : lines) {
                int c = l.indexOf(':');
                if (c > 0 && l.substring(0, c).trim().equalsIgnoreCase("content-length")) {
                    try {
                        length = Long.parseLong(l.substring(c + 1).trim());
                    } catch (NumberFormatException ignored) {
                        length = -1;
                    }
                }
            }
            if ("GET".equals(method) && path.equals("/" + token)) {
                send(out, 200, "text/html; charset=utf-8", page().getBytes(StandardCharsets.UTF_8));
            } else if ("POST".equals(method) && path.equals("/" + token + "/upload")) {
                upload(in, out, length, uri.getQueryParameter("name"));
            } else {
                send(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Log.i(Library.TAG, "upload connection: " + e);
        }
    }

    private void upload(InputStream in, OutputStream out, long length, String name) throws IOException {
        String title = titleFrom(name);
        if (length <= 0 || length > MAX_BYTES) {
            send(out, 413, "application/json", "{\"ok\":false,\"error\":\"too large (60 MB at most)\"}".getBytes(StandardCharsets.UTF_8));
            listener.failed(title, "too large");
            return;
        }
        File tmp = File.createTempFile("art-up", ".img", cacheDir);
        try {
            Library.copy(new Bounded(in, length), tmp, MAX_BYTES);
            Artwork a = new Artwork();
            a.title = title;
            a.source = "upload";
            a.collections.add(Library.UPLOADS);
            Artwork done = library.addFile(tmp, a);
            if (Sources.suits(done.width, done.height, screenRatio)) {
                done.fill = true;
                library.update(done);
            }
            send(out, 200, "application/json",
                    ("{\"ok\":true,\"size\":\"" + done.width + "x" + done.height + "\"}").getBytes(StandardCharsets.UTF_8));
            listener.received(done);
        } catch (IOException | OutOfMemoryError e) {
            send(out, 415, "application/json", "{\"ok\":false,\"error\":\"not a picture the TV can read\"}".getBytes(StandardCharsets.UTF_8));
            listener.failed(title, String.valueOf(e.getMessage()));
        } finally {
            tmp.delete();
        }
    }

    static String titleFrom(String name) {
        if (name == null) return "";
        // Only the file's own name, never a path a browser might send.
        String s = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        s = s.replaceAll("[\\p{Cntrl}]", "").trim();
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        s = s.replace('_', ' ').trim();
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0;
        while (b.size() < 16384) {
            int c = in.read();
            if (c < 0) return null;
            b.write(c);
            // Looking for \r\n\r\n.
            state = (c == '\r') ? (state == 2 ? 3 : 1) : (c == '\n') ? (state == 1 ? 2 : state == 3 ? 4 : 0) : 0;
            if (state == 4) return b.toString("ISO-8859-1");
        }
        return null;
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : code == 404 ? "Not Found" : code == 413 ? "Payload Too Large" : "Unsupported";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    /** Reads exactly n bytes of the body, then reports end of stream. */
    private static final class Bounded extends InputStream {
        private final InputStream in;
        private long left;

        Bounded(InputStream in, long n) {
            this.in = in;
            this.left = n;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) return -1;
            int c = in.read();
            if (c >= 0) left--;
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            int n = in.read(b, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }
    }

    private String page() {
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>Art Frame</title><style>"
                + "body{font-family:-apple-system,system-ui,sans-serif;background:#111214;color:#edeae4;margin:0;padding:24px;max-width:560px}"
                + "h1{font-family:Georgia,serif;font-weight:normal;font-size:28px;margin:0 0 6px}"
                + "p{color:#9a968e;margin:0 0 20px}"
                + "label{display:block;background:#e0b354;color:#1a1712;text-align:center;padding:18px;border-radius:14px;font-size:19px;font-weight:600}"
                + "input{display:none}ul{list-style:none;padding:0;margin:20px 0 0}li{padding:10px 0;border-bottom:1px solid #2a2c31;display:flex;justify-content:space-between;gap:12px}"
                + "li span:last-child{color:#9a968e;white-space:nowrap}"
                + "</style></head><body><h1>Art Frame</h1><p>Pictures you choose are sent straight to the TV on your home network.</p>"
                + "<label>Choose pictures<input id=f type=file accept='image/*' multiple></label><ul id=l></ul><script>"
                + "const T='" + token + "';const f=document.getElementById('f'),l=document.getElementById('l');"
                + "f.onchange=async()=>{for(const x of f.files){const li=document.createElement('li');"
                + "const a=document.createElement('span');a.textContent=x.name;const b=document.createElement('span');b.textContent='sending…';"
                + "li.append(a,b);l.prepend(li);try{const r=await fetch('/'+T+'/upload?name='+encodeURIComponent(x.name),{method:'POST',body:x});"
                + "const j=await r.json();b.textContent=j.ok?'on the TV ('+j.size+')':j.error;}catch(e){b.textContent='failed';}}f.value='';};"
                + "</script></body></html>";
    }
}
