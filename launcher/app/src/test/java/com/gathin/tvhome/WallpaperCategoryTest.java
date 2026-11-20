package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class WallpaperCategoryTest {
    private final List<File> collection = Arrays.asList(new File("misty--valley.jpg"), new File("ocean--coast.jpg"),
            new File("ocean--waves.jpg"), new File("plain.jpg"));

    @Test
    public void categoryIsThePrefixBeforeTheDoubleDash() {
        assertEquals("ocean", WallpaperRotation.category("ocean--coast.jpg"));
        assertEquals("", WallpaperRotation.category("plain.jpg"));
        assertEquals("", WallpaperRotation.category("--odd.jpg"));
    }

    @Test
    public void aCategoryChoiceKeepsOnlyThatCategory() {
        List<File> ocean = WallpaperRotation.chosen(collection, null, "category:ocean");
        assertEquals(2, ocean.size());
        assertEquals("ocean--coast.jpg", ocean.get(0).getName());
    }

    @Test
    public void anEmptyCategoryLeavesTheThemeInCharge() {
        assertNull(WallpaperRotation.chosen(collection, null, "category:desert"));
    }
}
