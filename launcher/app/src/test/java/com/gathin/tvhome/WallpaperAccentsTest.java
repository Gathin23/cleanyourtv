package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

public class WallpaperAccentsTest {
    @Test
    public void readsFileAndColour() {
        Map<String, Integer> a = WallpaperAccents.parse(Arrays.asList("01-a.jpg\t#ff1744"));
        assertEquals(0xFFFF1744, (int) a.get("01-a.jpg"));
    }

    @Test
    public void skipsWhatDoesNotParse() {
        Map<String, Integer> a = WallpaperAccents.parse(Arrays.asList("", "# note", "no-tab #ff0000",
                "b.jpg\tff0000", "c.jpg\t#ff00", "d.jpg\t#gggggg", "\t#ff0000"));
        assertTrue(a.isEmpty());
    }

    @Test
    public void keepsGoodLinesBesideBadOnes() {
        Map<String, Integer> a = WallpaperAccents.parse(Arrays.asList("bad.jpg\t#zzzzzz", "good.jpg\t#00e5ff"));
        assertFalse(a.containsKey("bad.jpg"));
        assertEquals(0xFF00E5FF, (int) a.get("good.jpg"));
    }
}
