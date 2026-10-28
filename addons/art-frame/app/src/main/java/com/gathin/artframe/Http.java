package com.gathin.artframe;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Plain HTTPS GETs to the picture sources, with timeouts and size caps. No cookies, no logins. */
final class Http {
    static final String AGENT = "ArtFrame/1.0 (cleanyourtv add-on)";

    private Http() {}

    static JSONObject json(String url) throws IOException {
        return json(url, null);
    }

    /** With one extra request header ("Name: value"), e.g. an API key; null for none. */
    static JSONObject json(String url, String header) throws IOException {
        byte[] body = bytes(url, 4L << 20, header);
        try {
            return new JSONObject(new String(body, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException("bad JSON from " + new URL(url).getHost());
        }
    }

    static byte[] bytes(String url, long max) throws IOException {
        return bytes(url, max, null);
    }

    static byte[] bytes(String url, long max, String header) throws IOException {
        HttpURLConnection c = open(url, header);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[32 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > max) throw new IOException("response too large");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    static void download(String url, File dst, long max) throws IOException {
        HttpURLConnection c = open(url, null);
        try (InputStream in = c.getInputStream()) {
            Library.copy(in, dst, max);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String header) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("https only");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", AGENT);
        c.setRequestProperty("AIC-User-Agent", AGENT);
        if (header != null) {
            int colon = header.indexOf(':');
            c.setRequestProperty(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
        }
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw new IOException("HTTP " + code + " from " + u.getHost());
        }
        return c;
    }
}
