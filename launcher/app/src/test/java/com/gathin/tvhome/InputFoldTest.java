package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InputFoldTest {
    // TvInputManager's INPUT_STATE_* values: CONNECTED 0, CONNECTED_STANDBY 1, DISCONNECTED 2.
    @Test public void aConnectedHdmiPortStays() {
        assertFalse(InputFold.folds(true, 0));
    }

    @Test public void anEmptyPortReportingStandbyFolds() {
        assertTrue(InputFold.folds(true, 1));
    }

    @Test public void aDisconnectedPortFolds() {
        assertTrue(InputFold.folds(true, 2));
    }

    @Test public void anHdmiRowWithNoStateStays() {
        assertFalse(InputFold.folds(true, -1));
    }

    @Test public void anAvJackAlwaysFolds() {
        assertTrue(InputFold.folds(false, -1));
        assertTrue(InputFold.folds(false, 0));
    }
}
