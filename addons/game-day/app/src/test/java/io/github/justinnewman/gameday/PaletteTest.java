package io.github.justinnewman.gameday;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

public class PaletteTest {
    @Test public void thirtySixNamedColours() {
        assertEquals(36, Palette.COUNT);
        assertEquals("Red, light", Palette.name(0));
        assertEquals("Grey, dark", Palette.name(35));
        assertEquals(16, Palette.byName("green, medium"));
        assertEquals(-1, Palette.byName("Chartreuse"));
        assertEquals(0xFF2E7D32, Palette.colour(16));
    }

    @Test public void firstUnusedSkipsUsed() {
        assertEquals(12, Palette.firstUnused(new HashSet<>()));
        assertEquals(13, Palette.firstUnused(new HashSet<>(Arrays.asList(12))));
    }
}
