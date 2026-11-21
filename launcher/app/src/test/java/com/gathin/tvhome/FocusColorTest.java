package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** focus_color from a theme: the highlight's fixed colour where it would be plain white; app_glow wins. */
public class FocusColorTest {

    private static final int TERRACOTTA = 0xFFC86A4A;
    private static final int RED = 0xFFE53935;

    @Test
    public void focusColorTakesThePlaceOfWhite() {
        assertEquals(TERRACOTTA, Config.focusAccent(TERRACOTTA, 0, false));
    }

    @Test
    public void theAppColourWinsWhenTheThemeAsksForIt() {
        // app_glow (and cinematic_home's dock) keep precedence: the highlight stays the app's own colour.
        assertEquals(RED, Config.focusAccent(TERRACOTTA, RED, true));
    }

    @Test
    public void unsetLeavesTheWhiteGlassEverywhere() {
        assertEquals(0, Config.focusAccent(0, 0, false));
        assertEquals(RED, Config.focusAccent(0, RED, true));
    }
}
