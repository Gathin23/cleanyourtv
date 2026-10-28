package io.github.justinnewman.gameday;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SportTest {
    @Test public void listIsComplete() {
        assertEquals(33, Sport.values().length);
        assertEquals("Puck drop", Sport.byLabel("hockey").word());
        assertEquals("Lights out", Sport.byLabel("Motor racing, light start").word());
        assertEquals("Green flag", Sport.byLabel("Motor racing, flag start").word());
        assertEquals("Starts", Sport.byLabel("Other").word());
        assertNull(Sport.byLabel("Basketbal"));
    }

    @Test public void teamSports() {
        assertTrue(Sport.byLabel("Soccer").team());
        assertFalse(Sport.byLabel("Golf").team());
        assertFalse(Sport.byLabel("Motor racing, light start").team());
    }

    @Test public void emoji() {
        assertEquals(Sport.byLabel("Hockey"), Sport.fromEmoji("🏒 Dallas Stars @ Minnesota Wild"));
        assertEquals(Sport.byLabel("Soccer"), Sport.fromEmoji("⚽ Utah Royals vs Portland Thorns"));
        assertEquals(Sport.byLabel("Golf"), Sport.fromEmoji("🏌️ The Masters"));
        assertNull(Sport.fromEmoji("🏎️ Mexico City Grand Prix"));
        assertNull(Sport.fromEmoji("Welcome to the calendar!"));
    }

    @Test public void labelsAreAlphabeticalWithOtherLast() {
        String[] l = Sport.labels().split(", (?=[A-Z])");
        assertEquals(33, l.length);
        assertEquals("American football", l[0]);
        assertEquals("Other", l[l.length - 1]);
    }
}
