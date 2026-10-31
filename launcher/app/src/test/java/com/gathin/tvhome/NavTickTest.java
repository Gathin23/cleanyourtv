package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NavTickTest {
    private final Object tileA = new Object();
    private final Object tileB = new Object();

    @Test public void tileToTileIsAMove() {
        assertTrue(NavTick.isMove(tileA, tileB));
    }

    @Test public void focusFromNowhereIsNot() {
        assertFalse(NavTick.isMove(null, tileA));
    }

    @Test public void focusLeavingTheWindowIsNot() {
        assertFalse(NavTick.isMove(tileA, null));
    }

    @Test public void sameViewIsNot() {
        assertFalse(NavTick.isMove(tileA, tileA));
    }
}
