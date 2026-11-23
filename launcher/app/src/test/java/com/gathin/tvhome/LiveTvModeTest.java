package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveTvModeTest {
    @Test public void aSinglePressDoesNotUnlock() {
        LiveTvMode m = new LiveTvMode();
        assertFalse(m.press(10_000, 3));
        assertFalse(m.unlocked());
        assertTrue(m.opensLiveTv(10_000));
    }

    @Test public void quickPressesUpToTheCountUnlock() {
        LiveTvMode m = new LiveTvMode();
        assertFalse(m.press(10_000, 3));
        assertFalse(m.press(10_800, 3));
        assertTrue(m.press(11_600, 3));
        assertTrue(m.unlocked());
        assertFalse(m.opensLiveTv(11_700));
    }

    @Test public void twoPressesAreEnoughWhenTheCountIsTwo() {
        LiveTvMode m = new LiveTvMode();
        assertFalse(m.press(5_000, 2));
        assertTrue(m.press(5_900, 2));
    }

    @Test public void aSlowPressStartsTheBurstAgain() {
        LiveTvMode m = new LiveTvMode();
        m.press(10_000, 3);
        m.press(10_500, 3);
        assertFalse(m.press(11_501, 3));   // more than a second after the last one: counts as the first
        assertFalse(m.press(12_000, 3));
        assertTrue(m.press(12_500, 3));
    }

    @Test public void sleepLocksAgain() {
        LiveTvMode m = new LiveTvMode();
        m.press(1_000, 2);
        m.press(1_500, 2);
        m.lock();
        assertFalse(m.unlocked());
        assertTrue(m.opensLiveTv(60_000));
        assertFalse(m.press(60_100, 2));   // the burst before the sleep does not carry over
    }

    @Test public void aLiveTvAppThatClosesAtOnceIsNotOpenedAgainInALoop() {
        LiveTvMode m = new LiveTvMode();
        m.opened(20_000);
        assertFalse(m.opensLiveTv(21_000));
        assertFalse(m.opensLiveTv(24_000));
        assertTrue(m.opensLiveTv(24_001));
    }

    @Test public void aRecentOpenIsStillOnItsWay() {
        LiveTvMode m = new LiveTvMode();
        assertFalse(m.openedWithin(5_000, 1_000));
        m.opened(5_000);
        assertTrue(m.openedWithin(5_900, 1_000));
        assertFalse(m.openedWithin(6_001, 1_000));
    }

    @Test public void thePressesChoiceCyclesBetweenTwoAndThree() {
        assertEquals(3, LiveTvMode.nextPresses(2));
        assertEquals(2, LiveTvMode.nextPresses(3));
        assertEquals(2, LiveTvMode.nextPresses(5));
        assertEquals(2, LiveTvMode.nextPresses(0));
    }
}
