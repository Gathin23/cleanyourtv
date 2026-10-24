package io.github.maxhayim.manager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Files from the project's repository on GitHub, and nowhere else. Blocking: call it off the main thread. */
final class Net {
    static final String REPO = "https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/";
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private Net() {
    }

    /** A repository file by its path, or null when it can't be read. */
    static byte[] get(String path) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(REPO + path).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) return null;
                }
                return out.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String text(String path) {
        byte[] b = get(path);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }
}
