package io.github.gagantk.kannadacalendar;

import android.content.Context;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The only file this add-on keeps: the days of the calendar between a month ago and fourteen months ahead,
 * one tab-separated line per event. It is filled from the owner's own link and stays on the TV.
 */
final class Cache {
    static final long MAX_AGE_MS = 30L * 24 * 3600 * 1000;
    static final int DAYS_BEFORE = 31, DAYS_AFTER = 430;
    private static final long MAX_BYTES = 40L << 20;

    private Cache() {}

    static File file(Context c) { return new File(c.getFilesDir(), "days.tsv"); }

    /** The link the owner pushed with setup.sh: the first line of feed.txt in the app's own folder. */
    static File linkFile(Context c) {
        File dir = c.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "feed.txt");
    }

    static String link(Context c) {
        File f = linkFile(c);
        if (f == null || !f.isFile() || f.length() > 4096) return null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            return FeedUrl.normalise(r.readLine());
        } catch (IOException e) {
            return null;
        }
    }

    /** True when nothing is saved, it is a month old or from an older version, it misses today, or the link is newer. */
    static boolean stale(Context c, Book book, LocalDate today) {
        File f = file(c);
        if (!f.isFile() || book == null || book.legacy || !book.covers(today)) return true;
        if (System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) return true;
        File link = linkFile(c);
        return link != null && link.isFile() && link.lastModified() > f.lastModified();
    }

    static Book load(Context c) {
        File f = file(c);
        if (!f.isFile()) return null;
        List<Entry> entries = new ArrayList<>();
        boolean legacy = false;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length == 4) legacy = true;
                if (p.length != 4 && p.length != 5) continue;
                try {
                    entries.add(new Entry(LocalDate.parse(p[0]), p[1], unesc(p[2]), unesc(p[3]), p.length == 5 ? p[4] : ""));
                } catch (RuntimeException e) {
                    // a damaged line is skipped, the rest still reads
                }
            }
        } catch (IOException e) {
            return null;
        }
        Book book = new Book(entries);
        book.legacy = legacy;
        return book;
    }

    /** Downloads the feed over https, keeps the window around today and saves it. */
    static Book download(Context c, String url, LocalDate today) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setConnectTimeout(20000);
        http.setReadTimeout(60000);
        http.setInstanceFollowRedirects(true);
        http.setRequestProperty("Accept", "text/calendar");
        List<Entry> entries = new ArrayList<>();
        try {
            if (http.getResponseCode() != 200) throw new IOException("HTTP " + http.getResponseCode());
            if (http.getContentLengthLong() > MAX_BYTES) throw new IOException("feed too large");
            try (InputStream in = new LimitedStream(http.getInputStream(), MAX_BYTES)) {
                IcsReader.read(in, today.minusDays(DAYS_BEFORE), today.plusDays(DAYS_AFTER), entries);
            }
        } finally {
            http.disconnect();
        }
        if (entries.isEmpty()) throw new IOException("no days in the window");
        File tmp = new File(c.getFilesDir(), "days.tsv.tmp");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            for (Entry e : entries) {
                w.write(e.date + "\t" + e.time + "\t" + esc(e.summary) + "\t" + esc(e.description) + "\t" + e.holiday + "\n");
            }
        }
        if (!tmp.renameTo(file(c))) throw new IOException("could not save");
        return new Book(entries);
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t");
    }

    static String unesc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** Stops a feed that never ends from filling the TV's storage or memory. */
    private static final class LimitedStream extends FilterInputStream {
        private long left;

        LimitedStream(InputStream in, long limit) {
            super(in);
            left = limit;
        }

        @Override public int read() throws IOException {
            int b = super.read();
            if (b >= 0 && --left < 0) throw new IOException("feed too large");
            return b;
        }

        @Override public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0 && (left -= n) < 0) throw new IOException("feed too large");
            return n;
        }
    }
}
