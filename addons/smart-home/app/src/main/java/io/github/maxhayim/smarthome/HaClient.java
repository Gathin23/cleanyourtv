package io.github.maxhayim.smarthome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Plain HTTP to Home Assistant with the owner's long-lived access token. Blocking: call it off the main thread. */
final class HaClient {
    /** Status code (0 when the server couldn't be reached) and the body. */
    static final class Reply {
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
    /** Larger answers are cut off: /api/states on a big installation is still well under this. */
    private static final int MAX_BODY = 4 * 1024 * 1024;

    private final String base;
    private final String token;

    HaClient(String base, String token) {
        this.base = base;
        this.token = token;
    }

    Reply get(String path) {
        return request("GET", path, null);
    }

    Reply post(String path, String body) {
        return request("POST", path, body);
    }

    private Reply request(String method, String path, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + path).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Accept", "application/json");
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
            byte[] buf = new byte[16384];
            for (int n; (n = s.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BODY) break;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
