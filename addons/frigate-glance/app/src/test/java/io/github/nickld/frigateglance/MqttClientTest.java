package io.github.nickld.frigateglance;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class MqttClientTest {
    private static final List<String> TOPICS = List.of("frigate/reviews", "frigate/events", "frigate/available");

    static final class Recorder implements MqttClient.Listener {
        boolean connected;
        final List<String> topics = new ArrayList<>();
        final List<byte[]> payloads = new ArrayList<>();
        Exception cause;

        @Override public void onConnected() { connected = true; }
        @Override public void onMessage(String topic, byte[] payload) { topics.add(topic); payloads.add(payload); }
        @Override public void onDisconnected(Exception c) { cause = c; }
    }

    private static final byte[] CONNACK_OK = {0x20, 0x02, 0x00, 0x00};
    private static final byte[] SUBACK = {(byte) 0x90, 0x05, 0x00, 0x01, 0x00, 0x00, 0x00};

    private static byte[] publish(String topic, byte[] payload) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] t = topic.getBytes(StandardCharsets.UTF_8);
        body.write(t.length >> 8);
        body.write(t.length & 0xff);
        body.writeBytes(t);
        body.writeBytes(payload);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30);
        out.writeBytes(MqttClient.remainingLength(body.size()));
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    private static Recorder runAgainst(byte[] brokerBytes, ByteArrayOutputStream sent, String url) {
        Recorder r = new Recorder();
        new MqttClient(url, "frigate-glance-test", TOPICS, r).session(new ByteArrayInputStream(brokerBytes), sent);
        return r;
    }

    @Test
    public void sendsConnectWithCredentialsAndSubscribes() {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        runAgainst(concat(CONNACK_OK, SUBACK), sent, "mqtt://u:p@broker:1883");
        byte[] b = sent.toByteArray();
        assertEquals(0x10, b[0] & 0xff);
        String s = new String(b, StandardCharsets.ISO_8859_1);
        assertTrue(s.contains("\u0000\u0004MQTT\u0004"));
        int flagsAt = s.indexOf("MQTT") + 5;
        assertEquals(0xC2, b[flagsAt] & 0xff);                 // user, password, clean session
        assertEquals(60, ((b[flagsAt + 1] & 0xff) << 8) | (b[flagsAt + 2] & 0xff));
        assertTrue(s.contains("frigate-glance-test"));
        assertTrue(s.contains("\u0000\u0001u\u0000\u0001p"));
        int sub = s.indexOf((char) 0x82);
        assertTrue("SUBSCRIBE sent after CONNECT", sub > 0);
        for (String t : TOPICS) assertTrue(s.indexOf(t, sub) > sub);
    }

    @Test
    public void noCredentialsWhenUrlHasNone() {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        runAgainst(concat(CONNACK_OK, SUBACK), sent, "mqtt://broker");
        byte[] b = sent.toByteArray();
        int flagsAt = new String(b, StandardCharsets.ISO_8859_1).indexOf("MQTT") + 5;
        assertEquals(0x02, b[flagsAt] & 0xff);
    }

    @Test
    public void deliversPublishPayload() {
        byte[] payload = "{\"type\":\"new\"}".getBytes(StandardCharsets.UTF_8);
        Recorder r = runAgainst(concat(CONNACK_OK, SUBACK, publish("frigate/reviews", payload)),
                new ByteArrayOutputStream(), "mqtt://broker:1883");
        assertTrue(r.connected);
        assertEquals(List.of("frigate/reviews"), r.topics);
        assertArrayEquals(payload, r.payloads.get(0));
        assertNotNull("stream end is a disconnect", r.cause);
    }

    @Test
    public void refusedConnackEndsWithDisconnected() {
        Recorder r = runAgainst(new byte[] {0x20, 0x02, 0x00, 0x05}, new ByteArrayOutputStream(), "mqtt://u:p@broker");
        assertTrue(!r.connected);
        assertTrue(r.cause.getMessage(), r.cause.getMessage().contains("not authorised"));
    }

    @Test
    public void badPasswordIsNamed() {
        Recorder r = runAgainst(new byte[] {0x20, 0x02, 0x00, 0x04}, new ByteArrayOutputStream(), "mqtt://u:p@broker");
        assertTrue(r.cause.getMessage(), r.cause.getMessage().contains("bad user name or password"));
    }

    @Test
    public void decodesMultiByteRemainingLength() {
        byte[] payload = new byte[300];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) ('a' + i % 26);
        Recorder r = runAgainst(concat(CONNACK_OK, SUBACK, publish("frigate/events", payload)),
                new ByteArrayOutputStream(), "mqtt://broker");
        assertArrayEquals(payload, r.payloads.get(0));
    }

    @Test
    public void qos1PublishSkipsPacketId() {
        byte[] t = "frigate/events".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);
        body.write(t.length);
        body.writeBytes(t);
        body.write(0);
        body.write(7);
        body.writeBytes("x".getBytes(StandardCharsets.UTF_8));
        byte[] pub = concat(new byte[] {0x32}, MqttClient.remainingLength(body.size()), body.toByteArray());
        Recorder r = runAgainst(concat(CONNACK_OK, SUBACK, pub), new ByteArrayOutputStream(), "mqtt://broker");
        assertEquals("x", new String(r.payloads.get(0), StandardCharsets.UTF_8));
    }

    @Test
    public void percentEncodedCredentialsAreDecoded() {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        runAgainst(concat(CONNACK_OK, SUBACK), sent, "mqtt://us%40r:p%3Ass@broker");
        String s = new String(sent.toByteArray(), StandardCharsets.ISO_8859_1);
        assertTrue(s.contains("us@r") && s.contains("p:ss"));
    }

    @Test
    public void backoffDoublesToSixtySeconds() {
        assertEquals(1000, MqttClient.backoffMs(0));
        assertEquals(8000, MqttClient.backoffMs(3));
        assertEquals(60_000, MqttClient.backoffMs(10));
        assertEquals(60_000, MqttClient.backoffMs(100));
    }

    @Test
    public void pingsDuringSteadyTrafficSoTheBrokerKeepsUs() {
        // Messages every 20 s never let a 30 s read time out; the client must still ping within the keep-alive.
        byte[] pub = publish("frigate/events", "x".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        long[] now = {0};
        MqttClient client = new MqttClient("mqtt://broker", "t", TOPICS, new Recorder(), () -> now[0] += 20_000);
        client.session(new ByteArrayInputStream(concat(CONNACK_OK, SUBACK, pub, pub, pub, pub)), sent);
        byte[] b = sent.toByteArray();
        boolean ping = false;
        for (int i = 0; i + 1 < b.length; i++) if ((b[i] & 0xff) == 0xC0 && b[i + 1] == 0) ping = true;
        assertTrue("PINGREQ sent while messages kept arriving", ping);
    }

    @Test
    public void badBrokerPacketEndsTheSessionInsteadOfCrashing() {
        byte[] broken = {0x30, 0x02, 0x00, 0x09};   // topic length 9 but no topic bytes
        Recorder r = runAgainst(concat(CONNACK_OK, SUBACK, broken), new ByteArrayOutputStream(), "mqtt://broker");
        assertNotNull(r.cause);
    }
}
