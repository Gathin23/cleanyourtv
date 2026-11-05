package io.github.maxhayim.adblocking;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Plain HTTP to the blocker. Blocking: call it off the main thread. */
final class Http {
    static final class Reply {
        /** 0 when the server couldn't be reached. */
        final int code;
        final String body;

        Reply(int code, String body) {
            this.code = code;
            this.body = body;
        }

        boolean ok() {
            return code >= 200 && code < 300;
        }
    }

    private static final int TIMEOUT_MS = 6000;
    private static final int MAX_BODY = 1024 * 1024;

    private Http() {
    }

    /** header is "Name: value" or null. */
    static Reply request(String method, String url, String body, String header) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept", "application/json");
            if (header != null) {
                int colon = header.indexOf(':');
                c.setRequestProperty(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(b.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(b);
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Reply(code, in == null ? "" : read(in));
        } catch (IOException | RuntimeException e) {
            return new Reply(0, String.valueOf(e.getMessage()));
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = s.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BODY) break;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
