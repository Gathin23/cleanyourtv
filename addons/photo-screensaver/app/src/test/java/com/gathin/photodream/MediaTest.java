package com.gathin.photodream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MediaTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void providerWritesOnlyPicturesClipsAndSeconds() {
        assertEquals("pictures/sea.jpg", Media.path(Arrays.asList("pictures", "sea.jpg")));
        assertEquals("pictures/Sea_2.MP4", Media.path(Arrays.asList("pictures", "Sea_2.MP4")));
        assertEquals("seconds.txt", Media.path(Collections.singletonList("seconds.txt")));
        assertNull(Media.path(Arrays.asList("pictures", "..")));
        assertNull(Media.path(Arrays.asList("pictures", ".jpg")));
        assertNull(Media.path(Arrays.asList("pictures", "sea.mkv")));
        assertNull(Media.path(Arrays.asList("pictures", "sub", "a.jpg")));
        assertNull(Media.path(Collections.singletonList("sea.jpg")));
        assertNull(Media.path(Collections.singletonList("pictures")));
        assertNull(Media.path(null));
        assertTrue(Media.isFolder(Collections.singletonList("pictures")));
        assertFalse(Media.isFolder(Arrays.asList("pictures", "sea.jpg")));
    }

    @Test public void secondsDefaultToTwenty() {
        assertEquals(20, Media.seconds(null));
        assertEquals(20, Media.seconds("abc"));
        assertEquals(20, Media.seconds("4"));
        assertEquals(20, Media.seconds("3601"));
        assertEquals(45, Media.seconds(" 45\n"));
    }

    @Test public void listsByNameSkippingEmptyAndOtherFiles() throws Exception {
        File dir = tmp.newFolder();
        write(new File(dir, "b.mp4"), 3);
        write(new File(dir, "a.jpg"), 3);
        write(new File(dir, "empty.png"), 0);
        write(new File(dir, "notes.txt"), 3);
        List<File> files = Media.list(dir);
        assertEquals(2, files.size());
        assertEquals("a.jpg", files.get(0).getName());
        assertEquals("b.mp4", files.get(1).getName());
        assertEquals("b.mp4", Media.next(files, "a.jpg").getName());
        assertEquals("a.jpg", Media.next(files, "b.mp4").getName());
        assertEquals("a.jpg", Media.next(files, "gone.jpg").getName());
        assertTrue(Media.list(new File(dir, "missing")).isEmpty());
    }

    @Test public void decodesNoBiggerThanTheScreen() {
        assertTrue(Media.accepted(3840, 2160));
        assertFalse(Media.accepted(65535, 2159));
        assertEquals(2, Media.sampleSize(3840, 2160, 1920, 1080));
        assertEquals(1, Media.sampleSize(1920, 1080, 1920, 1080));
        assertArrayEquals(new int[]{2400, 1920}, Media.coverScale(2400, 1800, 1920, 1080));
        assertNull(Media.coverScale(1920, 1080, 1920, 1080));
    }

    @Test public void readsTheDateFromTheName() {
        assertArrayEquals(new int[]{2023, 12}, Media.dateFromName("photo-0007-20231202.jpg"));
        assertArrayEquals(new int[]{2023, 12}, Media.dateFromName("photo-202312.jpg"));
        assertNull(Media.dateFromName("photo-20231302.jpg"));
        assertNull(Media.dateFromName("holiday.jpg"));
        assertNull(Media.dateFromName(null));
    }

    private static void write(File file, int bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[bytes]);
        }
    }
}
