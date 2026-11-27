package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Config.readWallpaper keeps a copy of wallpaper.jpg in internal storage so that Home shows the
 * theme wallpaper even when external storage is not yet mounted at boot (#4104).
 */
public class WallpaperBootCopyTest {

    private static File dir(String prefix) throws IOException {
        return Files.createTempDirectory(prefix).toFile();
    }

    private static File write(File dir, String name, byte[] bytes) throws IOException {
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        }
        return f;
    }

    @Test
    public void mountedWithWallpaperReturnsSrcAndKeepsCopy() throws IOException {
        File external = dir("ext");
        File internal = dir("int");
        File src = write(external, Config.WALLPAPER_NAME, new byte[]{1, 2, 3});

        File result = Config.readWallpaper(internal, src, true);

        assertEquals(src, result);
        File copy = new File(internal, Config.WALLPAPER_NAME);
        assertTrue("copy must exist in internal storage", copy.isFile());
        assertEquals(src.length(), copy.length());
    }

    @Test
    public void unmountedWithCopyReturnsCopy() throws IOException {
        File external = dir("ext");
        File internal = dir("int");
        File src = write(external, Config.WALLPAPER_NAME, new byte[]{1, 2, 3});
        // seed the internal copy first (simulates a prior boot with storage mounted)
        Config.readWallpaper(internal, src, true);

        File result = Config.readWallpaper(internal, src, false);

        assertEquals(new File(internal, Config.WALLPAPER_NAME), result);
    }

    @Test
    public void unmountedWithNoCopyReturnsNull() throws IOException {
        File internal = dir("int");
        File result = Config.readWallpaper(internal, null, false);
        assertNull("no copy yet — must return null, not crash", result);
    }

    @Test
    public void mountedWithNoWallpaperDeletesCopyAndReturnsNull() throws IOException {
        File external = dir("ext");
        File internal = dir("int");
        File src = write(external, Config.WALLPAPER_NAME, new byte[]{1, 2, 3});
        Config.readWallpaper(internal, src, true);
        assertTrue(new File(internal, Config.WALLPAPER_NAME).isFile());

        // wallpaper removed from theme
        src.delete();
        File result = Config.readWallpaper(internal, src, true);

        assertNull(result);
        assertFalse("stale copy must be removed", new File(internal, Config.WALLPAPER_NAME).isFile());
    }

    @Test
    public void copyIsRefreshedWhenExternalFileChanges() throws IOException {
        File external = dir("ext");
        File internal = dir("int");
        File src = write(external, Config.WALLPAPER_NAME, new byte[]{1, 2, 3});
        Config.readWallpaper(internal, src, true);

        // replace with a larger file
        write(external, Config.WALLPAPER_NAME, new byte[]{4, 5, 6, 7});
        Config.readWallpaper(internal, src, true);

        assertEquals(src.length(), new File(internal, Config.WALLPAPER_NAME).length());
    }
}
