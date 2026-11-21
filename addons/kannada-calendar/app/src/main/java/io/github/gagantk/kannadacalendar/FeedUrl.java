package io.github.gagantk.kannadacalendar;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the link a calendar's publisher hands out into one this add-on can download. Bangalore Press
 * gives the old Google Calendar feed address, {@code .../calendar/feeds/<id>/private-<key>/basic}, inside an
 * "add to my Google Calendar" link. Google stopped serving that form, and the same calendar is still
 * served as iCalendar from {@code calendar.google.com/calendar/ical/<id>/private-<key>/basic.ics}.
 */
final class FeedUrl {
    private static final Pattern OLD_FEED = Pattern.compile(
            "^https://(?:www\\.)?google\\.com/calendar/feeds/([^/]+)/(private-[0-9a-fA-F]+|public)/(?:basic|full)/?$");

    private FeedUrl() {}

    /** The https address to download, or null when the text is empty or is not a web address we may use. */
    static String normalise(String raw) {
        if (raw == null) return null;
        String url = raw.trim();
        int cid = url.indexOf("cid=");
        if (url.contains("/render") && cid >= 0) {
            url = url.substring(cid + 4);
            int amp = url.indexOf('&');
            if (amp >= 0) url = url.substring(0, amp);
            if (url.startsWith("http%3A") || url.startsWith("https%3A")) {
                try {
                    url = URLDecoder.decode(url, StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    return null;
                }
            }
        }
        if (url.startsWith("webcal://")) url = "https://" + url.substring(9);
        if (url.startsWith("http://")) url = "https://" + url.substring(7);
        if (!url.startsWith("https://") || url.length() > 600 || url.matches(".*[\\s'\"`<>\\\\].*")) return null;
        Matcher old = OLD_FEED.matcher(url);
        if (old.matches()) {
            return "https://calendar.google.com/calendar/ical/" + old.group(1) + "/" + old.group(2) + "/basic.ics";
        }
        return url;
    }
}
