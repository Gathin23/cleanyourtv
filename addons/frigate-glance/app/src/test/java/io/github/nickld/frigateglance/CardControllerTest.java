package io.github.nickld.frigateglance;

import static io.github.nickld.frigateglance.Fixtures.reviewJson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class CardControllerTest {
    private static final float[] BOX = {0f, 0f, 0.5f, 0.5f};

    /** Records what the controller asks the card to do. */
    static final class FakeView implements CardView {
        final List<String> calls = new ArrayList<>();
        CardModel lastModel;

        @Override public void show(CardModel m) { calls.add("show:" + m.reviewId); lastModel = m; }
        @Override public void update(CardModel m) { calls.add("update:" + m.reviewId); lastModel = m; }
        @Override public void hide() { calls.add("hide"); }

        String last() { return calls.get(calls.size() - 1); }
    }

    private FakeView view;
    private long now;
    private CardController c;

    @Before
    public void setUp() {
        view = new FakeView();
        now = 1_000_000L;
        GlanceConfig config = GlanceConfig.parse("{\"mqtt\":\"mqtt://h:1883\",\"frigateUrl\":\"https://h:8971\"}");
        c = new CardController(view, new ReviewFilter(config), Map.of("frontdoooor", "Front door"), 8, () -> now);
    }

    private static Review rev(String type, String id, String camera, String severity) {
        return Review.parse(reviewJson(type, id, camera, severity, "person"));
    }

    private static Review revWithDetection(String id, String eventId) {
        String after = "{\"id\":\"" + id + "\",\"camera\":\"frontdoooor\",\"severity\":\"alert\",\"data\":{\"detections\":[\""
                + eventId + "\"],\"objects\":[\"person\"],\"zones\":[\"entire_front_yard\"]}}";
        return Review.parse("{\"type\":\"new\",\"after\":" + after + "}");
    }

    private static Detection det(String eventId, float[] box) {
        String b = box == null ? "null" : "[" + (box[0] * 100) + "," + (box[1] * 100) + "," + (box[2] * 100) + "," + (box[3] * 100) + "]";
        return Detection.parse("{\"type\":\"update\",\"after\":{\"id\":\"" + eventId + "\",\"camera\":\"frontdoooor\",\"label\":\"person\","
                + "\"sub_label\":[\"Nick\",0.9],\"score\":0.9,\"box\":" + b + "}}", Map.of("frontdoooor", new int[] {100, 100}));
    }

    @Test
    public void newAlertShowsCard() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        assertEquals(List.of("show:r1"), view.calls);
        assertTrue(c.isShowing());
    }

    @Test
    public void filteredReviewShowsNothing() {
        c.onReview(rev("new", "r1", "driveway", "detection"));
        assertTrue(view.calls.isEmpty());
    }

    @Test
    public void secondReviewReplacesFirst() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.onReview(rev("new", "r2", "driveway", "alert"));
        assertEquals("show:r2", view.last());
        assertTrue(c.isShowing());
    }

    @Test
    public void updateForShownCardRefreshesIt() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.onReview(rev("update", "r1", "frontdoooor", "alert"));
        assertEquals("update:r1", view.last());
    }

    @Test
    public void updateForOtherReviewIsIgnored() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.onReview(rev("update", "r9", "frontdoooor", "alert"));
        assertEquals(List.of("show:r1"), view.calls);
    }

    @Test
    public void endHidesAfterDismissSeconds() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.onReview(rev("end", "r1", "frontdoooor", "alert"));
        now += 7_999;
        c.tick();
        assertTrue(c.isShowing());
        now += 2;
        c.tick();
        assertFalse(c.isShowing());
        assertEquals("hide", view.last());
    }

    @Test
    public void missingEndHidesAfter120s() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        now += 119_999;
        c.tick();
        assertTrue(c.isShowing());
        now += 2;
        c.tick();
        assertFalse(c.isShowing());
    }

    @Test
    public void updateExtendsTheHardDeadline() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        now += 100_000;
        c.onReview(rev("update", "r1", "frontdoooor", "alert"));
        now += 100_000;
        c.tick();
        assertTrue(c.isShowing());
    }

    @Test
    public void detectionAddsBoxAndNameToShownCard() {
        c.onReview(revWithDetection("r1", "ev1"));
        c.onDetection(det("ev1", BOX));
        assertEquals(1, view.lastModel.boxes.size());
        assertEquals("Person · Nick · Entire Front Yard", view.lastModel.detail);
    }

    @Test
    public void detectionForOtherReviewIgnored() {
        c.onReview(revWithDetection("r1", "ev1"));
        c.onDetection(det("ev9", BOX));
        assertEquals(0, view.lastModel.boxes.size());
    }

    @Test
    public void detectionWithoutBoxStillUpdatesName() {
        c.onReview(revWithDetection("r1", "ev1"));
        c.onDetection(det("ev1", null));
        assertEquals(0, view.lastModel.boxes.size());
        assertEquals("Person · Nick · Entire Front Yard", view.lastModel.detail);
    }

    @Test
    public void dismissHides() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.dismiss();
        assertFalse(c.isShowing());
        assertEquals("hide", view.last());
    }

    @Test
    public void snoozeHidesAndBlocksNewCards() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.snooze(60);
        assertFalse(c.isShowing());
        assertEquals(now + 60 * 60_000L, c.snoozedUntil());
        c.onReview(rev("new", "r2", "frontdoooor", "alert"));
        assertFalse(c.isShowing());
        now += 60 * 60_000L;
        c.onReview(rev("new", "r3", "frontdoooor", "alert"));
        assertTrue(c.isShowing());
    }

    @Test
    public void resumeEndsSnooze() {
        c.snooze(60);
        c.resume();
        assertEquals(0, c.snoozedUntil());
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        assertTrue(c.isShowing());
    }

    @Test
    public void frigateOfflineHidesCard() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.onAvailable(false);
        assertFalse(c.isShowing());
    }

    @Test
    public void displayNameFromConfig() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        assertEquals("Front door", view.lastModel.title);
    }

    @Test
    public void prettyNameWithoutDisplayName() {
        c.onReview(rev("new", "r1", "front_door", "alert"));
        assertEquals("Front Door", view.lastModel.title);
    }

    @Test
    public void testCardShowsAndLeavesOnItsOwn() {
        c.showTest("driveway");
        assertEquals("show:test", view.last());
        assertEquals("Test card", view.lastModel.detail);
        assertEquals("driveway", view.lastModel.camera);
        now += 18_001;
        c.tick();
        assertFalse(c.isShowing());
    }

    @Test
    public void activeCardHasNoCountdown() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        assertEquals(0, view.lastModel.countdownMs);
        c.onReview(rev("update", "r1", "frontdoooor", "alert"));
        assertEquals(0, view.lastModel.countdownMs);
    }

    @Test
    public void endStartsCountdownOfDismissSeconds() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.onReview(rev("end", "r1", "frontdoooor", "alert"));
        assertEquals(8000, view.lastModel.countdownMs);
    }

    @Test
    public void detectionAfterEndKeepsTheCountdownRunning() {
        c.onReview(revWithDetection("r1", "ev1"));
        c.onReview(Review.parse("{\"type\":\"end\",\"after\":{\"id\":\"r1\",\"camera\":\"frontdoooor\",\"severity\":\"alert\",\"data\":{\"detections\":[\"ev1\"],\"objects\":[\"person\"]}}}"));
        now += 3000;
        c.onDetection(det("ev1", BOX));
        assertEquals(5000, view.lastModel.countdownMs);
    }

    @Test
    public void testCardCountsDownItsWholeLife() {
        c.showTest("driveway");
        assertEquals(18_000, view.lastModel.countdownMs);
    }

    @Test
    public void replayCardSaysReplay() {
        c.onReview(rev("new", "r1", "_replay_frontdoooor", "alert"));
        assertEquals("Front door (replay)", view.lastModel.title);
    }

    @Test
    public void updateForAnAlertNeverShownOpensACard() {
        // Connected mid-event (a reconnect, a reinstall): only updates arrive for it.
        c.onReview(rev("update", "r1", "frontdoooor", "alert"));
        assertEquals("show:r1", view.last());
    }

    @Test
    public void dismissedAlertDoesNotReopenOnUpdate() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.dismiss();
        c.onReview(rev("update", "r1", "frontdoooor", "alert"));
        assertFalse(c.isShowing());
    }

    @Test
    public void replacedAlertDoesNotStealTheCardBack() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.onReview(rev("new", "r2", "driveway", "alert"));
        c.onReview(rev("update", "r1", "frontdoooor", "alert"));
        assertEquals("show:r2", view.last());
    }

    @Test
    public void endedAlertDoesNotReopen() {
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        c.onReview(rev("end", "r1", "frontdoooor", "alert"));
        now += 9000;
        c.tick();
        c.onReview(rev("update", "r1", "frontdoooor", "alert"));
        assertFalse(c.isShowing());
    }

    @Test
    public void endedObjectLosesItsBox() {
        c.onReview(revWithDetection("r1", "ev1"));
        c.onDetection(det("ev1", BOX));
        assertEquals(1, view.lastModel.boxes.size());
        c.onDetection(Detection.parse("{\"type\":\"end\",\"after\":{\"id\":\"ev1\",\"camera\":\"frontdoooor\",\"label\":\"person\",\"box\":[0,0,50,50]}}",
                Map.of("frontdoooor", new int[] {100, 100})));
        assertEquals(0, view.lastModel.boxes.size());
    }

    @Test
    public void replayCardLeavesSoonAfterTheReplayGoesQuiet() {
        // Stopping a replay removes its camera with no "end": the card must not wait the full 120 s.
        c.onReview(Review.fromReplayEvent("{\"type\":\"new\",\"after\":{\"id\":\"e1\",\"camera\":\"_replay_shop\",\"label\":\"person\"}}"));
        assertTrue(c.isShowing());
        now += 10_000;
        c.onReview(Review.fromReplayEvent("{\"type\":\"update\",\"after\":{\"id\":\"e1\",\"camera\":\"_replay_shop\",\"label\":\"person\"}}"));
        now += 14_999;
        c.tick();
        assertTrue(c.isShowing());
        now += 2;
        c.tick();
        assertFalse(c.isShowing());
    }

    @Test
    public void shownCameraIsKnownForFullScreen() {
        assertEquals(null, c.shownCamera());
        c.onReview(rev("new", "r1", "frontdoooor", "alert"));
        assertEquals("frontdoooor", c.shownCamera());
        c.dismiss();
        assertEquals(null, c.shownCamera());
    }
}
