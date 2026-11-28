package io.github.jain05vaibhav.previewrows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.jain05vaibhav.previewrows.PreviewModels.CardItem;
import io.github.jain05vaibhav.previewrows.PreviewModels.ChannelRecord;
import io.github.jain05vaibhav.previewrows.PreviewModels.ChannelRow;
import io.github.jain05vaibhav.previewrows.PreviewModels.FocusTarget;
import io.github.jain05vaibhav.previewrows.PreviewModels.IntentChoice;
import io.github.jain05vaibhav.previewrows.PreviewModels.PosterSource;
import io.github.jain05vaibhav.previewrows.PreviewModels.ProgramRecord;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreviewLogicTest {

    private static ProgramRecord program(long id, long channelId, String pkg, String title,
                                         String poster, String intentUri, int weight, int order) {
        return new ProgramRecord(id, channelId, pkg, title, "", "", "", "",
                poster, "", intentUri, weight, 0L, 0L, true, order);
    }

    @Test
    public void groupsByChannelDropsEmptyChannelsAndSortsByWeight() {
        List<ChannelRecord> channels = Arrays.asList(
                new ChannelRecord(10L, "com.arte.tv", "ARTE Live", "", false, "TYPE_PREVIEW", 0),
                new ChannelRecord(20L, "com.google.android.youtube.tv", "Recommended for you", "", false, "TYPE_PREVIEW", 1),
                new ChannelRecord(30L, "com.amazon.amazonvideo.livingroom", "Watch Next", "", true, "TYPE_PREVIEW", 2)
        );
        List<ProgramRecord> programs = Arrays.asList(
                program(1, 20L, "", "Low weight clip", "https://i.ytimg.com/1.jpg", "https://youtube.com/watch?v=1", 5, 0),
                program(2, 20L, "", "High weight clip", "https://i.ytimg.com/2.jpg", "https://youtube.com/watch?v=2", 90, 1),
                program(3, 30L, "com.amazon.amazonvideo.livingroom", "Prime Movie", "https://m.media-amazon.com/3.jpg", "intent:#Intent;package=com.amazon.amazonvideo.livingroom;end", 10, 0)
        );
        Map<String, String> labels = new HashMap<>();
        labels.put("com.google.android.youtube.tv", "YouTube");
        labels.put("com.amazon.amazonvideo.livingroom", "Prime Video");

        List<ChannelRow> rows = PreviewLogic.groupAndFilter(channels, programs, labels);

        assertEquals(2, rows.size());
        // Browsable channel comes first
        assertEquals(30L, rows.get(0).channelId);
        assertEquals("Prime Video · Watch Next", rows.get(0).headerLabel);
        assertEquals(1, rows.get(0).items.size());

        // Non-browsable channel with programs follows; ARTE empty channel is dropped
        assertEquals(20L, rows.get(1).channelId);
        assertEquals("YouTube · Recommended for you", rows.get(1).headerLabel);
        assertEquals(2, rows.get(1).items.size());
        assertEquals("High weight clip", rows.get(1).items.get(0).title);
        assertEquals("Low weight clip", rows.get(1).items.get(1).title);
    }

    @Test
    public void filtersOutNonSearchableAndInvalidPrograms() {
        List<ChannelRecord> channels = Collections.singletonList(
                new ChannelRecord(5L, "com.example.tv", "Featured", "", false, "TYPE_PREVIEW", 0)
        );
        List<ProgramRecord> programs = Arrays.asList(
                new ProgramRecord(1, 5L, "com.example.tv", "Hidden row", "", "", "", "",
                        "", "", "https://example.com/1", 10, 0L, 0L, false, 0),
                new ProgramRecord(2, 0L, "com.example.tv", "No channel", "", "", "", "",
                        "", "", "https://example.com/2", 10, 0L, 0L, true, 1),
                new ProgramRecord(3, 5L, "", "", "", "", "", "",
                        "", "", "", 10, 0L, 0L, true, 2),
                program(4, 5L, "com.example.tv", "Visible row", "", "https://example.com/4", 1, 3)
        );

        List<ChannelRow> rows = PreviewLogic.groupAndFilter(channels, programs, Collections.emptyMap());
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).items.size());
        assertEquals("Visible row", rows.get(0).items.get(0).title);
    }

    @Test
    public void choosesProgramIntentThenChannelIntentThenPackageFallback() {
        ChannelRecord channel = new ChannelRecord(1L, "com.netflix.ninja", "Trending",
                "intent:#Intent;action=android.intent.action.VIEW;package=com.netflix.ninja;end",
                false, "TYPE_PREVIEW", 0);

        ProgramRecord withProgramIntent = program(1, 1L, "com.netflix.ninja", "Show A",
                "", "https://www.netflix.com/title/800", 10, 0);
        IntentChoice c1 = PreviewLogic.chooseIntent(withProgramIntent, channel, "");
        assertEquals(IntentChoice.Kind.INTENT_URI, c1.kind);
        assertEquals("https://www.netflix.com/title/800", c1.uri);

        ProgramRecord missingProgramIntent = program(2, 1L, "com.netflix.ninja", "Show B",
                "", "javascript:alert(1)", 10, 1);
        IntentChoice c2 = PreviewLogic.chooseIntent(missingProgramIntent, channel, "");
        assertEquals(IntentChoice.Kind.CHANNEL_URI, c2.kind);
        assertEquals(channel.appLinkIntentUri, c2.uri);

        ChannelRecord channelWithoutLink = new ChannelRecord(2L, "com.spotify.tv.android", "Podcasts",
                "", false, "TYPE_PREVIEW", 1);
        ProgramRecord noUri = program(3, 2L, "", "Episode 1", "", "", 1, 0);
        IntentChoice c3 = PreviewLogic.chooseIntent(noUri, channelWithoutLink, "");
        assertEquals(IntentChoice.Kind.PACKAGE_LAUNCH, c3.kind);
        assertEquals("com.spotify.tv.android", c3.packageName);

        IntentChoice c4 = PreviewLogic.chooseIntent(
                program(4, 9L, "", "Orphan", "", "", 1, 0), null, "");
        assertEquals(IntentChoice.Kind.NONE, c4.kind);
    }

    @Test
    public void validatesLaunchUriSchemesSafely() {
        assertTrue(PreviewLogic.isAllowedLaunchUri("intent:#Intent;action=android.intent.action.VIEW;end"));
        assertTrue(PreviewLogic.isAllowedLaunchUri("https://www.netflix.com/watch/123"));
        assertTrue(PreviewLogic.isAllowedLaunchUri("youtube://watch/abc"));
        assertFalse(PreviewLogic.isAllowedLaunchUri(null));
        assertFalse(PreviewLogic.isAllowedLaunchUri("   "));
        assertFalse(PreviewLogic.isAllowedLaunchUri("javascript:alert(1)"));
        assertFalse(PreviewLogic.isAllowedLaunchUri("file:///etc/passwd"));
        assertFalse(PreviewLogic.isAllowedLaunchUri("https://"));
        assertFalse(PreviewLogic.isAllowedLaunchUri("not-a-uri"));
    }

    @Test
    public void handlesLocalAndRemotePostersWithoutInternet() {
        ProgramRecord localContent = program(1, 1L, "org.jellyfin.androidtv", "Movie",
                "content://org.jellyfin.androidtv.image/42", "intent://open", 1, 0);
        assertEquals(PosterSource.Kind.LOCAL_URI, PreviewLogic.choosePoster(localContent).kind);

        ProgramRecord localRes = program(2, 1L, "com.spotify.tv.android", "Podcast",
                "android.resource://com.spotify.tv.android/drawable/cover", "intent://open", 1, 1);
        assertEquals(PosterSource.Kind.LOCAL_URI, PreviewLogic.choosePoster(localRes).kind);

        ProgramRecord remoteHttps = program(3, 1L, "com.google.android.youtube.tv", "Video",
                "https://i.ytimg.com/vi/abc/hqdefault.jpg", "https://youtube.com/watch?v=abc", 1, 2);
        assertEquals(PosterSource.Kind.REMOTE_FALLBACK, PreviewLogic.choosePoster(remoteHttps).kind);

        ProgramRecord fallbackThumb = new ProgramRecord(4, 1L, "org.jellyfin.androidtv", "Episode",
                "", "", "", "", "https://example.com/remote.jpg",
                "content://org.jellyfin.androidtv.image/thumb", "intent://open", 1, 0, 0, true, 3);
        assertEquals(PosterSource.Kind.LOCAL_URI, PreviewLogic.choosePoster(fallbackThumb).kind);
        assertEquals("content://org.jellyfin.androidtv.image/thumb", PreviewLogic.choosePoster(fallbackThumb).uri);

        ProgramRecord missing = program(5, 1L, "it.rainet.androidtv", "Live", "", "intent://open", 1, 4);
        assertEquals(PosterSource.Kind.NONE, PreviewLogic.choosePoster(missing).kind);
    }

    @Test
    public void buildsSubtitleWithEpisodeAndProgress() {
        ProgramRecord episode = new ProgramRecord(1, 1L, "com.crunchyroll.crunchyroid", "Frieren",
                "Journey continues", "Beyond Journey's End", "1", "24",
                "", "", "intent://play", 10, 600_000L, 1_200_000L, true, 0);
        assertEquals("S1 · E24 · Beyond Journey's End · 50%",
                PreviewLogic.buildSubtitle(episode, "Frieren"));

        ProgramRecord tagline = new ProgramRecord(2, 1L, "it.rainet.androidtv", "Show",
                "Daily drama", "", "", "", "", "", "intent://play", 1, 0L, 0L, true, 0);
        assertEquals("Daily drama", PreviewLogic.buildSubtitle(tagline, "Show"));
    }

    @Test
    public void avoidsDuplicatingAppNameInChannelHeader() {
        assertEquals("YouTube", PreviewLogic.formatHeader("YouTube", "YouTube", "com.yt", 1L));
        assertEquals("YouTube · Trending", PreviewLogic.formatHeader("YouTube", "Trending", "com.yt", 1L));
        assertEquals("com.unknown.app", PreviewLogic.formatHeader("", "", "com.unknown.app", 7L));
        assertEquals("Channel 7", PreviewLogic.formatHeader("", "", "", 7L));
    }

    @Test
    public void sortsTiesInWeightByCursorOrderAndHandlesOrphanChannels() {
        List<ProgramRecord> programs = Arrays.asList(
                program(10, 99L, "com.example.orphan", "First", "", "intent://1", 5, 0),
                program(11, 99L, "com.example.orphan", "Second", "", "intent://2", 5, 1)
        );
        List<ChannelRow> rows = PreviewLogic.groupAndFilter(Collections.emptyList(), programs, Collections.emptyMap());
        assertEquals(1, rows.size());
        assertEquals("com.example.orphan", rows.get(0).headerLabel);
        List<CardItem> items = rows.get(0).items;
        assertEquals("First", items.get(0).title);
        assertEquals("Second", items.get(1).title);
    }

    @Test
    public void groupsWithEmptyChannelListUsingAppLabel() {
        List<ProgramRecord> programs = Arrays.asList(
                program(1, 42L, "com.google.android.youtube.tv", "Clip A", "", "https://youtube.com/watch?v=a", 20, 0),
                program(2, 42L, "com.google.android.youtube.tv", "Clip B", "", "https://youtube.com/watch?v=b", 10, 1)
        );
        Map<String, String> labels = Collections.singletonMap("com.google.android.youtube.tv", "YouTube");

        List<ChannelRow> rows = PreviewLogic.groupAndFilter(Collections.emptyList(), programs, labels);
        assertEquals(1, rows.size());
        assertEquals("YouTube", rows.get(0).headerLabel);
        assertEquals("YouTube", rows.get(0).appLabel);
        assertEquals(2, rows.get(0).items.size());
    }

    @Test
    public void enforcesIntentLaunchPublisherAndSelfPackageChecks() {
        String self = "io.github.jain05vaibhav.previewrows";
        assertTrue(PreviewLogic.canLaunch("com.netflix.ninja", "com.netflix.ninja", self));
        assertTrue(PreviewLogic.canLaunch("com.example.player", "", self));
        assertFalse(PreviewLogic.canLaunch("", "com.netflix.ninja", self));
        assertFalse(PreviewLogic.canLaunch(null, "com.netflix.ninja", self));
        assertFalse(PreviewLogic.canLaunch(self, "", self));
        assertFalse(PreviewLogic.canLaunch(self, self, self));
        assertFalse(PreviewLogic.canLaunch("com.other.browser", "com.netflix.ninja", self));
    }

    @Test
    public void restoresMatchingFocusTargetOrFallsBackToChannelThenFirst() {
        List<ChannelRecord> channels = Arrays.asList(
                new ChannelRecord(10L, "com.app.a", "Row A", "", true, "TYPE_PREVIEW", 0),
                new ChannelRecord(20L, "com.app.b", "Row B", "", false, "TYPE_PREVIEW", 1)
        );
        List<ProgramRecord> programs = Arrays.asList(
                program(101, 10L, "com.app.a", "A1", "", "https://a/1", 10, 0),
                program(201, 20L, "com.app.b", "B1", "", "https://b/1", 20, 0),
                program(202, 20L, "com.app.b", "B2", "", "https://b/2", 10, 1)
        );
        List<ChannelRow> rows = PreviewLogic.groupAndFilter(channels, programs, Collections.emptyMap());

        FocusTarget exact = PreviewLogic.pickFocusTarget(rows, 20L, 202L);
        assertEquals(1, exact.rowIndex);
        assertEquals(1, exact.itemIndex);

        FocusTarget sameChannel = PreviewLogic.pickFocusTarget(rows, 20L, 999L);
        assertEquals(1, sameChannel.rowIndex);
        assertEquals(0, sameChannel.itemIndex);

        FocusTarget fallback = PreviewLogic.pickFocusTarget(rows, 99L, 999L);
        assertEquals(0, fallback.rowIndex);
        assertEquals(0, fallback.itemIndex);
    }

    @Test
    public void subsamplesExtremeAspectRatioImagesWithinPixelBudget() {
        int sample = PreviewLogic.computeSampleSize(20000, 100, 204, 114);
        assertTrue("Expected sample >= 32 for 20000px wide image, got " + sample, sample >= 32);
        assertEquals(1, PreviewLogic.computeSampleSize(204, 114, 204, 114));
    }

    @Test
    public void focusTargetIgnoresItemsBeyondRowCap() {
        List<ChannelRecord> channels = Collections.singletonList(
                new ChannelRecord(10L, "com.app.a", "Row A", "", true, "TYPE_PREVIEW", 0)
        );
        List<ProgramRecord> programs = Arrays.asList(
                program(101, 10L, "com.app.a", "A1", "", "https://a/1", 30, 0),
                program(102, 10L, "com.app.a", "A2", "", "https://a/2", 20, 1),
                program(103, 10L, "com.app.a", "A3", "", "https://a/3", 10, 2)
        );
        List<ChannelRow> rows = PreviewLogic.groupAndFilter(channels, programs, Collections.emptyMap());
        List<ChannelRow> capped = PreviewLogic.capRows(rows, 2);
        assertEquals(1, capped.size());
        assertEquals(2, capped.get(0).items.size());

        // Item 103 is beyond cap=2, so focus falls back to item 0 of channel 10L instead of index 2.
        FocusTarget beyondCap = PreviewLogic.pickFocusTarget(rows, 10L, 103L, 2);
        assertEquals(0, beyondCap.rowIndex);
        assertEquals(0, beyondCap.itemIndex);

        FocusTarget onCapped = PreviewLogic.pickFocusTarget(capped, 10L, 103L);
        assertEquals(0, onCapped.rowIndex);
        assertEquals(0, onCapped.itemIndex);

        FocusTarget withinCap = PreviewLogic.pickFocusTarget(capped, 10L, 102L);
        assertEquals(0, withinCap.rowIndex);
        assertEquals(1, withinCap.itemIndex);
    }
}

