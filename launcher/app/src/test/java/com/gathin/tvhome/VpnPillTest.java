package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VpnPillTest {

    @Test
    public void greenWhileAnyVpnIsUp() {
        assertEquals(VpnBadge.PILL_UP, VpnBadge.pillState(true, true));
        assertEquals(VpnBadge.PILL_UP, VpnBadge.pillState(true, false));
    }

    @Test
    public void redWhileTheInstalledVpnAppIsDown() {
        assertEquals(VpnBadge.PILL_OFF, VpnBadge.pillState(false, true));
    }

    @Test
    public void iconOnlyShowsTheClosedLockOnlyWhileUp() {
        assertEquals(R.drawable.ic_lock, VpnBadge.iconOnlyLock(true));
        assertEquals(R.drawable.ic_lock_open, VpnBadge.iconOnlyLock(false));
    }

    @Test
    public void hiddenWithNoVpnAndNoInstalledVpnApp() {
        assertEquals(VpnBadge.PILL_HIDDEN, VpnBadge.pillState(false, false));
    }

    /**
     * At rest the pill is an outline, so its ink is the state colour; on the solid focused fill it is the
     * contrasting ink. A solid pill at rest read as selected, which is the bug this pins.
     */
    @Test
    public void inkContrastsTheSolidFillOnlyWhenFocused() {
        assertEquals(VpnBadge.INK, VpnBadge.pillInkColor(true, true));
        assertEquals(VpnBadge.GREEN, VpnBadge.pillInkColor(true, false));
        assertEquals(VpnBadge.INK_OFF, VpnBadge.pillInkColor(false, true));
        assertEquals(VpnBadge.RED, VpnBadge.pillInkColor(false, false));
    }
}
