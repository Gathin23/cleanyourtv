package com.gathin.tvhome;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class LaunchActionsTest {
    @Test public void configuredActionOverridesDefaultOnlyForItsPackage() {
        Object normal = new Object(), featured = new Object();
        Object result = LaunchActions.select("store", Map.of("store", "FEATURED_LAUNCH"), normal,
                (pkg, action) -> {
                    assertEquals("store", pkg);
                    assertEquals("FEATURED_LAUNCH", action);
                    return featured;
                });
        assertSame(featured, result);
    }

    @Test public void unresolvedActionRetainsNormalEntry() {
        Object normal = new Object();
        assertSame(normal, LaunchActions.select("store", Map.of("store", "MISSING"), normal,
                (pkg, action) -> null));
    }

    @Test public void unconfiguredPackageDoesNotTryOtherAppsAction() {
        Object normal = new Object();
        assertSame(normal, LaunchActions.select("other", Map.of("store", "FEATURED_LAUNCH"), normal,
                (pkg, action) -> { throw new AssertionError("No action configured for this package"); }));
    }

    @Test public void viewPrefixGivesTheUri() {
        assertEquals("bbox://live", LaunchActions.viewUri("view:bbox://live"));
        assertEquals("bbox://live?a=b", LaunchActions.viewUri("view: bbox://live?a=b "));
    }

    @Test public void plainActionOrEmptyViewHasNoUri() {
        assertNull(LaunchActions.viewUri("com.telekom.onetv.tv.pl.action.PROGRAM_GUIDE"));
        assertNull(LaunchActions.viewUri("view:"));
        assertNull(LaunchActions.viewUri(null));
    }

    @Test public void emptyConfigurationRetainsNormalEntryWithoutResolving() {
        Object normal = new Object();
        assertSame(normal, LaunchActions.select("store", Map.of(), normal,
                (pkg, action) -> { throw new AssertionError("Unnecessary resolution"); }));
    }

    @Test public void inputIdStripsThePrefixAndTrimsTheId() {
        assertEquals("com.mediatek.tvinput/.hdmi.HDMIInputService/HW5",
                LaunchActions.inputId("input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"));
        assertEquals("HW5", LaunchActions.inputId("input: HW5 "));
    }

    @Test public void inputIdIsNullForEverythingElse() {
        assertNull(LaunchActions.inputId(null));
        assertNull(LaunchActions.inputId("FEATURED_LAUNCH"));
        assertNull(LaunchActions.inputId("INPUT:HW5"));
        assertNull(LaunchActions.inputId("not-input:HW5"));
    }

    @Test public void emptyInputIdFallsBackToANormalLaunchInsteadOfAnEmptySwitch() {
        assertNull(LaunchActions.inputId("input:"));
        assertNull(LaunchActions.inputId("input:   "));
    }
}
