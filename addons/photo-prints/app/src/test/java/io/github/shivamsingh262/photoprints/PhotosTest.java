package io.github.shivamsingh262.photoprints;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The date photos.sh writes into a name, and which files count as photos. */
public class PhotosTest {

    @Test
    public void theDateInTheName() {
        assertArrayEquals(new int[]{2023, 12}, Photos.dateFromName("photo-0007-20231202.jpg"));
        assertArrayEquals(new int[]{2023, 12}, Photos.dateFromName("photo-0007-202312.jpg"));
        assertArrayEquals(new int[]{2026, 1}, Photos.dateFromName("photo-0001-20260110.JPEG"));
    }

    @Test
    public void noDateOrNoSuchMonth() {
        assertNull(Photos.dateFromName("photo-0007.jpg"));
        assertNull(Photos.dateFromName("holiday.jpg"));
        assertNull(Photos.dateFromName("photo-0007-20231302.jpg"));
        assertNull(Photos.dateFromName("photo-0007-18991202.jpg"));
    }

    @Test
    public void onlyImagesCount() {
        assertTrue(Photos.isImage("photo-0001-20231202.jpg"));
        assertTrue(Photos.isImage("Beach day.webp"));
        assertTrue(Photos.isImage("scan.PNG"));
        assertFalse(Photos.isImage(".hidden.jpg"));
        assertFalse(Photos.isImage("notes.txt"));
        assertFalse(Photos.isImage("clip.mp4"));
        assertFalse(Photos.isImage(null));
    }
}
