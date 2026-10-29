package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsKeyTest {
    private static final class Panel implements SettingsKey.Starter {
        final List<String> opened = new ArrayList<>();
        boolean available = true;
        RuntimeException failure;

        @Override public boolean service(String component, String action) {
            opened.add("service:" + component + ":" + action);
            if (failure != null) throw failure;
            return available;
        }

        @Override public void activity(String action) {
            opened.add("activity:" + action);
            if (failure != null) throw failure;
        }
    }

    @Test public void emptyDefaultsLeaveTheExistingSettingsRouteAlone() {
        Panel panel = new Panel();
        assertFalse(SettingsKey.open("", "", panel));
        assertTrue(panel.opened.isEmpty());
    }

    @Test public void explicitServiceKeepsItsActionAndDoesNotOpenAnActivityToo() {
        Panel panel = new Panel();
        assertTrue(SettingsKey.open("vendor.settings/.Panel", "vendor.OPEN_PANEL", panel));
        assertEquals(Arrays.asList("service:vendor.settings/.Panel:vendor.OPEN_PANEL"), panel.opened);
    }

    @Test public void explicitServiceDoesNotRequireAnAction() {
        Panel panel = new Panel();
        assertTrue(SettingsKey.open("vendor.settings/.Panel", "", panel));
        assertEquals(Arrays.asList("service:vendor.settings/.Panel:"), panel.opened);
    }

    @Test public void actionWithoutServiceOpensAnActivity() {
        Panel panel = new Panel();
        assertTrue(SettingsKey.open("", "vendor.OPEN_PANEL", panel));
        assertEquals(Arrays.asList("activity:vendor.OPEN_PANEL"), panel.opened);
    }

    @Test public void missingServiceFallsBackWithoutTreatingItsActionAsAnActivity() {
        Panel panel = new Panel();
        panel.available = false;
        assertFalse(SettingsKey.open("vendor.settings/.Missing", "vendor.OPEN_PANEL", panel));
        assertEquals(Arrays.asList("service:vendor.settings/.Missing:vendor.OPEN_PANEL"), panel.opened);
    }

    @Test public void malformedServiceFallsBackWithoutStartingAnything() {
        for (String component : new String[] {"vendor.settings", "/.Panel", "vendor.settings/", "a/b/c"}) {
            Panel panel = new Panel();
            assertFalse(component, SettingsKey.open(component, "vendor.OPEN_PANEL", panel));
            assertTrue(component, panel.opened.isEmpty());
        }
    }

    @Test public void deniedOrUnavailableServiceLetsTheCallerUseExistingSettings() {
        for (RuntimeException error : new RuntimeException[] {
                new SecurityException(), new IllegalStateException(), new IllegalArgumentException()}) {
            Panel panel = new Panel();
            panel.failure = error;
            assertFalse(SettingsKey.open("vendor.settings/.Panel", "vendor.OPEN_PANEL", panel));
        }
    }

    @Test public void missingOrDeniedActivityLetsTheCallerUseExistingSettings() {
        for (RuntimeException error : new RuntimeException[] {
                new ActivityNotFoundException(), new SecurityException()}) {
            Panel panel = new Panel();
            panel.failure = error;
            assertFalse(SettingsKey.open("", "vendor.OPEN_PANEL", panel));
        }
    }

    @Test public void ordinaryReleasesKeepWorkingWithTheFeatureOffAndOn() {
        for (boolean configured : new boolean[] {false, true}) {
            assertTrue(SettingsKey.released(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SETTINGS, 0, configured));
            assertTrue(SettingsKey.released(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU, 0, configured));
            assertFalse(SettingsKey.released(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU, 0, configured));
            assertFalse(SettingsKey.released(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU,
                    KeyEvent.FLAG_CANCELED, configured));
        }
    }

    @Test public void onlyAnOptedInMenuReleaseAcceptsTheObservedFirmwareFlags() {
        assertTrue(SettingsKey.released(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU, 30001, true));
        assertFalse(SettingsKey.released(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU, 30001, false));
        assertFalse(SettingsKey.released(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU, 30001, true));
        assertFalse(SettingsKey.released(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SETTINGS, 30001, true));
        assertFalse(SettingsKey.released(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU,
                30001 | KeyEvent.FLAG_LONG_PRESS, true));
    }
}
