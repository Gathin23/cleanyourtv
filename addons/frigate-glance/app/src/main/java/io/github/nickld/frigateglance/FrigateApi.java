package io.github.nickld.frigateglance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Frigate's HTTP API, as Peek uses it: log in on the authenticated port (8971) for a frigate_token cookie,
 * read /api/config, fetch a camera's latest frame. Frigate's certificate is usually self-signed, so it is
 * trusted on first contact and pinned (SHA-256 of the leaf certificate) from then on. Frigate makes itself a new
 * one when its container is recreated; that one is refused until it is trusted from the settings screen.
 */
public final class FrigateApi {
    public interface PinListener {
        void onFirstPin(String sha256);
    }

    private static final int TIMEOUT_MS = 10_000;

    private final String baseUrl, user, password;
    private final PinListener pinListener;
    private volatile String pin;
    /** A certificate seen in place of the pinned one, or "": what trustOffered() would pin. */
    private volatile String offered = "";
    private volatile String token;

    public FrigateApi(String baseUrl, String user, String password, String pinnedSha256, PinListener onFirstPin) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.user = user == null ? "" : user;
        this.password = password == null ? "" : password;
        this.pin = pinnedSha256 == null ? "" : pinnedSha256;
        this.pinListener = onFirstPin;
    }

    public JSONObject config() throws IOException {
        try {
            return new JSONObject(new String(get("/api/config"), StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException("Frigate sent an unreadable config: " + e.getMessage(), e);
        }
    }

    public byte[] latestJpeg(String camera, int height) throws IOException {
        return get("/api/" + URLEncoder.encode(camera, "UTF-8") + "/latest.jpg?h=" + height);
    }

    /** camera name to {width, height} of its detect stream; boxes in events are in these pixels. */
    public static Map<String, int[]> detectSizes(JSONObject config) {
        Map<String, int[]> out = new HashMap<>();
        JSONObject cams = config.optJSONObject("cameras");
        if (cams == null) return out;
        for (Iterator<String> it = cams.keys(); it.hasNext(); ) {
            String name = it.next();
            JSONObject cam = cams.optJSONObject(name);
            JSONObject detect = cam == null ? null : cam.optJSONObject("detect");
            if (detect == null) continue;
            int w = detect.optInt("width", 0), h = detect.optInt("height", 0);
            if (w > 0 && h > 0) out.put(name, new int[] {w, h});
        }
        return out;
    }

    /** camera name to its go2rtc stream: the first of cameras.<name>.live.streams, else the camera name. */
    public static Map<String, String> liveStreams(JSONObject config) {
        Map<String, String> out = new HashMap<>();
        JSONObject cams = config.optJSONObject("cameras");
        if (cams == null) return out;
        for (Iterator<String> it = cams.keys(); it.hasNext(); ) {
            String name = it.next();
            JSONObject cam = cams.optJSONObject(name);
            JSONObject live = cam == null ? null : cam.optJSONObject("live");
            JSONObject streams = live == null ? null : live.optJSONObject("streams");
            String stream = name;
            if (streams != null) {
                Iterator<String> keys = streams.keys();
                if (keys.hasNext()) stream = streams.optString(keys.next(), name);
            }
            out.put(name, stream);
        }
        return out;
    }

    static String tokenFromSetCookie(List<String> headers) {
        if (headers == null) return null;
        for (String h : headers) {
            for (String part : h.split(";")) {
                String p = part.trim();
                if (p.startsWith("frigate_token=")) return p.substring("frigate_token=".length());
            }
        }
        return null;
    }

    private byte[] get(String path) throws IOException {
        if (!user.isEmpty() && token == null) login();
        HttpURLConnection c = open(path);
        int code = c.getResponseCode();
        if (code == 401 && !user.isEmpty()) {
            c.disconnect();
            login();
            c = open(path);
            code = c.getResponseCode();
        }
        if (code != 200) {
            c.disconnect();
            throw new IOException("Frigate answered HTTP " + code + " for " + path.replaceFirst("\\?.*", ""));
        }
        try (InputStream in = c.getInputStream()) {
            return readAll(in);
        } finally {
            c.disconnect();
        }
    }

    private void login() throws IOException {
        token = null;
        HttpURLConnection c = connection("/api/login");
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] body;
        try {
            body = new JSONObject().put("user", user).put("password", password).toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        try (OutputStream out = c.getOutputStream()) {
            out.write(body);
        }
        int code = c.getResponseCode();
        String t = null;
        for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals("set-cookie")) t = tokenFromSetCookie(e.getValue());
            if (t != null) break;
        }
        c.disconnect();
        if (code == 401 || code == 400) throw new IOException("Frigate refused the username or password");
        if (code != 200 || t == null) throw new IOException("Frigate login failed (HTTP " + code + ")");
        token = t;
    }

    private HttpURLConnection open(String path) throws IOException {
        HttpURLConnection c = connection(path);
        if (token != null) c.setRequestProperty("Cookie", "frigate_token=" + token);
        return c;
    }

    private HttpURLConnection connection(String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setUseCaches(false);
        if (c instanceof HttpsURLConnection) {
            HttpsURLConnection https = (HttpsURLConnection) c;
            https.setSSLSocketFactory(pinnedFactory());
            // The pin is checked in the trust manager; the certificate rarely names the IP Frigate is reached on.
            https.setHostnameVerifier((host, session) -> true);
        }
        return c;
    }

    private SSLSocketFactory pinnedFactory() throws IOException {
        X509TrustManager tm = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("not a server");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain == null || chain.length == 0) throw new CertificateException("no certificate");
                checkLeaf(sha256(chain[0].getEncoded()));
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] {tm}, null);
            return ctx.getSocketFactory();
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /** Trusts on first contact, then only the pinned certificate; a different one is kept as the offer. */
    void checkLeaf(String seen) throws CertificateException {
        if (pin.isEmpty()) {
            pin = seen;
            pinListener.onFirstPin(seen);
        } else if (!pin.equals(seen)) {
            offered = seen;
            throw new CertificateException("New certificate");
        } else {
            offered = "";
        }
    }

    /** The pinned certificate's SHA-256, or "" before first contact. */
    public String pin() {
        return pin;
    }

    /** The certificate Frigate presented in place of the pinned one, or "" when there is none. */
    public String offeredPin() {
        return offered;
    }

    /** Pins the certificate Frigate now presents; false when none was offered. */
    public boolean trustOffered() {
        String o = offered;
        if (o.isEmpty()) return false;
        pin = o;
        offered = "";
        pinListener.onFirstPin(o);
        return true;
    }

    static String sha256(byte[] der) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(der);
            StringBuilder s = new StringBuilder();
            for (byte b : d) s.append(String.format(Locale.ROOT, "%02x", b));
            return s.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
