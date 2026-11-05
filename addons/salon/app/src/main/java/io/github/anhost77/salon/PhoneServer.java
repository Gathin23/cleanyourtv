package io.github.anhost77.salon;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;

/**
 * The page the phone opens from the QR code, on the home network only, while Réglages > Depuis le téléphone is on
 * screen: send the home photo, type the TMDB token. Every request must carry the one-time code from the QR code;
 * the server stops when the screen closes or after {@link #LIFETIME_MS}, whichever comes first. Plain HTTP on the
 * local network, like a printer's page: nothing goes through the internet.
 */
final class PhoneServer {
    interface Listener {
        /** On a background thread: what the phone just did, in words for the TV screen. */
        void done(String what);
    }

    static final long LIFETIME_MS = 10 * 60_000L;
    private static final int MAX_BODY = 25 * 1024 * 1024;

    private final Context ctx;
    private final Listener listener;
    private final String code;
    private ServerSocket server;
    private volatile boolean stopped;

    PhoneServer(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        byte[] r = new byte[6];
        new SecureRandom().nextBytes(r);
        StringBuilder sb = new StringBuilder();
        for (byte b : r) sb.append("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".charAt((b & 0xFF) % 32));
        code = sb.toString();
    }

    /** Starts listening; returns the address to put in the QR code, or null without a home network. */
    String start() throws IOException {
        String ip = localIp();
        if (ip == null) return null;
        server = new ServerSocket(0, 8, InetAddress.getByName(ip));
        server.setSoTimeout(1000);
        long until = System.currentTimeMillis() + LIFETIME_MS;
        Thread t = new Thread(() -> {
            while (!stopped && System.currentTimeMillis() < until) {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(15_000);
                    handle(s);
                } catch (java.net.SocketTimeoutException tick) {
                    // Check the clock and the stop flag again.
                } catch (IOException e) {
                    if (stopped) break;
                }
            }
            stop();
        }, "phone-server");
        t.setDaemon(true);
        t.start();
        return "http://" + ip + ":" + server.getLocalPort() + "/?k=" + code;
    }

    void stop() {
        stopped = true;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    private void handle(Socket s) throws IOException {
        InputStream in = s.getInputStream();
        String line = readLine(in);
        if (line == null) return;
        String[] parts = line.split(" ");
        if (parts.length < 2) return;
        String method = parts[0], target = parts[1];
        int length = 0;
        for (String h; (h = readLine(in)) != null && !h.isEmpty(); ) {
            int colon = h.indexOf(':');
            if (colon > 0 && h.substring(0, colon).trim().equalsIgnoreCase("content-length")) {
                try {
                    length = Integer.parseInt(h.substring(colon + 1).trim());
                } catch (NumberFormatException ignored) {
                    length = -1;
                }
            }
        }
        OutputStream out = s.getOutputStream();
        String path = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
        if (!target.contains("k=" + code)) {
            reply(out, 403, "text/plain", "Code périmé : rescannez le QR code affiché sur la TV.");
            return;
        }
        if ("GET".equals(method) && "/".equals(path)) {
            reply(out, 200, "text/html", page());
            return;
        }
        if (!"POST".equals(method) || length < 0 || length > MAX_BODY) {
            reply(out, 400, "text/plain", "Demande refusée.");
            return;
        }
        byte[] body = new byte[length];
        for (int n = 0; n < length; ) {
            int r = in.read(body, n, length - n);
            if (r < 0) throw new IOException("cut");
            n += r;
        }
        if ("/photo".equals(path)) {
            boolean ok = HomePhoto.save(ctx, body);
            reply(out, ok ? 200 : 415, "text/plain", ok ? "Photo envoyée : elle apparaît sur l'accueil de la TV."
                    : "Ce fichier n'est pas une photo lisible (JPEG ou PNG).");
            if (ok) listener.done("Nouvelle photo reçue");
        } else if ("/key".equals(path)) {
            String key = new String(body, StandardCharsets.UTF_8).trim();
            Settings.setTmdbToken(key);
            reply(out, 200, "text/plain", key.isEmpty() ? "Clé effacée." : "Clé enregistrée.");
            listener.done(key.isEmpty() ? "Clé TMDB effacée" : "Clé TMDB enregistrée");
        } else {
            reply(out, 404, "text/plain", "Inconnu.");
        }
    }

    private String page() {
        String k = "?k=" + code;
        return "<!doctype html><html lang=fr><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'><title>Réglages de la TV</title>"
                + "<style>body{font:17px system-ui,sans-serif;margin:0;padding:20px;background:#0b0f16;color:#eee}"
                + "h1{font-size:22px}section{background:#18202c;border-radius:14px;padding:16px;margin:16px 0}"
                + "button,input{font:inherit;width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:0;"
                + "margin-top:10px}button{background:#fff;color:#000;font-weight:600}input{background:#0b0f16;color:#eee;"
                + "border:1px solid #445}p.m{color:#9ab;min-height:1.2em}</style></head><body>"
                + "<h1>Réglages de la TV</h1>"
                + "<section><b>Photo de l'accueil</b><p>Choisissez une photo : la TV la place au centre de l'accueil, "
                + "la même photo floutée derrière.</p><input type=file id=f accept='image/*'>"
                + "<button onclick=send()>Envoyer la photo</button><p class=m id=pm></p></section>"
                + "<section><b>Clé TMDB (films et séries)</b><p>Le « jeton d'accès en lecture » de votre compte "
                + "themoviedb.org (Paramètres &gt; API). Laisser vide pour l'effacer.</p>"
                + "<input id=t autocomplete=off placeholder='eyJhbGciOi…'>"
                + "<button onclick=key()>Enregistrer la clé</button><p class=m id=km></p></section>"
                + "<script>"
                + "async function post(u,b,m){m.textContent='Envoi…';try{let r=await fetch(u+'" + k + "',"
                + "{method:'POST',body:b});m.textContent=await r.text()}catch(e){m.textContent='Échec : la TV ne répond plus "
                + "(page fermée ?)'}}"
                + "function send(){let f=document.getElementById('f').files[0];if(f)post('/photo',f,"
                + "document.getElementById('pm'))}"
                + "function key(){post('/key',document.getElementById('t').value,document.getElementById('km'))}"
                + "</script></body></html>";
    }

    private static void reply(OutputStream out, int status, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String head = String.format(Locale.ROOT, "HTTP/1.1 %d %s\r\nContent-Type: %s; charset=utf-8\r\n"
                + "Content-Length: %d\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n", status,
                status == 200 ? "OK" : "Error", type, b.length);
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(b);
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int c; (c = in.read()) != -1; ) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) throw new IOException("line too long");
        }
        return b.toString("UTF-8");
    }

    /** The TV's address on the home network (Wi-Fi or Ethernet), or null. */
    static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.getName().startsWith("tun")) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // No network.
        }
        return null;
    }
}
