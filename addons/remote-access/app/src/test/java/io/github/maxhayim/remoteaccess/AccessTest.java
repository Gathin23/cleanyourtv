package io.github.maxhayim.remoteaccess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccessTest {
    @Test
    public void adbStates() {
        assertEquals(Access.Adb.OFF, Access.adb(false, false, -1));
        assertEquals(Access.Adb.OFF, Access.adb(false, false, 5555));
        assertEquals(Access.Adb.USB_ONLY, Access.adb(true, false, -1));
        assertEquals(Access.Adb.NETWORK, Access.adb(true, false, 5555));
        assertEquals(Access.Adb.NETWORK, Access.adb(true, true, -1));
    }

    @Test
    public void wirelessDebuggingWithUsbDebuggingOff() {
        // Xiaomi TV Box S 3rd gen (MiTV-AFMU0), Google TV 14: adb_enabled 0, adb_wifi_enabled 1, and port 5555 answers.
        assertEquals(Access.Adb.NETWORK, Access.adb(false, true, 5555));
        assertEquals(Access.Adb.NETWORK, Access.adb(false, true, -1));
    }

    @Test
    public void adbWording() {
        assertEquals("On, port 5555", Access.adbValue(Access.Adb.NETWORK, false, 5555));
        assertEquals("Wireless debugging", Access.adbValue(Access.Adb.NETWORK, true, -1));
        assertEquals("Off", Access.adbValue(Access.Adb.OFF, false, -1));
    }

    @Test
    public void ports() {
        assertEquals(5555, Access.port("5555"));
        assertEquals(-1, Access.port(""));
        assertEquals(-1, Access.port("-1"));
        assertEquals(-1, Access.port("70000"));
        assertEquals(-1, Access.port(null));
    }

    @Test
    public void tailscaleRange() {
        assertTrue(Access.tailscaleAddress("100.64.0.1"));
        assertTrue(Access.tailscaleAddress("100.101.102.103"));
        assertFalse(Access.tailscaleAddress("100.128.0.1"));
        assertFalse(Access.tailscaleAddress("10.0.0.37"));
        assertFalse(Access.tailscaleAddress("fd7a::1"));
    }

    @Test
    public void tailscaleWording() {
        assertEquals("Not installed", Access.tailscale(false, null));
        assertEquals("Installed, not connected", Access.tailscale(true, null));
        assertEquals("Connected, 100.64.0.1", Access.tailscale(true, "100.64.0.1"));
    }
}
