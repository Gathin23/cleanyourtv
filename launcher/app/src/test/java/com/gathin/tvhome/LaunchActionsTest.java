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

    @Test public void emptyConfigurationRetainsNormalEntryWithoutResolving() {
        Object normal = new Object();
        assertSame(normal, LaunchActions.select("store", Map.of(), normal,
                (pkg, action) -> { throw new AssertionError("Unnecessary resolution"); }));
    }
}
