package com.gathin.newsdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConfigTest {

    @Test public void providerWritesOnlyTheTwoFiles() {
        assertEquals("feeds.txt", Config.path(Collections.singletonList("feeds.txt")));
        assertEquals("seconds.txt", Config.path(Collections.singletonList("seconds.txt")));
        assertNull(Config.path(Collections.singletonList("cache.bin")));
        assertNull(Config.path(Collections.singletonList("..")));
        assertNull(Config.path(Arrays.asList("x", "feeds.txt")));
        assertNull(Config.path(Collections.emptyList()));
        assertNull(Config.path(null));
    }

    @Test public void onlyHttpsAddresses() {
        assertTrue(Config.validUrl("https://feeds.bbci.co.uk/news/world/rss.xml"));
        assertTrue(Config.validUrl("HTTPS://www.dr.dk/nyheder/service/feeds/senestenyt"));
        assertFalse(Config.validUrl("http://example.com/rss"));
        assertFalse(Config.validUrl("https://"));
        assertFalse(Config.validUrl("https://exa mple.com/rss"));
        assertFalse(Config.validUrl("file:///sdcard/rss.xml"));
        assertFalse(Config.validUrl("example.com/rss"));
        assertFalse(Config.validUrl(null));
    }

    @Test public void feedsSkipCommentsBadLinesAndDuplicates() {
        String text = "# my feeds\n"
                + "https://a.example/rss\n"
                + "\n"
                + "  https://b.example/atom  \r\n"
                + "http://plain.example/rss\n"
                + "https://a.example/rss\n";
        assertEquals(Arrays.asList("https://a.example/rss", "https://b.example/atom"), Config.feeds(text));
    }

    @Test public void feedsMayHaveANameBeforeABar() {
        assertEquals("DR | https://www.dr.dk/rss", Config.feed("  DR|  https://www.dr.dk/rss "));
        assertEquals("Kort nyt | DR | https://www.dr.dk/rss", Config.feed("Kort nyt | DR | https://www.dr.dk/rss"));
        assertEquals("https://www.dr.dk/rss", Config.feed(" | https://www.dr.dk/rss"));
        assertNull(Config.feed("DR | http://www.dr.dk/rss"));
        assertNull(Config.feed("A name much longer than forty characters in all | https://x.example/rss"));
        assertEquals("https://www.dr.dk/rss", Config.url("DR | https://www.dr.dk/rss"));
        assertEquals("DR", Config.name("DR | https://www.dr.dk/rss"));
        assertEquals("", Config.name("https://www.dr.dk/rss"));
        // One line per address: the first one, name and all, wins.
        assertEquals(Arrays.asList("B.T. | https://www.bt.dk/rss", "https://tv2.example/rss"),
                Config.feeds("B.T. | https://www.bt.dk/rss\nhttps://www.bt.dk/rss\nhttps://tv2.example/rss\n"));
    }

    @Test public void noFileMeansTheDefaultFeedAnEmptyFileMeansNone() {
        assertEquals(Config.DEFAULT_FEEDS, Config.feeds((String) null));
        assertTrue(Config.feeds("").isEmpty());
        assertTrue(Config.feeds("# nothing yet\n").isEmpty());
    }

    @Test public void atMostTenFeeds() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 15; i++) text.append("https://f").append(i).append(".example/rss\n");
        List<String> feeds = Config.feeds(text.toString());
        assertEquals(Config.MAX_FEEDS, feeds.size());
        assertEquals("https://f0.example/rss", feeds.get(0));
        assertEquals(feeds, Config.feeds(Config.feedsText(feeds)));
    }

    @Test public void secondsDefaultToFifteen() {
        assertEquals(15, Config.seconds((String) null));
        assertEquals(15, Config.seconds("abc"));
        assertEquals(15, Config.seconds("4"));
        assertEquals(15, Config.seconds("601"));
        assertEquals(5, Config.seconds("5"));
        assertEquals(30, Config.seconds(" 30\n"));
        assertEquals(600, Config.seconds("600"));
    }

    @Test public void secondsStepRoundTheChoices() {
        assertEquals(20, Config.nextSeconds(15));
        assertEquals(8, Config.nextSeconds(120));
        assertEquals(8, Config.nextSeconds(600));
        assertEquals(10, Config.nextSeconds(9));
    }
}
