package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The wallpaper picker's preview shows the focused row's picture; the None row and stray positions show none. */
public class WallpaperPreviewTest {

    private static final List<File> IMAGES = Arrays.asList(
            new File("/sdcard/wallpapers/01-dusk.jpg"),
            new File("/sdcard/wallpapers/02-dawn.jpg"));

    @Test
    public void aRowShowsItsOwnPicture() {
        assertEquals(IMAGES.get(0), WallpaperPreview.image(IMAGES, 0));
        assertEquals(IMAGES.get(1), WallpaperPreview.image(IMAGES, 1));
    }

    @Test
    public void theNoneRowShowsNoPicture() {
        // The picker's last row is wallpaper_none; its position equals the list size.
        assertNull(WallpaperPreview.image(IMAGES, IMAGES.size()));
    }

    @Test
    public void strayPositionsShowNoPicture() {
        assertNull(WallpaperPreview.image(IMAGES, -1));
        assertNull(WallpaperPreview.image(IMAGES, IMAGES.size() + 1));
    }

    @Test
    public void anEmptyPickerShowsNoPicture() {
        assertNull(WallpaperPreview.image(null, 0));
        assertNull(WallpaperPreview.image(java.util.Collections.emptyList(), 0));
    }
}
