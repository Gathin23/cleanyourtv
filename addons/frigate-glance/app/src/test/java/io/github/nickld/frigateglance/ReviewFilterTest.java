package io.github.nickld.frigateglance;

import static io.github.nickld.frigateglance.Fixtures.reviewJson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReviewFilterTest {
    private static final long NOW = 1_000_000L;

    private static ReviewFilter filter(String extraJson) {
        String extra = extraJson.substring(1, extraJson.length() - 1);
        return new ReviewFilter(GlanceConfig.parse("{\"mqtt\":\"mqtt://h:1883\",\"frigateUrl\":\"https://h:8971\""
                + (extra.isEmpty() ? "" : "," + extra) + "}"));
    }

    private static Review review(String severity, String camera, String... objects) {
        return Review.parse(reviewJson("new", "r1", camera, severity, objects));
    }

    @Test
    public void alertShownByDefault() {
        assertTrue(filter("{}").shows(review("alert", "driveway", "person"), NOW, 0));
    }

    @Test
    public void detectionHiddenWhenAlertsOnly() {
        assertFalse(filter("{}").shows(review("detection", "driveway", "car"), NOW, 0));
    }

    @Test
    public void detectionShownWhenAlertsAndDetections() {
        assertTrue(filter("{\"severity\":\"alerts_and_detections\"}").shows(review("detection", "driveway", "car"), NOW, 0));
    }

    @Test
    public void cameraNotListedIsHidden() {
        assertFalse(filter("{\"cameras\":{\"porch\":\"Front door\"}}").shows(review("alert", "shop", "person"), NOW, 0));
        assertTrue(filter("{\"cameras\":{\"porch\":\"Front door\"}}").shows(review("alert", "porch", "person"), NOW, 0));
    }

    @Test
    public void labelFilterNeedsOneMatchingObject() {
        assertTrue(filter("{\"labels\":[\"person\"]}").shows(review("alert", "driveway", "car", "person"), NOW, 0));
        assertFalse(filter("{\"labels\":[\"person\"]}").shows(review("alert", "driveway", "car"), NOW, 0));
    }

    @Test
    public void snoozedHidesEverythingUntilItExpires() {
        assertFalse(filter("{}").shows(review("alert", "driveway", "person"), NOW, NOW + 1));
        assertTrue(filter("{}").shows(review("alert", "driveway", "person"), NOW, NOW));
    }

    @Test
    public void replayCameraCountsAsTheCameraItReplays() {
        assertTrue(filter("{\"cameras\":{\"shop\":\"Shop\"}}").shows(review("alert", "_replay_shop", "person"), NOW, 0));
        assertFalse(filter("{\"cameras\":{\"driveway\":\"Drive\"}}").shows(review("alert", "_replay_shop", "person"), NOW, 0));
    }

    @Test
    public void baseCameraOfReplay() {
        assertEquals("shop", ReviewFilter.baseCamera("_replay_shop"));
        assertEquals("shop", ReviewFilter.baseCamera("shop"));
    }

    @Test
    public void newCamerasLetsACameraOutsideTheListIn() {
        assertTrue(filter("{\"cameras\":{\"driveway\":\"D\"},\"newCameras\":true}").shows(review("alert", "backyard", "person"), NOW, 0));
        assertFalse(filter("{\"cameras\":{\"driveway\":\"D\"}}").shows(review("alert", "backyard", "person"), NOW, 0));
    }

    @Test
    public void cameraSwitchedOffStaysOffEvenWithNewCameras() {
        assertFalse(filter("{\"newCameras\":true,\"camerasOff\":[\"shop\"]}").shows(review("alert", "_replay_shop", "person"), NOW, 0));
    }
}
