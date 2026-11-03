package com.gathin.tvhome;

import android.content.pm.ApplicationInfo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UninstallTest {

    @Test
    public void nullApplicationInfoCannotBeUninstalled() {
        assertFalse(MainActivity.canUninstall(null));
    }

    @Test
    public void systemAppCannotBeUninstalled() {
        ApplicationInfo ai = new ApplicationInfo();
        ai.flags = ApplicationInfo.FLAG_SYSTEM;
        assertFalse(MainActivity.canUninstall(ai));
    }

    @Test
    public void regularUserAppCanBeUninstalled() {
        ApplicationInfo ai = new ApplicationInfo();
        ai.flags = 0;
        assertTrue(MainActivity.canUninstall(ai));
    }

    @Test
    public void updatedSystemAppCanBeUninstalled() {
        ApplicationInfo ai = new ApplicationInfo();
        ai.flags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        assertTrue(MainActivity.canUninstall(ai));
    }
}
