package com.norma.tvlauncher;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackageMapTest {
    @Test public void anEntryMapsItsPackageToItsAction() {
        Map<String, String> m = Config.packageMap(Collections.singletonList(
                "com.telekom.onetv.tv.pl=com.telekom.onetv.tv.pl.action.PROGRAM_GUIDE"));
        assertEquals("com.telekom.onetv.tv.pl.action.PROGRAM_GUIDE", m.get("com.telekom.onetv.tv.pl"));
    }

    @Test public void spacesAroundTheHalvesAreTrimmed() {
        assertEquals("a.ACTION", Config.packageMap(Collections.singletonList(" a.pkg = a.ACTION ")).get("a.pkg"));
    }

    @Test public void entriesWithoutBothHalvesAreSkipped() {
        assertTrue(Config.packageMap(Arrays.asList("a.pkg", "=a.ACTION", "a.pkg=", "")).isEmpty());
    }

    @Test public void aValueMayContainAnEqualsSign() {
        assertEquals("a=b", Config.packageMap(Collections.singletonList("a.pkg=a=b")).get("a.pkg"));
    }

    @Test public void aLaterEntryForAPackageWins() {
        assertEquals("second", Config.packageMap(Arrays.asList("a.pkg=first", "a.pkg=second")).get("a.pkg"));
    }
}
