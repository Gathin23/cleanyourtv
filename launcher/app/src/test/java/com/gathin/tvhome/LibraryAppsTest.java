package com.gathin.tvhome;

import org.junit.Test;
import java.text.Collator;
import java.util.Locale;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class LibraryAppsTest {
    @Test public void thePillHidesOnlyWhileTheTopBarButtonIsAWayIn() {
        assertTrue(LibraryApps.showPill(true, false, true));
        assertTrue(LibraryApps.showPill(true, true, true));
        assertTrue(LibraryApps.showPill(true, false, false));
        assertFalse(LibraryApps.showPill(true, true, false));
        assertFalse(LibraryApps.showPill(false, true, true));
        assertFalse(LibraryApps.showPill(false, false, true));
    }

    @Test public void okOpensHomeAppsAndEveryAppWithTheSwitchButNeverInSelectionMode() {
        assertTrue(LibraryApps.okOpens(false, false, true));
        assertFalse(LibraryApps.okOpens(false, false, false));
        assertTrue(LibraryApps.okOpens(false, true, false));
        assertTrue(LibraryApps.okOpens(false, true, true));
        assertFalse(LibraryApps.okOpens(true, true, false));
        assertFalse(LibraryApps.okOpens(true, false, true));
    }

    @Test public void hidingKeepsTheNextAppOrThePreviousAppAtTheEnd() {
        assertEquals(1, LibraryApps.nextAfterRemoval(0, 5));
        assertEquals(3, LibraryApps.nextAfterRemoval(2, 5));
        assertEquals(3, LibraryApps.nextAfterRemoval(4, 5));
        assertEquals(-1, LibraryApps.nextAfterRemoval(0, 1));
        assertEquals(-1, LibraryApps.nextAfterRemoval(0, 0));
        assertEquals(1, LibraryApps.nextAfterRemoval(-1, 3));
        assertEquals(1, LibraryApps.nextAfterRemoval(9, 3));
    }

    @Test public void checkedAppsComeFirstAndEachGroupCanUseLabelOrder() {
        assertTrue(LibraryApps.compareHomeGroup(true, false) < 0);
        assertTrue(LibraryApps.compareHomeGroup(false, true) > 0);
        assertEquals(0, LibraryApps.compareHomeGroup(true, true));
        assertEquals(0, LibraryApps.compareHomeGroup(false, false));
        Collator alphabet = Collator.getInstance(Locale.ENGLISH);
        assertTrue(LibraryApps.compareCatalog(true, "Zeta", "pkg.zeta",
                false, "Alpha", "pkg.alpha", alphabet) < 0);
        assertTrue(LibraryApps.compareCatalog(true, "Alpha", "pkg.alpha",
                true, "Zeta", "pkg.zeta", alphabet) < 0);
        assertTrue(LibraryApps.compareCatalog(false, "Alpha", "pkg.alpha",
                false, "Zeta", "pkg.zeta", alphabet) < 0);
    }

    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test public void missingShortcutsComeBeforeHomeAppsEvenWhenTheHomeAppIsNewer() {
        long now = 100 * DAY;
        assertTrue(LibraryApps.comparePriority(false, DAY, true, now, now) < 0);
        assertTrue(LibraryApps.comparePriority(true, now, false, DAY, now) > 0);
    }

    @Test public void recentMissingAppsComeFirstButOlderAppsAndHomeAppsUseLabelOrder() {
        long now = 100 * DAY;
        assertTrue(LibraryApps.comparePriority(false, now, false, now - DAY, now) < 0);
        assertTrue(LibraryApps.comparePriority(false, now - DAY, false, DAY, now) < 0);
        assertEquals(0, LibraryApps.comparePriority(false, DAY, false, 2 * DAY, now));
        assertEquals(0, LibraryApps.comparePriority(true, now, true, DAY, now));
    }

    @Test public void recentMeansFirstInstalledWithinSevenDaysAndNotAnUnknownOrFutureDate() {
        long now = 100 * DAY;
        assertTrue(LibraryApps.recent(now - 7 * DAY, now));
        assertFalse(LibraryApps.recent(now - 7 * DAY - 1, now));
        assertFalse(LibraryApps.recent(0, now));
        assertFalse(LibraryApps.recent(now + 1, now));
    }
    @Test public void themeAndTvDefaultsStayInPlaceUntilTheOwnerChoosesAnApp() {
        assertTrue(LibraryApps.visible(true, false, false));
        assertFalse(LibraryApps.visible(true, true, false));
        assertFalse(LibraryApps.visible(false, false, false));
        assertTrue(LibraryApps.visible(false, false, true));
        assertTrue(LibraryApps.visible(true, true, true));
    }

    @Test public void undoingAnAdditionRestoresDefaultVisibilityAndKeepsOtherChoices() {
        Set<String> original = new HashSet<>();
        original.add("com.example.phone");
        original.add("com.example.hidden");
        Set<String> next = LibraryApps.change(original, "com.example.phone", false);
        assertFalse(next.contains("com.example.phone"));
        assertTrue(next.contains("com.example.hidden"));
        assertTrue(original.contains("com.example.phone"));
        assertFalse(LibraryApps.visible(false, false, next.contains("com.example.phone")));
    }

    @Test public void removingAnyHomeAppOverridesThemeDefaultsAndPreviousAdditions() {
        assertFalse(LibraryApps.visible(true, false, false, true));
        assertFalse(LibraryApps.visible(true, false, true, true));
        assertFalse(LibraryApps.visible(false, false, true, true));
        assertTrue(LibraryApps.visible(false, false, true, false));
        assertTrue(LibraryApps.visible(true, false, false, false));
    }

    @Test public void addingAdvancesPastCheckedAppsToTheNextMissingShortcut() {
        assertEquals(3, LibraryApps.nextFocus(1, 5, index -> index == 3 || index == 4));
        assertEquals(0, LibraryApps.nextFocus(4, 5, index -> index == 0 || index == 2));
    }

    @Test public void addingTheLastMissingShortcutKeepsANearbyCardInsteadOfJumpingToTheStart() {
        assertEquals(3, LibraryApps.nextFocus(2, 5, index -> false));
        assertEquals(3, LibraryApps.nextFocus(4, 5, index -> false));
        assertEquals(0, LibraryApps.nextFocus(0, 1, index -> { fail("No other card to inspect"); return true; }));
        assertEquals(-1, LibraryApps.nextFocus(0, 0, index -> { fail("Empty catalogue"); return true; }));
    }

    @Test public void staleFocusIsClampedAfterAnAppRemoval() {
        assertEquals(1, LibraryApps.nextFocus(9, 3, index -> index == 1));
        assertEquals(1, LibraryApps.nextFocus(-1, 3, index -> index == 1));
    }

    @Test public void choicesRoundTripInStableOrderIncludingAnEmptyList() {
        Set<String> apps = new HashSet<>();
        apps.add("org.z.example"); apps.add("com.a.example");
        assertEquals("com.a.example\norg.z.example\n", LibraryApps.encode(apps));
        assertEquals(apps, LibraryApps.decode(LibraryApps.encode(apps)));
        assertEquals(Collections.emptySet(), LibraryApps.decode(""));
    }

    @Test public void duplicateAndMalformedRecordsAreRejected() {
        for (String raw : new String[]{"com.example\ncom.example\n", "com..example\n",
                "com.example\n\n", "../file\n", "9bad.app\n", "com.example\ninvalid/name\n"}) {
            try { LibraryApps.decode(raw); fail(raw); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void excludedFromCatalogIsExactSetMembershipAndEmptyByDefault() {
        Set<String> excluded = new HashSet<>();
        excluded.add("com.example.factory");
        assertTrue(LibraryApps.excludedFromCatalog(excluded, "com.example.factory"));
        assertFalse(LibraryApps.excludedFromCatalog(excluded, "com.example.other"));
        assertFalse(LibraryApps.excludedFromCatalog(Collections.emptySet(), "com.example.factory"));
    }

    @Test public void aFullListAllowsRemovalAndAnExistingChoiceButNotAnotherApp() {
        Set<String> apps = new HashSet<>();
        for (int i = 0; i < LibraryApps.MAX_ADDED; i++) apps.add("com.example.app" + i);
        assertEquals(apps, LibraryApps.change(apps, "com.example.app0", true));
        assertEquals(LibraryApps.MAX_ADDED - 1, LibraryApps.change(apps, "com.example.app0", false).size());
        try { LibraryApps.change(apps, "com.example.extra", true); fail(); }
        catch (IllegalArgumentException expected) { }
        assertEquals(LibraryApps.MAX_ADDED, apps.size());
    }
}
