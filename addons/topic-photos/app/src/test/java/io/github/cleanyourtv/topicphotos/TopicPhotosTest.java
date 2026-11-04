package io.github.cleanyourtv.topicphotos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TopicPhotosTest {

    @Test
    public void topicsAreTrimmedDedupedAndCapped() {
        assertEquals(Arrays.asList("mountains", "city at night"), Settings.parseTopics(" mountains ,, city   at night, mountains"));
        assertEquals(8, Settings.parseTopics("a,b,c,d,e,f,g,h,i,j").size());
        assertTrue(Settings.parseTopics(" , ").isEmpty());
    }

    @Test
    public void daylightIsSevenToNineteen() {
        assertEquals("night", Settings.daypart(6 * 60 + 59));
        assertEquals("day", Settings.daypart(7 * 60));
        assertEquals("day", Settings.daypart(18 * 60 + 59));
        assertEquals("night", Settings.daypart(19 * 60));
    }

    @Test
    public void queriesGetTheTimeOfDayOnlyWhenAsked() {
        List<String> topics = Arrays.asList("ocean", "forest");
        assertEquals(topics, Photos.queries(topics, ""));
        assertEquals(Arrays.asList("ocean daylight", "forest daylight"), Photos.queries(topics, "day"));
        assertEquals(Arrays.asList("ocean night", "forest night"), Photos.queries(topics, "night"));
    }

    @Test
    public void onlyLandscapeJpegsAtLeastTheScreenFit() {
        assertTrue(Photos.fits("image/jpeg", 4032, 3024, 1920, 1080));
        assertFalse(Photos.fits("image/jpeg", 1600, 1200, 1920, 1080));   // smaller than the screen
        assertFalse(Photos.fits("image/jpeg", 3000, 4000, 1920, 1080));   // portrait
        assertFalse(Photos.fits("image/jpeg", 8000, 2000, 1920, 1080));   // a panorama, cropped to nothing
        assertFalse(Photos.fits("image/png", 4000, 3000, 1920, 1080));
    }

    @Test
    public void commonsWidthIsTheSmallestStepThatCovers() {
        assertEquals(1280, Photos.commonsWidth(1280));
        assertEquals(1920, Photos.commonsWidth(1920));
        assertEquals(3840, Photos.commonsWidth(2560));
    }
}
