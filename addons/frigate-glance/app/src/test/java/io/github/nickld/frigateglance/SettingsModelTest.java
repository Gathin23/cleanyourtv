package io.github.nickld.frigateglance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class SettingsModelTest {
    private static final String FRIGATE = "{\"cameras\":{"
            + "\"driveway\":{\"review\":{\"alerts\":{\"labels\":[\"person\",\"car\"]}}},"
            + "\"shop\":{\"review\":{\"alerts\":{\"labels\":[\"person\"]}},\"objects\":{\"track\":[\"person\",\"dog\"]}},"
            + "\"frontdoooor\":{}}}";

    private static GlanceConfig config(String extra) {
        return GlanceConfig.parse("{\"mqtt\":\"mqtt://h\",\"frigateUrl\":\"https://h:8971\"" + extra + "}");
    }

    @Test
    public void replayCamerasGetNoSwitchOfTheirOwn() throws Exception {
        assertEquals(List.of("shop"), SettingsModel.cameraNames(new JSONObject("{\"cameras\":{\"shop\":{},\"_replay_shop\":{}}}")));
    }

    @Test
    public void camerasComeSortedFromFrigate() throws Exception {
        assertEquals(List.of("driveway", "frontdoooor", "shop"), SettingsModel.cameraNames(new JSONObject(FRIGATE)));
    }

    @Test
    public void labelsAreAlertLabelsAndTrackedObjects() throws Exception {
        assertEquals(List.of("car", "dog", "person"), SettingsModel.labelNames(new JSONObject(FRIGATE)));
    }

    @Test
    public void everyCameraIsOnWhenNoneAreListed() {
        assertTrue(SettingsModel.cameraOn(config(""), "shop"));
    }

    @Test
    public void turningACameraOffListsItAsOff() throws Exception {
        JSONObject ch = SettingsModel.toggleCamera(config(""), List.of("driveway", "frontdoooor", "shop"), "shop");
        assertEquals("[\"shop\"]", ch.getJSONArray("camerasOff").toString());
        assertFalse(SettingsModel.cameraOn(config(",\"camerasOff\":[\"shop\"]"), "shop"));
    }

    @Test
    public void turningItBackOnClearsIt() throws Exception {
        JSONObject ch = SettingsModel.toggleCamera(config(",\"camerasOff\":[\"shop\"]"), List.of("driveway", "shop"), "shop");
        assertEquals(0, ch.getJSONArray("camerasOff").length());
    }

    @Test
    public void aCameraMissingFromTheListIsAddedWithItsName() throws Exception {
        GlanceConfig c = config(",\"cameras\":{\"driveway\":\"Drive\"}");
        assertFalse(SettingsModel.cameraOn(c, "shop"));
        JSONObject cams = SettingsModel.toggleCamera(c, List.of("driveway", "shop"), "shop").getJSONObject("cameras");
        assertEquals("Drive", cams.getString("driveway"));
        assertEquals("Shop", cams.getString("shop"));
    }

    @Test
    public void newCamerasOnLetsUnlistedCamerasIn() {
        GlanceConfig c = config(",\"cameras\":{\"driveway\":\"Drive\"},\"newCameras\":true");
        assertTrue(SettingsModel.cameraOn(c, "backyard"));
        assertFalse(SettingsModel.cameraOn(config(",\"cameras\":{\"driveway\":\"Drive\"},\"newCameras\":true,\"camerasOff\":[\"backyard\"]"), "backyard"));
    }

    @Test
    public void labelToggleWorksTheSameWay() throws Exception {
        JSONArray l = SettingsModel.toggleLabel(config(""), List.of("car", "person"), "car");
        assertEquals("[\"person\"]", l.toString());
        GlanceConfig c = config(",\"labels\":[\"person\"]");
        assertEquals(0, SettingsModel.toggleLabel(c, List.of("car", "person"), "car").length());
    }

    @Test
    public void lastCameraCannotBeTurnedOff() throws Exception {
        GlanceConfig c = config(",\"cameras\":{\"shop\":\"Shop\"}");
        assertEquals(0, SettingsModel.toggleCamera(c, List.of("driveway", "shop"), "shop").length());
    }

    @Test
    public void cycleWrapsAround() {
        assertEquals("top-left", SettingsModel.next(List.of("top-right", "top-left"), "top-right"));
        assertEquals("top-right", SettingsModel.next(List.of("top-right", "top-left"), "top-left"));
        assertEquals("top-right", SettingsModel.next(List.of("top-right", "top-left"), "unknown"));
    }

    @Test
    public void durationsCycle() {
        assertEquals(Integer.valueOf(15), SettingsModel.next(SettingsModel.DISMISS_SECONDS, 8));
        assertEquals(Integer.valueOf(4), SettingsModel.next(SettingsModel.DISMISS_SECONDS, 30));
        assertEquals(Integer.valueOf(120), SettingsModel.next(SettingsModel.SNOOZE_MINUTES, 60));
        assertEquals(Integer.valueOf(15), SettingsModel.next(SettingsModel.SNOOZE_MINUTES, 240));
    }

    @Test
    public void cardSizeCycles() {
        assertEquals(Integer.valueOf(115), SettingsModel.next(SettingsModel.CARD_SCALES, 100));
        assertEquals(Integer.valueOf(70), SettingsModel.next(SettingsModel.CARD_SCALES, 150));
        assertEquals(Integer.valueOf(70), SettingsModel.next(SettingsModel.CARD_SCALES, 123));
    }
}
