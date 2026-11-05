package io.github.cleanyourtv.topicphotos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PhotoHistoryTest {

    @Test
    public void leftGoesBackThroughWhatWasShownAndRightReplaysItBeforeANewPick() {
        PhotoHistory h = new PhotoHistory();
        h.push("c");
        h.push("a");
        h.push("e");
        assertEquals("a", h.back(k -> true));
        assertEquals("c", h.back(k -> true));
        assertNull(h.back(k -> true));             // the first photo: nothing earlier
        assertEquals("a", h.forward(k -> true));
        assertEquals("e", h.forward(k -> true));
        assertNull(h.forward(k -> true));          // the end: the carousel picks a new one
    }

    @Test
    public void aNewPickAfterGoingBackDropsTheRestAndPhotosGoneAreSkipped() {
        PhotoHistory h = new PhotoHistory();
        h.push("a");
        h.push("b");
        h.push("c");
        h.back(k -> true);                         // on b
        h.push("d");                               // c is no longer ahead
        assertNull(h.forward(k -> true));
        assertEquals("a", h.back(k -> !k.equals("b")));   // b left the list in a refresh
        assertNull(h.back(k -> true));
    }
}
