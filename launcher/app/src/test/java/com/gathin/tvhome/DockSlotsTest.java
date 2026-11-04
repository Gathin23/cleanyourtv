package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DockSlotsTest {
    @Test public void threeDockAppsInFourColumnsLeaveOneSlotEmpty() {
        assertEquals(1, DockSlots.empty(3, 4, 20, false));
    }

    @Test public void fillDockKeepsTheOldBehaviour() {
        assertEquals(0, DockSlots.empty(3, 4, 20, true));
    }

    @Test public void aFullDockNeedsNoEmptySlot() {
        assertEquals(0, DockSlots.empty(4, 4, 20, false));
        assertEquals(0, DockSlots.empty(6, 6, 20, false));
    }

    @Test public void noInstalledDockAppFillsTheFirstRow() {
        assertEquals(0, DockSlots.empty(0, 4, 20, false));
    }

    @Test public void nothingAfterTheDockNeedsNoEmptySlot() {
        assertEquals(0, DockSlots.empty(2, 4, 2, false));
    }

    @Test public void focusOnAnEmptySlotGoesToTheLastDockApp() {
        assertEquals(2, DockSlots.focusable(3, 3, 1));
        assertEquals(1, DockSlots.focusable(2, 2, 2));
        assertEquals(1, DockSlots.focusable(3, 2, 2));
    }

    @Test public void focusOnATileStaysPut() {
        assertEquals(0, DockSlots.focusable(0, 3, 1));
        assertEquals(2, DockSlots.focusable(2, 3, 1));
        assertEquals(4, DockSlots.focusable(4, 3, 1));
        assertEquals(3, DockSlots.focusable(3, 4, 0));
    }
}
