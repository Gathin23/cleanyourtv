package io.github.justinnewman.bedtimelock;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Phone unlock through ntfy (https://ntfy.sh, or a self-hosted server): the lock posts a request to a private
 * channel the parents' phones subscribe to, with Unlock and Not now buttons. Each button posts back a one-time token
 * to a reply channel nobody subscribes to, which the lock reads every few seconds while the request waits, for at most
 * WAIT_MS. Nothing goes online at any other time.
 */
final class PhoneUnlock {
    interface Listener {
        /** true: Unlock was tapped; false: Not now. */
        void onAnswer(boolean unlock);

        /** The request could not be sent (no network, server down). */
        void onFailed();

        /** WAIT_MS passed with no answer. */
        void onExpired();
    }

    static final String SERVER = "https://ntfy.sh";
    /** How long a request waits for an answer, and how long before the next may go out. */
    static final long WAIT_MS = 5 * 60_000L;
    private static final long EVERY_MS = 3_000L;
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BYTES = 64 * 1024;
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    /** When the last request went out, for the wait before the next; kept for the process. */
    private static long lastAsk;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = this::poll;
    private final String channel;
    private final Listener listener;
    private String token;
    private long askedAt;

    PhoneUnlock(String channel, Listener listener) {
        this.channel = channel;
        this.listener = listener;
    }

    /** A new private channel name: "bedtime-" and 20 random letters and digits (about 103 bits). */
    static String newChannel() {
        SecureRandom random = new SecureRandom();
        StringBuilder name = new StringBuilder("bedtime-");
        for (int i = 0; i < 20; i++) name.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return name.toString();
    }

    static String replyChannel(String channel) {
        return channel + "-reply";
    }

    /** Milliseconds before another request may go out (the whole WAIT_MS after one, answered or not); 0 when one may. */
    static long cooldownLeft(long now) {
        long left = lastAsk + WAIT_MS - now;
        return lastAsk == 0 || left <= 0 ? 0 : left;
    }

    /** "4:59" for the countdown on the lock. */
    static String clock(long ms) {
        long seconds = (ms + 999) / 1000;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    boolean pending() {
        return token != null && System.currentTimeMillis() - askedAt < WAIT_MS;
    }

    /** Send the request; the answer comes to the listener. `text` is the notification's line, e.g. the time. */
    void ask(String text) {
        token = Pin.newSalt();
        askedAt = System.currentTimeMillis();
        lastAsk = askedAt;
        String sent = token;
        run(() -> {
            try {
                Map<String, String> headers = new TreeMap<>();
                headers.put("Title", "Bedtime Lock");
                headers.put("Priority", "high");
                headers.put("Tags", "lock");
                headers.put("Actions", actions(SERVER, replyChannel(channel), sent));
                post(SERVER + "/" + channel, text, headers);
                main.postDelayed(pollTask, EVERY_MS);
            } catch (IOException e) {
                main.post(() -> {
                    token = null;
                    lastAsk = 0;   // nothing reached the phones, so Back may try again at once
                    listener.onFailed();
                });
            }
        });
    }

    /** Keep reading answers while the request still waits (after the lock comes back on screen), or report it lapsed. */
    void resume() {
        main.removeCallbacks(pollTask);
        if (token != null) main.post(pollTask);
    }

    void stop() {
        main.removeCallbacks(pollTask);
    }

    /** No new requests; a courtesy note already queued still goes out. */
    void shutdown() {
        stop();
        io.shutdown();
    }

    /** A plain line to the parents' channel (the test, and "handled" after an answer). */
    void tell(String text) {
        run(() -> {
            try {
                Map<String, String> headers = new TreeMap<>();
                headers.put("Title", "Bedtime Lock");
                headers.put("Priority", "low");
                post(SERVER + "/" + channel, text, headers);
            } catch (IOException ignored) {
                // Only a courtesy note; the unlock itself is done.
            }
        });
    }

    private void poll() {
        if (!pending()) {
            if (token != null) {
                token = null;
                listener.onExpired();
            }
            return;
        }
        String wanted = token;
        long since = askedAt / 1000 - 5;
        run(() -> {
            String lines;
            try {
                lines = get(SERVER + "/" + replyChannel(channel) + "/json?poll=1&since=" + since);
            } catch (IOException e) {
                lines = "";
            }
            String answer = answerIn(lines, wanted);
            main.post(() -> {
                if (!wanted.equals(token)) return;   // a newer request, or this one already answered
                if (answer == null) {
                    main.postDelayed(pollTask, EVERY_MS);
                    return;
                }
                token = null;
                listener.onAnswer(answer.equals("unlock"));
            });
        });
    }

    private void run(Runnable job) {
        try {
            io.execute(job);
        } catch (RejectedExecutionException shutDown) {
            // The lock is gone.
        }
    }

    /**
     * The Actions header: two ntfy http buttons that post "unlock <token>" or "no <token>" to the reply channel and
     * clear the notification.
     */
    static String actions(String server, String reply, String token) {
        String url = server + "/" + reply;
        return "http, Unlock, " + url + ", body=unlock " + token + ", clear=true; "
                + "http, Not now, " + url + ", body=no " + token + ", clear=true";
    }

    /**
     * "unlock" or "no" when a line of ntfy's JSON stream carries that answer for `token`, else null. The token is hex,
     * so a plain match on the message field is exact.
     */
    static String answerIn(String lines, String token) {
        if (lines == null || token == null) return null;
        for (String line : lines.split("\n")) {
            if (line.contains("\"message\":\"unlock " + token + "\"")) return "unlock";
            if (line.contains("\"message\":\"no " + token + "\"")) return "no";
        }
        return null;
    }

    private static void post(String url, String body, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        } finally {
            c.disconnect();
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int n; (n = in.read(buffer)) != -1; ) {
                    if (out.size() + n > MAX_BYTES) throw new IOException("reply too large");
                    out.write(buffer, 0, n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }
}
