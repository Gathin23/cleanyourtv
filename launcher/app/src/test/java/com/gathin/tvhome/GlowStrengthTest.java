package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GlowStrengthTest {
    @Test public void aChoiceMadeOnTheTvWinsOverTheTheme() {
        assertEquals(0, Config.chosenGlowStrength(0, 100));
        assertEquals(40, Config.chosenGlowStrength(40, 70));
    }

    @Test public void noChoiceOrAnOutOfRangeOneKeepsTheThemesStrength() {
        assertEquals(70, Config.chosenGlowStrength(-1, 70));
        assertEquals(70, Config.chosenGlowStrength(101, 70));
    }
}
