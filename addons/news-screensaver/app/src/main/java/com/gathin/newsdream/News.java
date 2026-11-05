package com.gathin.newsdream;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fetches the feeds and deals their headlines into one list, taking turns between the feeds so a busy one
 * doesn't crowd out the rest. The last good list is kept in a cache file, so the screensaver has something to
 * show at once, and with no network.
 */
final class News {
    static final long REFRESH_MS = 15 * 60_000L;
    static final long MAX_AGE_MS = 48 * 3_600_000L;
    static final int MAX_HEADLINES = 60;
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final String CACHE = "cache.bin";
    private static final int CACHE_VERSION = 2;

    private News() {}

    /**
     * Every feed's headlines, mixed; blocking, so call it off the main thread. Feeds that fail are skipped. A feed
     * line with a name ("DR | https://...") shows that name instead of the feed's own title.
     */
    static List<Headline> fetch(List<String> feedLines, long now) {
        List<List<Headline>> feeds = new ArrayList<>();
        for (String feed : feedLines) {
            if (Thread.currentThread().isInterrupted()) break;
            String url = Config.url(feed);
            String name = Config.name(feed);
            try {
                List<Headline> items = fetch(url).items;
                if (!name.isEmpty()) items = renamed(items, name);
                feeds.add(items);
            } catch (IOException | RuntimeException e) {
                // One feed down, the others still show. `adb logcat -s TvNews` says why.
                Log.w("TvNews", url + ": " + e);
            }
        }
        return mix(feeds, now);
    }

    private static List<Headline> renamed(List<Headline> items, String source) {
        List<Headline> renamed = new ArrayList<>(items.size());
        for (Headline h : items) renamed.add(new Headline(source, h.title, h.summary, h.time, h.link));
        return renamed;
    }

    static FeedParser.Feed fetch(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "cleanyourtv-news/1.0 (Android TV screensaver)");
        connection.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml");
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (InputStream in = new Capped(connection.getInputStream(), MAX_BYTES)) {
                return FeedParser.parse(in, connection.getURL().getHost());
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * One list from many: each feed newest first, older than two days dropped (undated items are kept), then one
     * from each feed in turn, at most {@link #MAX_HEADLINES}. A headline another feed already gave is left out.
     */
    static List<Headline> mix(List<List<Headline>> feeds, long now) {
        List<List<Headline>> lists = new ArrayList<>();
        for (List<Headline> feed : feeds) {
            List<Headline> fresh = new ArrayList<>();
            for (Headline h : feed) if (h.time == 0 || now - h.time <= MAX_AGE_MS) fresh.add(h);
            // Stable: undated items keep the feed's own order, after the dated ones.
            fresh.sort(Comparator.comparingLong((Headline h) -> h.time == 0 ? Long.MIN_VALUE : h.time).reversed());
            if (!fresh.isEmpty()) lists.add(fresh);
        }
        List<Headline> mixed = new ArrayList<>();
        // The same agency story often runs word for word in several papers: show it once.
        Set<String> seen = new HashSet<>();
        for (int i = 0; mixed.size() < MAX_HEADLINES; i++) {
            boolean any = false;
            for (List<Headline> list : lists) {
                if (i < list.size() && mixed.size() < MAX_HEADLINES) {
                    any = true;
                    if (seen.add(list.get(i).title.toLowerCase(Locale.ROOT))) mixed.add(list.get(i));
                }
            }
            if (!any) break;
        }
        return mixed;
    }

    static void save(File dir, List<Headline> headlines) {
        File tmp = new File(dir, CACHE + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp))) {
            out.writeInt(CACHE_VERSION);
            out.writeInt(headlines.size());
            for (Headline h : headlines) {
                out.writeUTF(h.source);
                out.writeUTF(h.title);
                out.writeUTF(h.summary);
                out.writeLong(h.time);
                out.writeUTF(h.link);
            }
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(new File(dir, CACHE))) tmp.delete();
    }

    /** The cached list, or an empty one. */
    static List<Headline> load(File dir) {
        File file = new File(dir, CACHE);
        if (!file.isFile()) return Collections.emptyList();
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readInt() != CACHE_VERSION) return Collections.emptyList();
            int n = Math.min(in.readInt(), MAX_HEADLINES);
            List<Headline> list = new ArrayList<>(Math.max(n, 0));
            for (int i = 0; i < n; i++) list.add(new Headline(in.readUTF(), in.readUTF(), in.readUTF(), in.readLong(), in.readUTF()));
            return list;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    static long cacheTime(File dir) {
        return new File(dir, CACHE).lastModified();
    }

    static void clearCache(File dir) {
        new File(dir, CACHE).delete();
    }

    /** Stops reading after {@code max} bytes, so a huge or endless reply can't fill the memory. */
    private static final class Capped extends FilterInputStream {
        private long left;

        Capped(InputStream in, long max) {
            super(in);
            left = max;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) throw new IOException("feed too large");
            int b = super.read();
            if (b >= 0) left--;
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            if (left <= 0) throw new IOException("feed too large");
            int n = super.read(buffer, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }
    }
}
