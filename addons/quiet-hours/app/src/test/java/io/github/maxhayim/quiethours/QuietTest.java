package io.github.maxhayim.quiethours;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuietTest {
    private static final int NIGHT = 23 * 60, DAY = 12 * 60;

    @Test
    public void defaultsCapFromEightToSeven() {
        Quiet.Settings s = new Quiet.Settings();
        assertTrue(Quiet.capNow(s, 20 * 60));
        assertTrue(Quiet.capNow(s, NIGHT));
        assertTrue(Quiet.capNow(s, 6 * 60 + 59));
        assertFalse(Quiet.capNow(s, 7 * 60));
        assertFalse(Quiet.capNow(s, DAY));
    }

    @Test
    public void modes() {
        Quiet.Settings s = new Quiet.Settings();
        s.mode = Quiet.Mode.ALWAYS;
        assertTrue(Quiet.capNow(s, DAY));
        assertEquals(-1, Quiet.minutesToNextChange(s, DAY));
        s.mode = Quiet.Mode.OFF;
        assertFalse(Quiet.capNow(s, NIGHT));
        assertFalse(Quiet.needsService(s));
    }

    @Test
    public void capIsAVolumeStepAndNeverSilence() {
        assertEquals(20, Quiet.capIndex(20, 100));
        assertEquals(3, Quiet.capIndex(20, 15));
        assertEquals(1, Quiet.capIndex(5, 15));
        assertEquals(15, Quiet.capIndex(100, 15));
        assertEquals(0, Quiet.capIndex(20, 0));
    }

    @Test
    public void lowersOnlyAboveTheCapDuringQuietHours() {
        Quiet.Settings s = new Quiet.Settings();
        assertTrue(Quiet.mustLower(s, NIGHT, 21, 100, false, false, false));
        assertFalse(Quiet.mustLower(s, NIGHT, 20, 100, false, false, false));
        assertFalse("never raises a lower volume", Quiet.mustLower(s, NIGHT, 5, 100, false, false, false));
        assertFalse(Quiet.mustLower(s, DAY, 90, 100, false, false, false));
    }

    @Test
    public void headphonesAndFixedVolume() {
        Quiet.Settings s = new Quiet.Settings();
        assertFalse(Quiet.mustLower(s, NIGHT, 80, 100, true, false, false));
        s.headphonesFree = false;
        assertTrue(Quiet.mustLower(s, NIGHT, 80, 100, true, false, false));
        assertFalse("ARC: Android can't change it", Quiet.mustLower(s, NIGHT, 80, 100, false, true, false));
    }

    @Test
    public void neverTouchesAMutedTv() {
        Quiet.Settings s = new Quiet.Settings();
        assertFalse("lowering could unmute it", Quiet.mustLower(s, NIGHT, 80, 100, false, false, true));
        assertTrue("capped as soon as it's unmuted", Quiet.mustLower(s, NIGHT, 80, 100, false, false, false));
    }

    @Test
    public void nextChangeIsTheNearerEdge() {
        Quiet.Settings s = new Quiet.Settings();
        assertEquals(60, Quiet.minutesToNextChange(s, 19 * 60));
        assertEquals(8 * 60, Quiet.minutesToNextChange(s, NIGHT));
        assertEquals(11 * 60, Quiet.minutesToNextChange(s, 20 * 60));
    }

    @Test
    public void storedValuesAreClamped() {
        Quiet.Settings s = new Quiet.Settings();
        s.cap = 0;
        s.start = -15;
        s.end = 24 * 60 + 7;
        s.mode = null;
        s.clamped();
        assertEquals(Quiet.MIN_CAP, s.cap);
        assertEquals(23 * 60 + 45, s.start);
        assertEquals(0, s.end);
        assertEquals(Quiet.Mode.SCHEDULED, s.mode);
    }

    @Test
    public void clock() {
        assertEquals("20:00", Quiet.clock(20 * 60, true));
        assertEquals("8:00 PM", Quiet.clock(20 * 60, false));
    }
}
