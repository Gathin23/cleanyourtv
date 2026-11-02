package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IconPackFilterTest {
    @Test
    public void componentInfoIsSplit() {
        assertEquals("com.a/com.a.Main", IconPackFilter.componentKey("ComponentInfo{com.a/com.a.Main}"));
        assertEquals("com.a", IconPackFilter.packageOf(" ComponentInfo{com.a/com.a.Main} "));
    }

    @Test
    public void malformedComponentInfoIsEmpty() {
        for (String bad : new String[] {null, "", "com.a/com.a.Main", "ComponentInfo{}", "ComponentInfo{/x}",
                "ComponentInfo{com.a/}", "ComponentInfo{com.a}", "ComponentInfo{a/b/c}", "ComponentInfo{a/b"}) {
            assertEquals(bad, "", IconPackFilter.componentKey(bad));
            assertEquals(bad, "", IconPackFilter.packageOf(bad));
        }
    }

    @Test
    public void exactComponentBeatsPackage() {
        IconPackFilter f = new IconPackFilter();
        f.add("ComponentInfo{com.a/com.a.Main}", "main_icon");
        f.add("ComponentInfo{com.a/com.a.Other}", "other_icon");
        assertEquals("other_icon", f.lookup("com.a", "com.a.Other"));
        assertEquals("main_icon", f.lookup("com.a", "com.a.Main"));
    }

    @Test
    public void unknownClassFallsBackToPackageThenNull() {
        IconPackFilter f = new IconPackFilter();
        f.add("ComponentInfo{com.a/com.a.Main}", "main_icon");
        assertEquals("main_icon", f.lookup("com.a", "com.a.Elsewhere"));
        assertNull(f.lookup("com.b", "com.b.Main"));
    }

    @Test
    public void unsafeDrawableNamesAreIgnored() {
        IconPackFilter f = new IconPackFilter();
        f.add("ComponentInfo{com.a/com.a.Main}", "");
        f.add("ComponentInfo{com.a/com.a.Main}", null);
        f.add("ComponentInfo{com.a/com.a.Main}", "../x");
        f.add("ComponentInfo{com.a/com.a.Main}", "a/b");
        f.add("garbage", "icon");
        assertTrue(f.isEmpty());
    }

    @Test
    public void firstItemForAComponentWins() {
        IconPackFilter f = new IconPackFilter();
        f.add("ComponentInfo{com.a/com.a.Main}", "first");
        f.add("ComponentInfo{com.a/com.a.Main}", "second");
        assertEquals("first", f.lookup("com.a", "com.a.Main"));
    }
}
