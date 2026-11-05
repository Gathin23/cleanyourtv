package io.github.nickld.frigateglance;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KeyRulesTest {
    private static final int LEFT = 21, RIGHT = 22, UP = 19, BACK = 4, DOWN = 0, UPA = 1;
    private final KeyRules rules = new KeyRules(LEFT, RIGHT);

    @Test
    public void nothingCaughtWithoutACard() {
        assertEquals(KeyRules.PASS, rules.decide(LEFT, DOWN, 0, false));
        assertEquals(KeyRules.PASS, rules.decide(LEFT, UPA, 100, false));
    }

    @Test
    public void dismissKeyIsSwallowedOnDownAndActsOnUp() {
        assertEquals(KeyRules.SWALLOW, rules.decide(LEFT, DOWN, 0, true));
        assertEquals(KeyRules.DISMISS, rules.decide(LEFT, UPA, 100, true));
    }

    @Test
    public void snoozeKeyActsOnUp() {
        assertEquals(KeyRules.SWALLOW, rules.decide(RIGHT, DOWN, 0, true));
        assertEquals(KeyRules.SNOOZE, rules.decide(RIGHT, UPA, 100, true));
    }

    @Test
    public void otherKeysPassWhileACardShows() {
        assertEquals(KeyRules.PASS, rules.decide(UP, DOWN, 0, true));
        assertEquals(KeyRules.PASS, rules.decide(BACK, UPA, 100, true));
    }

    @Test
    public void upWithoutItsDownStillPassesAfterTheCardWent() {
        // Down arrived while no card was up, so it went to the app; its up must follow it there.
        assertEquals(KeyRules.PASS, rules.decide(LEFT, DOWN, 0, false));
        assertEquals(KeyRules.PASS, rules.decide(LEFT, UPA, 100, true));
    }

    @Test
    public void upIsStillSwallowedIfTheCardLeftAfterDown() {
        assertEquals(KeyRules.SWALLOW, rules.decide(LEFT, DOWN, 0, true));
        assertEquals(KeyRules.DISMISS, rules.decide(LEFT, UPA, 100, false));
    }

    @Test
    public void unknownKeyCodesCatchNothing() {
        KeyRules none = new KeyRules(0, 0);
        assertEquals(KeyRules.PASS, none.decide(0, DOWN, 0, true));
        assertEquals(KeyRules.PASS, none.decide(LEFT, DOWN, 0, true));
    }

    @Test
    public void fullscreenKeyActsOnUpWhenSet() {
        KeyRules r = new KeyRules(LEFT, RIGHT, UP);
        assertEquals(KeyRules.SWALLOW, r.decide(UP, DOWN, 0, true));
        assertEquals(KeyRules.FULLSCREEN, r.decide(UP, UPA, 100, true));
    }

    @Test
    public void upPassesWhenFullscreenIsOff() {
        assertEquals(KeyRules.PASS, rules.decide(UP, DOWN, 0, true));
    }

    @Test
    public void holdKeyIgnoresAShortPress() {
        KeyRules r = new KeyRules(LEFT, RIGHT, 0, true, false, false);
        assertEquals(KeyRules.SWALLOW, r.decide(LEFT, DOWN, 1000, true));
        assertEquals(KeyRules.SWALLOW, r.decide(LEFT, UPA, 1000 + KeyRules.HOLD_MS - 1, true));
    }

    @Test
    public void holdKeyActsWhenHeldLongEnough() {
        KeyRules r = new KeyRules(LEFT, RIGHT, 0, true, false, false);
        assertEquals(KeyRules.SWALLOW, r.decide(LEFT, DOWN, 1000, true));
        assertEquals(KeyRules.DISMISS, r.decide(LEFT, UPA, 1000 + KeyRules.HOLD_MS, true));
    }

    @Test
    public void repeatsWhileHeldAreSwallowedAndDoNotRestartTheClock() {
        KeyRules r = new KeyRules(LEFT, RIGHT, 0, false, true, false);
        assertEquals(KeyRules.SWALLOW, r.decide(RIGHT, DOWN, 1000, true));
        assertEquals(KeyRules.SWALLOW, r.decide(RIGHT, DOWN, 1400, true));
        assertEquals(KeyRules.SNOOZE, r.decide(RIGHT, UPA, 1000 + KeyRules.HOLD_MS, true));
    }

    @Test
    public void holdOnOneKeyLeavesTheOthersQuick() {
        KeyRules r = new KeyRules(LEFT, RIGHT, UP, true, false, false);
        assertEquals(KeyRules.SWALLOW, r.decide(RIGHT, DOWN, 0, true));
        assertEquals(KeyRules.SNOOZE, r.decide(RIGHT, UPA, 50, true));
    }

    @Test
    public void heldHoldKeyIsReportedUntilReleased() {
        KeyRules r = new KeyRules(LEFT, RIGHT, 0, false, true, false);
        assertEquals(0, r.pendingHold());
        r.decide(RIGHT, DOWN, 0, true);
        assertEquals(KeyRules.SNOOZE, r.pendingHold());
        r.decide(RIGHT, UPA, 100, true);
        assertEquals(0, r.pendingHold());
    }

    @Test
    public void quickKeyIsNeverAPendingHold() {
        KeyRules r = new KeyRules(LEFT, RIGHT, 0, false, true, false);
        r.decide(LEFT, DOWN, 0, true);
        assertEquals(0, r.pendingHold());
    }
}
