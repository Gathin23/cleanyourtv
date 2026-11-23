package io.github.dpetrasgr.livetvmode;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModeTest {
    @Test public void aSingleHomeOpensLiveTv() {
        Mode m = new Mode();
        assertEquals(Mode.Action.OPEN_LIVE_TV, m.home(10_000, 3));
        assertFalse(m.unlocked());
    }

    @Test public void quickPressesUpToTheCountUnlock() {
        Mode m = new Mode();
        assertEquals(Mode.Action.OPEN_LIVE_TV, m.home(10_000, 3));
        assertEquals(Mode.Action.OPEN_LIVE_TV, m.home(10_400, 3));
        assertEquals(Mode.Action.UNLOCKED, m.home(10_800, 3));
        assertTrue(m.unlocked());
        assertEquals(Mode.Action.NONE, m.home(11_500, 3));
    }

    @Test public void twoPressesAreEnoughWhenTheCountIsTwo() {
        Mode m = new Mode();
        m.home(5_000, 2);
        assertEquals(Mode.Action.UNLOCKED, m.home(5_900, 2));
    }

    @Test public void aSlowPressStartsTheBurstAgain() {
        Mode m = new Mode();
        m.home(10_000, 3);
        m.home(10_500, 3);
        assertEquals(Mode.Action.OPEN_LIVE_TV, m.home(11_501, 3));
        m.home(12_000, 3);
        assertEquals(Mode.Action.UNLOCKED, m.home(12_500, 3));
    }

    @Test public void oneAppearanceReportedTwiceCountsOnce() {
        Mode m = new Mode();
        m.home(10_000, 2);
        assertEquals(Mode.Action.NONE, m.home(10_100, 2));
        assertEquals(Mode.Action.UNLOCKED, m.home(10_500, 2));
    }

    @Test public void sleepLocksAgainAndForgetsTheBurst() {
        Mode m = new Mode();
        m.home(1_000, 2);
        m.home(1_500, 2);
        assertTrue(m.unlocked());
        m.lock();
        assertFalse(m.unlocked());
        assertEquals(Mode.Action.OPEN_LIVE_TV, m.home(1_700, 2));
    }

    @Test public void aLiveTvAppThatKeepsClosingStopsBeingOpened() {
        Mode m = new Mode();
        long t = 100_000;
        for (int i = 0; i < Mode.LOOP_OPENS; i++) {
            assertFalse(m.looping(t));
            m.opened(t);
            t += 1_000;
        }
        assertTrue(m.looping(t));
        assertEquals(Mode.Action.NONE, m.home(t, 3));
        assertFalse(m.looping(100_000 + Mode.LOOP_WINDOW_MS));
    }

    @Test public void aViewerPressingHomeNowAndThenIsNotALoop() {
        Mode m = new Mode();
        long t = 100_000;
        for (int i = 0; i < 6; i++) {   // six single presses, two seconds apart
            assertEquals(Mode.Action.OPEN_LIVE_TV, m.home(t, 3));
            m.opened(t);
            t += 2_000;
        }
    }

    @Test public void sleepClearsTheLoopCount() {
        Mode m = new Mode();
        for (int i = 0; i < Mode.LOOP_OPENS; i++) m.opened(100_000 + i * 500L);
        assertTrue(m.looping(106_000));
        m.lock();
        assertFalse(m.looping(106_000));
    }

    @Test public void aRecentOpenIsStillOnItsWay() {
        Mode m = new Mode();
        assertFalse(m.openedWithin(5_000, 1_000));
        m.opened(5_000);
        assertTrue(m.openedWithin(5_900, 1_000));
        assertFalse(m.openedWithin(6_001, 1_000));
    }

    @Test public void wakeOpensLiveTvOverHomeAndOverAppsOnlyWhenAsked() {
        assertTrue(Mode.opensOnWake(true, false, false));
        assertTrue(Mode.opensOnWake(false, false, true));
        assertFalse(Mode.opensOnWake(false, false, false));
        assertFalse(Mode.opensOnWake(false, true, true));
    }

    @Test public void thePressesSettingCyclesBetweenTwoAndThree() {
        assertEquals(3, Mode.nextPresses(2));
        assertEquals(2, Mode.nextPresses(3));
        assertEquals(2, Mode.nextPresses(7));
    }
}
