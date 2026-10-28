package io.github.justinnewman.gameday;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Calendar links. Many are secrets (Google Calendar's "secret address in iCal format" lets anyone who has it read
 * the calendar), so a link is only ever shown masked, never logged and never written outside the app's own files.
 */
final class Links {
    static final int MAX_LENGTH = 2000;

    private Links() {
    }

    /** A fetchable https:// or http:// link: trimmed, webcal:// read as https://; null when it is not a link. */
    static String normalise(String raw) {
        if (raw == null) return null;
        String link = raw.trim();
        if (link.isEmpty() || link.length() > MAX_LENGTH) return null;
        String lower = link.toLowerCase(Locale.ROOT);
        if (lower.startsWith("webcal://")) link = "https://" + link.substring("webcal://".length());
        else if (lower.startsWith("https://")) link = "https://" + link.substring("https://".length());
        else if (lower.startsWith("http://")) link = "http://" + link.substring("http://".length());
        else return null;
        try {
            String host = new URI(link).getHost();
            return host == null || host.isEmpty() ? null : link;
        } catch (URISyntaxException bad) {
            return null;
        }
    }

    /** "calendar.google.com/…": enough to recognise a link, never enough to use it. */
    static String mask(String link) {
        try {
            String host = link == null ? null : new URI(link).getHost();
            return host == null ? "…" : host + "/…";
        } catch (URISyntaxException bad) {
            return "…";
        }
    }

    /** A short id that stays the same for the same link and cannot be turned back into it. */
    static String id(String link) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(link.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 4; i++) b.append(String.format(Locale.ROOT, "%02x", hash[i]));
            return b.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
