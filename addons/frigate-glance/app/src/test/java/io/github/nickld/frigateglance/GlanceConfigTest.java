package io.github.nickld.frigateglance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class GlanceConfigTest {
    private static final String REQUIRED =
            "\"mqtt\":\"mqtt://u:p@192.168.1.10:1883\",\"frigateUrl\":\"https://192.168.1.11:8971\"";
    private static final String FULL = "{" + REQUIRED
            + ",\"frigateUser\":\"viewer\",\"frigatePassword\":\"pw\",\"topicPrefix\":\"frigate\""
            + ",\"rtspUser\":\"r\",\"rtspPassword\":\"rp\",\"cameras\":{\"porch\":\"Front door\"}"
            + ",\"labels\":[\"person\"],\"severity\":\"alerts_and_detections\",\"corner\":\"top-left\""
            + ",\"dismissSeconds\":15,\"snoozeMinutes\":30,\"liveVideo\":false,\"boxes\":false"
            + ",\"dismissKey\":\"KEYCODE_MENU\",\"snoozeKey\":\"KEYCODE_INFO\"}";

    private static String requiredPlus(String extra) {
        return "{" + REQUIRED + "," + extra + "}";
    }

    @Test
    public void defaultsApplyWhenOnlyRequiredKeysGiven() {
        GlanceConfig c = GlanceConfig.parse("{" + REQUIRED + "}");
        assertEquals("frigate", c.topicPrefix);
        assertEquals("alerts", c.severity);
        assertEquals("top-right", c.corner);
        assertEquals(8, c.dismissSeconds);
        assertEquals(60, c.snoozeMinutes);
        assertTrue(c.liveVideo);
        assertTrue(c.boxes);
        assertTrue(c.cameras.isEmpty());
        assertTrue(c.labels.isEmpty());
        assertEquals("KEYCODE_DPAD_LEFT", c.dismissKey);
        assertEquals("KEYCODE_DPAD_RIGHT", c.snoozeKey);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingMqttIsRejected() {
        GlanceConfig.parse("{\"frigateUrl\":\"https://h:8971\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingFrigateUrlIsRejected() {
        GlanceConfig.parse("{\"mqtt\":\"mqtt://h:1883\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidJsonIsRejected() {
        GlanceConfig.parse("not json");
    }

    @Test(expected = IllegalArgumentException.class)
    public void backAsDismissKeyIsRejected() {
        GlanceConfig.parse(requiredPlus("\"dismissKey\":\"KEYCODE_BACK\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void okAsSnoozeKeyIsRejected() {
        GlanceConfig.parse(requiredPlus("\"snoozeKey\":\"KEYCODE_DPAD_CENTER\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void enterAsSnoozeKeyIsRejected() {
        GlanceConfig.parse(requiredPlus("\"snoozeKey\":\"KEYCODE_ENTER\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sameKeyTwiceIsRejected() {
        GlanceConfig.parse(requiredPlus("\"dismissKey\":\"KEYCODE_MENU\",\"snoozeKey\":\"KEYCODE_MENU\""));
    }

    @Test
    public void outOfRangeNumbersAreClamped() {
        GlanceConfig c = GlanceConfig.parse(requiredPlus("\"dismissSeconds\":999,\"snoozeMinutes\":1"));
        assertEquals(120, c.dismissSeconds);
        assertEquals(5, c.snoozeMinutes);
    }

    @Test
    public void unknownCornerFallsBackToTopRight() {
        assertEquals("top-right", GlanceConfig.parse(requiredPlus("\"corner\":\"middle\"")).corner);
    }

    @Test
    public void unknownSeverityFallsBackToAlerts() {
        assertEquals("alerts", GlanceConfig.parse(requiredPlus("\"severity\":\"everything\"")).severity);
    }

    @Test
    public void readsEveryKey() {
        GlanceConfig c = GlanceConfig.parse(FULL);
        assertEquals("viewer", c.frigateUser);
        assertEquals("Front door", c.cameras.get("porch"));
        assertTrue(c.labels.contains("person"));
        assertEquals("alerts_and_detections", c.severity);
        assertEquals("top-left", c.corner);
        assertEquals(15, c.dismissSeconds);
        assertEquals(30, c.snoozeMinutes);
        assertFalse(c.liveVideo);
        assertFalse(c.boxes);
        assertEquals("KEYCODE_MENU", c.dismissKey);
        assertEquals("rp", c.rtspPassword);
    }

    @Test
    public void roundTripsThroughJson() {
        GlanceConfig c = GlanceConfig.parse(FULL);
        assertEquals(c.toJson(), GlanceConfig.parse(c.toJson()).toJson());
    }

    @Test
    public void overridesFromSettingsWin() throws Exception {
        GlanceConfig c = GlanceConfig.parse(FULL)
                .withOverrides(new JSONObject("{\"liveVideo\":false,\"corner\":\"bottom-left\"}"));
        assertFalse(c.liveVideo);
        assertEquals("bottom-left", c.corner);
        assertEquals("viewer", c.frigateUser);
    }

    @Test(expected = IllegalArgumentException.class)
    public void overridesAreValidatedToo() throws Exception {
        GlanceConfig.parse(FULL).withOverrides(new JSONObject("{\"dismissKey\":\"KEYCODE_BACK\"}"));
    }

    @Test
    public void streamsMapCamerasToGo2rtcStreams() {
        GlanceConfig c = GlanceConfig.parse(requiredPlus("\"streams\":{\"porch\":\"porch_sub\"}"));
        assertEquals("porch_sub", c.streams.get("porch"));
        assertTrue(GlanceConfig.parse("{" + REQUIRED + "}").streams.isEmpty());
    }

    @Test
    public void streamsSurviveRoundTrip() {
        GlanceConfig c = GlanceConfig.parse(requiredPlus("\"streams\":{\"shop\":\"shop_sub\"}"));
        assertEquals("shop_sub", GlanceConfig.parse(c.toJson()).streams.get("shop"));
    }

    @Test
    public void frigateHostFromUrl() {
        assertEquals("192.168.1.11", GlanceConfig.parse("{" + REQUIRED + "}").frigateHost());
    }

    @Test
    public void mqttWithoutSchemeIsRejectedWithoutEchoingIt() {
        try {
            GlanceConfig.parse("{\"mqtt\":\"u:secretpw@192.168.1.10:1883\",\"frigateUrl\":\"https://h:8971\"}");
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertFalse(e.getMessage(), e.getMessage().contains("secretpw"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void mqttWithUnencodedCharactersIsRejected() {
        GlanceConfig.parse("{\"mqtt\":\"mqtt://u:p#w%@h:1883\",\"frigateUrl\":\"https://h:8971\"}");
    }

    @Test
    public void invalidJsonMessageDoesNotEchoTheFile() {
        try {
            GlanceConfig.parse("{\"frigatePassword\":\"secretpw\" oops}");
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertFalse(e.getMessage(), e.getMessage().contains("secretpw"));
        }
    }

    @Test
    public void cardScaleDefaultsTo100AndIsClamped() {
        assertEquals(100, GlanceConfig.parse("{" + REQUIRED + "}").cardScale);
        assertEquals(130, GlanceConfig.parse(requiredPlus("\"cardScale\":130")).cardScale);
        assertEquals(50, GlanceConfig.parse(requiredPlus("\"cardScale\":10")).cardScale);
        assertEquals(200, GlanceConfig.parse(requiredPlus("\"cardScale\":900")).cardScale);
    }

    @Test
    public void fullscreenIsOffByDefaultWithUpAsItsKey() {
        GlanceConfig c = GlanceConfig.parse("{" + REQUIRED + "}");
        assertFalse(c.fullscreen);
        assertEquals("KEYCODE_DPAD_UP", c.fullscreenKey);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fullscreenKeyMayNotRepeatAnotherCardKey() {
        GlanceConfig.parse(requiredPlus("\"fullscreen\":true,\"fullscreenKey\":\"KEYCODE_DPAD_LEFT\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fullscreenKeyMayNotBeOk() {
        GlanceConfig.parse(requiredPlus("\"fullscreenKey\":\"KEYCODE_DPAD_CENTER\""));
    }

    @Test
    public void holdIsOffForEveryKeyByDefault() {
        GlanceConfig c = GlanceConfig.parse("{" + REQUIRED + "}");
        assertFalse(c.dismissHold);
        assertFalse(c.snoozeHold);
        assertFalse(c.fullscreenHold);
        assertTrue(GlanceConfig.parse(requiredPlus("\"snoozeHold\":true")).snoozeHold);
    }
}
