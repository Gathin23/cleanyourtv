package com.gathin.homedashboard;

/** One item from a feed, cleaned for the screen: plain text, no HTML. */
final class Headline {
    final String source;
    final String title;
    final String summary;
    /** When it was published, in ms since 1970; 0 when the feed does not say. */
    final long time;
    /** The article's address; empty when the feed has none. */
    final String link;

    Headline(String source, String title, String summary, long time, String link) {
        this.source = source;
        this.title = title;
        this.summary = summary;
        this.time = time;
        this.link = link;
    }
}
