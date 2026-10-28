package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class QuietMachineModelTest {
    @Test public void greetingsChangeAtLocalDayBoundaries() {
        assertEquals("Good night", QuietMachineModel.greeting(0));
        assertEquals("Good night", QuietMachineModel.greeting(4));
        assertEquals("Good morning", QuietMachineModel.greeting(5));
        assertEquals("Good afternoon", QuietMachineModel.greeting(12));
        assertEquals("Good evening", QuietMachineModel.greeting(17));
        assertEquals("Good night", QuietMachineModel.greeting(22));
    }
    @Test public void appSearchIsCaseInsensitiveAndDoesNotInventCatalogs() {
        assertTrue(QuietMachineModel.matches("Spotify", "com.spotify.tv", " SPOT ", "Music"));
        assertFalse(QuietMachineModel.matches("Netflix", "com.netflix.tv", "", "Music"));
        assertFalse(QuietMachineModel.matches("Netflix", "com.netflix.tv", "", "Movies"));
        assertTrue(QuietMachineModel.matches("Netflix", "com.netflix.tv", "netflix", "All"));
        assertFalse(QuietMachineModel.matches("Netflix", "com.netflix.tv", "missing", "All"));
    }
    @Test public void offRestoresSystemBrightness() {
        assertEquals(-1f, QuietMachineModel.brightness("Off"), 0f);
        assertEquals(-1f, QuietMachineModel.brightness("unknown"), 0f);
        assertTrue(QuietMachineModel.brightness("Night") < QuietMachineModel.brightness("Evening"));
        assertEquals(1f, QuietMachineModel.brightness("Day"), 0f);
    }
}
