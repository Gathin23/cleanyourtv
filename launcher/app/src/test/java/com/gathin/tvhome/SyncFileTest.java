package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SyncFileTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void copiesSourceToDestinationAtomically() throws IOException {
        File src = folder.newFile("src.jpg");
        File dst = new File(folder.getRoot(), "dst.jpg");
        byte[] data = "test wallpaper payload".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(src)) {
            out.write(data);
        }
        src.setLastModified(123456000L);

        Config.syncFile(src, dst);

        assertTrue(dst.isFile());
        assertEquals(data.length, dst.length());
        assertFalse(new File(folder.getRoot(), "dst.jpg.tmp").exists());
    }

    @Test
    public void skipsSyncWhenLengthAndTimestampMatch() throws IOException {
        File src = folder.newFile("src.jpg");
        File dst = folder.newFile("dst.jpg");
        byte[] data = "already synced".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(src)) {
            out.write(data);
        }
        try (FileOutputStream out = new FileOutputStream(dst)) {
            out.write(data);
        }
        long time = 500000000L;
        src.setLastModified(time);
        dst.setLastModified(time);

        Config.syncFile(src, dst);

        assertEquals(time, dst.lastModified());
        assertFalse(new File(folder.getRoot(), "dst.jpg.tmp").exists());
    }

    @Test
    public void preservesPreviousDestinationOnMissingOrInvalidSource() throws IOException {
        File dst = folder.newFile("dst.jpg");
        byte[] original = "existing good cache".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(dst)) {
            out.write(original);
        }

        Config.syncFile(null, dst);
        assertEquals(original.length, dst.length());

        File nonexistent = new File(folder.getRoot(), "nonexistent.jpg");
        Config.syncFile(nonexistent, dst);
        assertEquals(original.length, dst.length());
    }

    @Test
    public void cleansStagingFileOnFailure() {
        File staging = new File(folder.getRoot(), "dst.jpg.tmp");
        assertFalse(staging.exists());
    }
}
