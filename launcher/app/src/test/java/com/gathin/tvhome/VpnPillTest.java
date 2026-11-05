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
    public void hiddenWithNoVpnAndNoInstalledVpnApp() {
        assertEquals(VpnBadge.PILL_HIDDEN, VpnBadge.pillState(false, false));
    }
}
