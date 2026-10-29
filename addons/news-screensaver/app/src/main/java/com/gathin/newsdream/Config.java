package com.gathin.newsdream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The two settings, each a small text file in the app's files dir: feeds.txt (one HTTPS feed address per line,
 * optionally after a name and a bar, "Ekstra Bladet | https://...", # for comments) and seconds.txt (how long a headline stays up). setup.sh writes them through FilesProvider,
 * SettingsActivity on the TV. No feeds.txt yet: the default feed.
 */
final class Config {
    static final String FEEDS = "feeds.txt";
    static final String SECONDS = "seconds.txt";
    static final int MAX_FEEDS = 10;
    static final int DEFAULT_SECONDS = 15;
    static final int MIN_SECONDS = 5;
    static final int MAX_SECONDS = 600;
    static final int MAX_NAME = 40;
    /** The choices SettingsActivity steps through. */
    static final int[] SECONDS_CHOICES = {8, 10, 15, 20, 30, 45, 60, 120};
    static final List<String> DEFAULT_FEEDS =
            Collections.singletonList("https://feeds.bbci.co.uk/news/world/rss.xml");

    private Config() {}

    /** The file a provider path names: only feeds.txt and seconds.txt, nothing else. */
    static String path(List<String> segments) {
        if (segments == null || segments.size() != 1) return null;
        String name = segments.get(0);
        return FEEDS.equals(name) || SECONDS.equals(name) ? name : null;
    }

    /** An absolute https:// address with a host and no spaces. Plain http is refused (Android blocks it too). */
    static boolean validUrl(String url) {
        if (url == null || url.length() > 500 || !url.toLowerCase(Locale.ROOT).startsWith("https://")) return false;
        for (int i = 0; i < url.length(); i++) {
            if (Character.isWhitespace(url.charAt(i)) || Character.isISOControl(url.charAt(i))) return false;
        }
        try {
            URI uri = new URI(url);
            return uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * One feed line tidied: "Name | https://..." or just the address; null when the address is not https or the
     * name too long. The last bar splits them, so a name may hold one ("Kort nyt | DR | https://...").
     */
    static String feed(String line) {
        if (line == null) return null;
        String text = line.trim();
        int bar = text.lastIndexOf('|');
        String name = bar < 0 ? "" : text.substring(0, bar).trim();
        String url = bar < 0 ? text : text.substring(bar + 1).trim();
        if (!validUrl(url) || name.length() > MAX_NAME) return null;
        for (int i = 0; i < name.length(); i++) if (Character.isISOControl(name.charAt(i))) return null;
        return name.isEmpty() ? url : name + " | " + url;
    }

    /** The address in a tidied feed line. */
    static String url(String feed) {
        return feed.substring(feed.lastIndexOf('|') + 1).trim();
    }

    /** The name in a tidied feed line, or "" to use the feed's own title. */
    static String name(String feed) {
        int bar = feed.lastIndexOf('|');
        return bar < 0 ? "" : feed.substring(0, bar).trim();
    }

    /** The feeds in feeds.txt's text, in order, one per address, at most ten; the default when there is no file. */
    static List<String> feeds(String text) {
        if (text == null) return DEFAULT_FEEDS;
        List<String> feeds = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        for (String line : text.split("\n")) {
            if (line.trim().startsWith("#")) continue;
            String feed = feed(line);
            if (feed == null || !urls.add(url(feed))) continue;
            feeds.add(feed);
            if (feeds.size() == MAX_FEEDS) break;
        }
        return feeds;
    }

    static String feedsText(List<String> feeds) {
        StringBuilder text = new StringBuilder();
        for (String feed : feeds) text.append(feed).append('\n');
        return text.toString();
    }

    /** seconds.txt's number, 5 to 600; 15 when it is missing or not a number. */
    static int seconds(String text) {
        if (text == null) return DEFAULT_SECONDS;
        try {
            int s = Integer.parseInt(text.trim());
            return s < MIN_SECONDS || s > MAX_SECONDS ? DEFAULT_SECONDS : s;
        } catch (NumberFormatException e) {
            return DEFAULT_SECONDS;
        }
    }

    /** The next choice after {@code seconds}, round to the first. */
    static int nextSeconds(int seconds) {
        for (int choice : SECONDS_CHOICES) if (choice > seconds) return choice;
        return SECONDS_CHOICES[0];
    }

    static List<String> feeds(File dir) {
        return feeds(read(new File(dir, FEEDS)));
    }

    static int seconds(File dir) {
        return seconds(read(new File(dir, SECONDS)));
    }

    /** A small text file, or null when it is missing or unreadable. */
    static String read(File file) {
        if (!file.isFile() || file.length() > 64 * 1024) return null;
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    static void write(File file, String text) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // The settings screen shows what is on disk again, so a failed write is visible there.
        }
    }
}
