package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PortalLoginTest {

    @Test public void unsetProbeFallsBackToAndroidsDefault() {
        assertEquals(PortalLogin.DEFAULT_PROBE, PortalLogin.url(null));
        assertEquals(PortalLogin.DEFAULT_PROBE, PortalLogin.url(""));
    }

    @Test public void aConfiguredPlainHttpProbeIsUsed() {
        assertEquals("http://captive.example.net/check", PortalLogin.url(" http://captive.example.net/check "));
        assertEquals("HTTP://captive.example.net/", PortalLogin.url("HTTP://captive.example.net/"));
    }

    @Test public void anHttpsProbeIsNotUsedAPortalCannotAnswerIt() {
        assertEquals(PortalLogin.DEFAULT_PROBE, PortalLogin.url("https://www.google.com/generate_204"));
        assertEquals(PortalLogin.DEFAULT_PROBE, PortalLogin.url("http://"));
    }

    @Test public void aRealBrowserOpensThePage() {
        assertTrue(PortalLogin.isBrowser("com.phlox.tvwebbrowser"));
        // "android" is the chooser: several browsers, the viewer picks.
        assertTrue(PortalLogin.isBrowser("android"));
    }

    @Test public void noBrowserOrOnlyTheStubMeansWifiSettings() {
        assertFalse(PortalLogin.isBrowser(null));
        assertFalse(PortalLogin.isBrowser(PortalLogin.STUB_PACKAGE));
    }
}
