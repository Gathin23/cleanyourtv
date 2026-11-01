package io.github.nickld.frigateglance;

import static io.github.nickld.frigateglance.Fixtures.fixture;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ReviewTest {
    @Test
    public void parsesNewAlert() {
        Review r = Review.parse(fixture("review_new.json"));
        assertEquals("new", r.type);
        assertEquals("1761100000.1-abc", r.id);
        assertEquals("porch", r.camera);
        assertEquals("alert", r.severity);
        assertEquals(List.of("person"), r.objects);
        assertEquals(List.of("front_yard"), r.zones);
        assertEquals(List.of("1761100000.0-ev1"), r.detectionIds);
    }

    @Test
    public void updateCarriesNewObjectsAndDetections() {
        Review r = Review.parse(fixture("review_update.json"));
        assertEquals("update", r.type);
        assertEquals(List.of("person", "car"), r.objects);
        assertEquals(2, r.detectionIds.size());
    }

    @Test
    public void verifiedObjectReadsAsItsLabelAndCarriesItsName() {
        Review r = Review.parse("{\"type\":\"update\",\"after\":{\"id\":\"r1\",\"camera\":\"porch\",\"severity\":\"alert\","
                + "\"data\":{\"objects\":[\"person-verified\",\"person\",\"car\"],\"sub_labels\":[\"Alex\"]}}}");
        assertEquals(List.of("person", "car"), r.objects);
        assertEquals(List.of("Alex"), r.subLabels);
    }

    @Test
    public void parsesEnd() {
        assertEquals("end", Review.parse(fixture("review_end.json")).type);
    }

    @Test
    public void malformedReviewIsNull() {
        assertNull(Review.parse("{\"type\":\"new\"}"));
        assertNull(Review.parse("not json"));
        assertNull(Review.parse("{\"type\":\"bogus\",\"after\":{\"id\":\"x\",\"camera\":\"c\",\"severity\":\"alert\"}}"));
    }

    @Test
    public void boxIsNormalisedByDetectSize() {
        Detection d = Detection.parse(fixture("event_update.json"), Map.of("driveway", new int[] {1280, 720}));
        assertArrayEquals(new float[] {0.5f, 0.25f, 0.75f, 1.0f}, d.box, 1e-4f);
        assertEquals("1761100000.0-ev1", d.id);
        assertEquals("car", d.label);
        assertEquals("ABC1234", d.plate);
        assertEquals(0.81, d.score, 1e-9);
        assertEquals(List.of("entiredriveway"), d.zones);
        assertNull(d.subLabel);
    }

    @Test
    public void unknownDetectSizeMeansNoBox() {
        assertNull(Detection.parse(fixture("event_update.json"), Map.of()).box);
    }

    @Test
    public void subLabelArrayTakesFirstElement() {
        assertEquals("Alex", Detection.parse(fixture("event_sublabel_array.json"), Map.of()).subLabel);
    }

    @Test
    public void malformedEventIsNull() {
        assertNull(Detection.parse("{}", Map.of()));
        assertNull(Detection.parse("[]", Map.of()));
    }

    @Test
    public void replayEventStandsInForAReview() {
        Review r = Review.fromReplayEvent(fixture("event_replay_new.json"));
        assertEquals("new", r.type);
        assertEquals("_replay_shop", r.camera);
        assertEquals("alert", r.severity);
        assertEquals(List.of("person"), r.objects);
        assertEquals(List.of("1761110000.5-xyz"), r.detectionIds);
        assertEquals("replay-1761110000.5-xyz", r.id);
    }

    @Test
    public void onlyReplayCameraEventsStandIn() {
        assertNull(Review.fromReplayEvent(fixture("event_update.json")));
        assertNull(Review.fromReplayEvent("not json"));
    }
}
