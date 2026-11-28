package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for shelf_previews: how Continue watching and preview programs are put together per app. */
public class ShelfPreviewsTest {

    private static WatchNextShelf.Item item(String pkg, String title, String link) {
        return new WatchNextShelf.Item(pkg, title, null, null, null, null, null, link, -1, -1, -1, -1, -1, null);
    }

    private static List<String> titles(List<WatchNextShelf.Item> items) {
        List<String> out = new ArrayList<>();
        for (WatchNextShelf.Item i : items) out.add(i.title);
        return out;
    }

    @Test
    public void continueWatchingFirstThenPreviewsWithoutDuplicates() {
        Map<String, List<WatchNextShelf.Item>> next = new LinkedHashMap<>();
        next.put("prime", Arrays.asList(item("prime", "Blade Runner", "a")));
        Map<String, List<WatchNextShelf.Item>> previews = new LinkedHashMap<>();
        previews.put("prime", Arrays.asList(item("prime", "Blade Runner again", "a"), item("prime", "Pick", "b")));
        Map<String, List<WatchNextShelf.Item>> out = WatchNextShelf.merge(next, previews, Collections.emptyList());
        assertEquals(Arrays.asList("Blade Runner", "Pick"), titles(out.get("prime")));
    }

    @Test
    public void appsWithOnlyPreviewsFollowAndHiddenAppsAreDropped() {
        Map<String, List<WatchNextShelf.Item>> next = new LinkedHashMap<>();
        next.put("prime", Arrays.asList(item("prime", "Film", "p")));
        Map<String, List<WatchNextShelf.Item>> previews = new LinkedHashMap<>();
        previews.put("arte", Arrays.asList(item("arte", "Doku", "x")));
        previews.put("spotify", Arrays.asList(item("spotify", "Podcast", "s")));
        Map<String, List<WatchNextShelf.Item>> out =
                WatchNextShelf.merge(next, previews, Collections.singletonList("spotify"));
        assertEquals(Arrays.asList("prime", "arte"), new ArrayList<>(out.keySet()));
        assertFalse(out.containsKey("spotify"));
    }

    @Test
    public void rowsWithoutLinkOrTitleAreSkippedAndTheCapHolds() {
        List<WatchNextShelf.Item> many = new ArrayList<>();
        many.add(item("arte", "No link", null));
        many.add(item("arte", null, "untitled"));
        for (int i = 0; i < 30; i++) many.add(item("arte", "T" + i, "l" + i));
        Map<String, List<WatchNextShelf.Item>> previews = new LinkedHashMap<>();
        previews.put("arte", many);
        List<WatchNextShelf.Item> out =
                WatchNextShelf.merge(Collections.emptyMap(), previews, Collections.emptyList()).get("arte");
        assertEquals(WatchNextShelf.MAX_WITH_PREVIEWS, out.size());
        assertEquals("T0", out.get(0).title);
    }

    @Test
    public void previewCardsHaveNoProgressBar() {
        assertTrue(item("arte", "Doku", "x").progress() < 0);
    }

    @Test
    public void browsableChannelsWinElseAllInIdOrder() {
        assertEquals(Arrays.asList(5L), WatchNextShelf.pickChannels(
                Arrays.asList(new long[]{3, 0}, new long[]{5, 1})));
        assertEquals(Arrays.asList(3L, 5L), WatchNextShelf.pickChannels(
                Arrays.asList(new long[]{3, 0}, new long[]{5, 0})));
        assertEquals(Collections.emptyList(), WatchNextShelf.pickChannels(Collections.<long[]>emptyList()));
    }
}
