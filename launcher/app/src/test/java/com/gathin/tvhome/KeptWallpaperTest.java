package com.gathin.tvhome;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KeptWallpaperTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File theme;
    private File privateDir;
    private File external;
    private File copy;

    @Before public void setUp() throws IOException {
        theme = tmp.newFolder("theme");
        privateDir = tmp.newFolder("files");
        external = new File(theme, Config.WALLPAPER_NAME);
        copy = new File(privateDir, Config.WALLPAPER_NAME);
    }

    private static void write(File file, byte[] bytes, long modified) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
        assertTrue(file.setLastModified(modified));
    }

    @Test public void mountedWallpaperIsUsedAndCopied() throws IOException {
        byte[] picture = {1, 2, 3, 4};
        write(external, picture, 1_700_000_000_000L);
        assertEquals(external, Config.keptWallpaper(privateDir, external, true));
        assertArrayEquals(picture, Files.readAllBytes(copy.toPath()));
        assertEquals(external.lastModified(), copy.lastModified());
        assertFalse(new File(privateDir, Config.WALLPAPER_NAME + ".part").exists());
    }

    @Test public void beforeMountTheCopyStandsIn() throws IOException {
        write(external, new byte[] {9, 9}, 1_700_000_000_000L);
        Config.keptWallpaper(privateDir, external, true);
        // At the next boot the card is not mounted yet: the external file cannot be seen.
        assertEquals(copy, Config.keptWallpaper(privateDir, null, false));
    }

    @Test public void beforeMountWithoutACopyThereIsNoWallpaper() {
        assertNull(Config.keptWallpaper(privateDir, external, false));
    }

    @Test public void removedWallpaperTakesTheCopyWithIt() throws IOException {
        write(copy, new byte[] {5}, 1_700_000_000_000L);
        assertNull(Config.keptWallpaper(privateDir, external, true));
        assertFalse(copy.exists());
    }

    @Test public void emptyWallpaperCountsAsNone() throws IOException {
        write(external, new byte[0], 1_700_000_000_000L);
        assertNull(Config.keptWallpaper(privateDir, external, true));
        assertFalse(copy.exists());
    }

    @Test public void newWallpaperReplacesTheCopy() throws IOException {
        write(external, new byte[] {1, 2}, 1_700_000_000_000L);
        Config.keptWallpaper(privateDir, external, true);
        byte[] next = {7, 7, 7};
        write(external, next, 1_700_000_100_000L);
        Config.keptWallpaper(privateDir, external, true);
        assertArrayEquals(next, Files.readAllBytes(copy.toPath()));
        assertEquals(external.lastModified(), copy.lastModified());
    }

    @Test public void unchangedWallpaperIsNotCopiedAgain() throws IOException {
        write(external, new byte[] {1, 2}, 1_700_000_000_000L);
        Config.keptWallpaper(privateDir, external, true);
        // Same size and date: a copy left with other bytes shows it was not rewritten.
        write(copy, new byte[] {3, 4}, external.lastModified());
        Config.keptWallpaper(privateDir, external, true);
        assertArrayEquals(new byte[] {3, 4}, Files.readAllBytes(copy.toPath()));
    }
}
