package com.gathin.newsdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FeedParserTest {

    private static FeedParser.Feed parse(String xml) throws IOException {
        return FeedParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "host.example");
    }

    @Test public void rss2() throws IOException {
        FeedParser.Feed feed = parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\"><channel>"
                + "<title>DR Nyheder</title><image><title>DR logo</title></image>"
                + "<link>https://www.dr.dk/</link>"
                + "<item><title>Første &amp; bedste</title><link> https://www.dr.dk/nyheder/a </link>"
                + "<description><![CDATA[<p>Et <b>resumé</b>&nbsp;her</p>]]></description>"
                + "<media:content url=\"https://x/y.jpg\"><media:title>Photo</media:title></media:content>"
                // 25 October 2026 is a Sunday: a wrong weekday must not lose the date.
                + "<pubDate>Sat, 25 Oct 2026 14:05:00 +0200</pubDate></item>"
                + "<item><title>Second</title><pubDate>Sat, 25 Oct 2026 12:00:00 GMT</pubDate></item>"
                + "</channel></rss>");
        assertEquals("DR Nyheder", feed.title);
        assertEquals(2, feed.items.size());
        Headline first = feed.items.get(0);
        assertEquals("DR Nyheder", first.source);
        assertEquals("Første & bedste", first.title);
        assertEquals("Et resumé her", first.summary);
        assertEquals(FeedParser.date("2026-10-25T12:05:00Z"), first.time);
        assertEquals("https://www.dr.dk/nyheder/a", first.link);
        assertEquals("", feed.items.get(1).summary);
        assertTrue(feed.items.get(1).time > 0);
    }

    @Test public void atomWithXhtmlContent() throws IOException {
        FeedParser.Feed feed = parse("<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                + "<title type=\"text\">Example Atom</title>"
                + "<link href=\"https://example.com/\"/>"
                + "<entry><title>Atom entry</title>"
                + "<link rel=\"enclosure\" href=\"https://example.com/a.mp3\"/>"
                + "<link href=\"https://example.com/entry\"/><updated>2026-10-25T10:00:00Z</updated>"
                + "<published>2026-10-25T09:30:00.123456+02:00</published>"
                + "<content type=\"xhtml\"><div xmlns=\"http://www.w3.org/1999/xhtml\"><p>One</p><p>two</p></div></content>"
                + "</entry></feed>");
        assertEquals("Example Atom", feed.title);
        Headline h = feed.items.get(0);
        assertEquals("Atom entry", h.title);
        assertEquals("One two", h.summary);
        assertEquals("https://example.com/entry", h.link);
        assertEquals(FeedParser.date("2026-10-25T07:30:00.123Z"), h.time);
    }

    @Test public void rdfWithDcDate() throws IOException {
        FeedParser.Feed feed = parse("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""
                + " xmlns=\"http://purl.org/rss/1.0/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                + "<channel><title>RDF feed</title></channel>"
                + "<item><title>RDF item</title><dc:date>2026-10-25T08:00:00+00:00</dc:date></item>"
                + "</rdf:RDF>");
        assertEquals("RDF feed", feed.title);
        assertEquals("RDF item", feed.items.get(0).title);
        assertEquals(FeedParser.date("2026-10-25T08:00:00Z"), feed.items.get(0).time);
    }

    @Test public void noFeedTitleUsesTheHostAndUntitledItemsAreSkipped() throws IOException {
        FeedParser.Feed feed = parse("<rss><channel><item><description>no title</description></item>"
                + "<item><title>  Kept  </title></item></channel></rss>");
        assertEquals("host.example", feed.title);
        assertEquals(1, feed.items.size());
        assertEquals("Kept", feed.items.get(0).title);
    }

    @Test public void stopsAfterTwentyItems() throws IOException {
        StringBuilder xml = new StringBuilder("<rss><channel><title>Many</title>");
        for (int i = 0; i < 50; i++) xml.append("<item><title>Item ").append(i).append("</title></item>");
        xml.append("</channel></rss>");
        assertEquals(FeedParser.MAX_ITEMS, parse(xml.toString()).items.size());
    }

    @Test(expected = IOException.class) public void notAFeed() throws IOException {
        parse("<html><body>Not found</body>");
    }

    @Test public void linksAreHttpAndShort() {
        assertEquals("https://a.example/x", FeedParser.link(" https://a.example/x\n"));
        assertEquals("http://a.example/x", FeedParser.link("http://a.example/x"));
        assertEquals("", FeedParser.link("javascript:alert(1)"));
        assertEquals("", FeedParser.link("https://a.example/a b"));
        assertEquals("", FeedParser.link(null));
        StringBuilder longLink = new StringBuilder("https://a.example/");
        while (longLink.length() <= 400) longLink.append('x');
        assertEquals("", FeedParser.link(longLink.toString()));
    }

    @Test public void cleanText() {
        assertEquals("a & b", FeedParser.clean("a &amp;amp; b", 100));
        assertEquals("bold", FeedParser.clean("&lt;b&gt;bold&lt;/b&gt;", 100));
        assertEquals("æøå — é", FeedParser.clean("&aelig;&oslash;&aring; &mdash; &#233;", 100));
        assertEquals("keep &unknown; as is", FeedParser.clean("keep &unknown; as is", 100));
        assertEquals("one two three…", FeedParser.clean("one two three four five six", 16));
        assertEquals("", FeedParser.clean(null, 10));
    }

    @Test public void dates() {
        long t = FeedParser.date("2026-10-25T12:00:00Z");
        assertTrue(t > 0);
        assertEquals(t, FeedParser.date("Sun, 25 Oct 2026 12:00:00 GMT"));
        assertEquals(t, FeedParser.date("Sun, 25 Oct 2026 14:00:00 +0200"));
        assertEquals(t, FeedParser.date("25 Oct 2026 12:00:00 +0000"));
        assertEquals(t, FeedParser.date("2026-10-25T14:00:00+02:00"));
        assertEquals(t + 500, FeedParser.date("2026-10-25T12:00:00.5Z"));
        assertEquals(0, FeedParser.date("yesterday"));
        assertEquals(0, FeedParser.date(""));
        assertEquals(0, FeedParser.date(null));
    }

    @Test public void mixTakesTurnsNewestFirstAndDropsOld() {
        long now = FeedParser.date("2026-10-25T12:00:00Z");
        long hour = 3_600_000L;
        List<Headline> a = Arrays.asList(h("A old", now - 3 * hour), h("A new", now - hour), h("A ancient", now - 72 * hour));
        List<Headline> b = Arrays.asList(h("B undated", 0), h("B new", now - 2 * hour));
        List<Headline> mixed = News.mix(Arrays.asList(a, b, Collections.emptyList()), now);
        List<String> titles = new ArrayList<>();
        for (Headline h : mixed) titles.add(h.title);
        assertEquals(Arrays.asList("A new", "B new", "A old", "B undated"), titles);
    }

    @Test public void mixShowsTheSameHeadlineOnce() {
        List<Headline> eb = Arrays.asList(h("Le Pen lover lov og orden", 0), h("Kun i EB", 0));
        List<Headline> bt = Arrays.asList(h("le pen lover lov og orden", 0), h("Kun i BT", 0));
        List<String> titles = new ArrayList<>();
        for (Headline h : News.mix(Arrays.asList(eb, bt), 0)) titles.add(h.title);
        assertEquals(Arrays.asList("Le Pen lover lov og orden", "Kun i EB", "Kun i BT"), titles);
    }

    @Test public void mixStopsAtSixty() {
        List<List<Headline>> feeds = new ArrayList<>();
        for (int f = 0; f < 4; f++) {
            List<Headline> many = new ArrayList<>();
            for (int i = 0; i < 20; i++) many.add(h("feed " + f + " item " + i, 0));
            feeds.add(many);
        }
        assertEquals(News.MAX_HEADLINES, News.mix(feeds, 0).size());
    }

    private static Headline h(String title, long time) {
        return new Headline("s", title, "", time, "");
    }
}
