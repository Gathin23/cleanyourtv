package io.github.nickld.frigateglance;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.net.SocketFactory;

/**
 * A subscribe-only MQTT 3.1.1 client: CONNECT, SUBSCRIBE at QoS 0, then PUBLISH messages in and PINGREQ out.
 * That is all Frigate's topics need, in a few KB instead of a client library. One run() is one session;
 * the caller reconnects, waiting backoffMs(attempt) in between.
 */
public final class MqttClient {
    public interface Listener {
        void onConnected();

        void onMessage(String topic, byte[] payload);

        void onDisconnected(Exception cause);
    }

    static final int KEEPALIVE_S = 60;
    static final int PING_EVERY_MS = 30_000;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final String[] REFUSALS = {
        "", "unacceptable protocol version", "identifier rejected", "server unavailable",
        "bad user name or password", "not authorised",
    };

    private final String host, user, password, clientId;
    private final int port;
    private final List<String> topics;
    private final Listener listener;
    private final java.util.function.LongSupplier clock;
    private long lastWrite;
    private volatile Socket socket;
    private volatile boolean stopped;

    /** url: mqtt://user:pass@host:port, user and password percent-encoded; port 1883 when left out. */
    public MqttClient(String url, String clientId, List<String> topics, Listener listener) {
        this(url, clientId, topics, listener, android.os.SystemClock::elapsedRealtime);
    }

    /** clock: milliseconds, for the keep-alive; tests pass their own. */
    MqttClient(String url, String clientId, List<String> topics, Listener listener, java.util.function.LongSupplier clock) {
        this.clock = clock;
        URI u = URI.create(url);
        host = u.getHost();
        port = u.getPort() > 0 ? u.getPort() : 1883;
        String info = u.getRawUserInfo();
        if (info == null) {
            user = null;
            password = null;
        } else {
            int c = info.indexOf(':');
            user = decode(c < 0 ? info : info.substring(0, c));
            password = c < 0 ? null : decode(info.substring(c + 1));
        }
        this.clientId = clientId;
        this.topics = topics;
        this.listener = listener;
    }

    /** One session over a socket. Blocks until it ends; always ends with onDisconnected. */
    public void run(SocketFactory factory) {
        try {
            Socket s = factory.createSocket();
            socket = s;
            if (stopped) throw new IOException("stopped");
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setSoTimeout(PING_EVERY_MS);
            session(s.getInputStream(), s.getOutputStream());
        } catch (IOException | RuntimeException e) {
            listener.onDisconnected(e instanceof IOException ? e : new IOException("bad broker address or packet", e));
        } finally {
            closeQuietly();
        }
    }

    public void stop() {
        stopped = true;
        closeQuietly();
    }

    /** Wait before reconnect attempt n (0 based): 1 s doubling to 60 s. */
    public static long backoffMs(int attempt) {
        return attempt >= 6 ? 60_000 : Math.min(60_000, 1000L << attempt);
    }

    /** The protocol, over any pair of streams. Reports the end through onDisconnected. */
    void session(InputStream rawIn, OutputStream out) {
        try {
            DataInputStream in = new DataInputStream(rawIn);
            out.write(connectPacket());
            out.flush();
            int[] header = readHeader(in, out);
            if (header[0] != 0x20 || header[1] != 2) throw new IOException("broker did not answer CONNECT");
            in.readUnsignedByte();
            int rc = in.readUnsignedByte();
            if (rc != 0) {
                throw new IOException("broker refused: " + (rc < REFUSALS.length ? REFUSALS[rc] : "code " + rc));
            }
            out.write(subscribePacket());
            out.flush();
            lastWrite = clock.getAsLong();
            listener.onConnected();
            while (!stopped) {
                header = readHeader(in, out);
                byte[] body = new byte[header[1]];
                in.readFully(body);
                if ((header[0] & 0xF0) == 0x30) deliver(header[0], body);
                // A steady stream of messages never lets the read time out, but the broker drops a client it
                // has not heard from for 1.5 keep-alives: ping on schedule whatever arrives.
                if (clock.getAsLong() - lastWrite >= PING_EVERY_MS) {
                    out.write(new byte[] {(byte) 0xC0, 0x00});
                    out.flush();
                    lastWrite = clock.getAsLong();
                }
            }
            throw new IOException("stopped");
        } catch (IOException e) {
            listener.onDisconnected(e);
        } catch (RuntimeException e) {
            listener.onDisconnected(new IOException("unreadable packet from the broker", e));
        }
    }

    private void deliver(int flags, byte[] body) {
        int topicLength = ((body[0] & 0xff) << 8) | (body[1] & 0xff);
        String topic = new String(body, 2, topicLength, StandardCharsets.UTF_8);
        int start = 2 + topicLength + (((flags >> 1) & 3) > 0 ? 2 : 0);
        byte[] payload = new byte[body.length - start];
        System.arraycopy(body, start, payload, 0, payload.length);
        listener.onMessage(topic, payload);
    }

    /** Packet type byte and remaining length. A read timeout sends PINGREQ; two in a row end the session. */
    private static int[] readHeader(DataInputStream in, OutputStream out) throws IOException {
        int first = -1;
        for (int quiet = 0; first < 0; ) {
            try {
                first = in.read();
                if (first < 0) throw new EOFException("broker closed the connection");
            } catch (SocketTimeoutException e) {
                if (++quiet > 1) throw new IOException("broker stopped answering");
                out.write(new byte[] {(byte) 0xC0, 0x00});
                out.flush();
            }
        }
        int length = 0;
        for (int shift = 0, b; ; shift += 7) {
            if (shift > 21) throw new IOException("bad remaining length");
            b = in.readUnsignedByte();
            length |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
        }
        return new int[] {first, length};
    }

    private byte[] connectPacket() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeString(body, "MQTT");
        body.write(4);
        int flags = 0x02;
        if (user != null) flags |= 0x80;
        if (password != null) flags |= 0x40;
        body.write(flags);
        body.write(KEEPALIVE_S >> 8);
        body.write(KEEPALIVE_S & 0xff);
        writeString(body, clientId);
        if (user != null) writeString(body, user);
        if (password != null) writeString(body, password);
        return packet(0x10, body.toByteArray());
    }

    private byte[] subscribePacket() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);
        body.write(1);
        for (String t : topics) {
            writeString(body, t);
            body.write(0);
        }
        return packet(0x82, body.toByteArray());
    }

    private static byte[] packet(int type, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(type);
        byte[] len = remainingLength(body.length);
        out.write(len, 0, len.length);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    static byte[] remainingLength(int n) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        do {
            int b = n % 128;
            n /= 128;
            out.write(n > 0 ? b | 0x80 : b);
        } while (n > 0);
        return out.toByteArray();
    }

    private static void writeString(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.write(b.length >> 8);
        out.write(b.length & 0xff);
        out.write(b, 0, b.length);
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    private void closeQuietly() {
        Socket s = socket;
        if (s == null) return;
        try {
            s.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
