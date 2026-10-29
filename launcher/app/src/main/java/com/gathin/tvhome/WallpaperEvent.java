package com.gathin.tvhome;

import java.io.File;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * wallpaper_events: a set of pictures that replaces the rotation between two dates, for Christmas or New Year's Eve,
 * written like wallpaper_moods: {"12-20 to 12-26": ["xmas-001.jpg", ...], "12-31 to 01-01": [...]}. A date is "MM-DD"
 * (every year) or "YYYY-MM-DD" (that one year, for a date that moves like Easter); a key with one date is that day. The
 * end date is inclusive: the event runs through the whole of that day. A yearly event whose start is after its end runs
 * over New Year, so "12-31 to 01-01" is New Year's Eve and New Year's Day. While an event is on, its pictures are the
 * whole rotation, ahead of wallpaper_moods; the first listed wins when two overlap. A picture named in an event is kept
 * out of the rest of the year's rotation.
 */
final class WallpaperEvent {
    private static final Pattern DATE = Pattern.compile("(?:(\\d{4})-)?(\\d{2})-(\\d{2})");
    private static final Pattern TO = Pattern.compile("\\s+to\\s+");
    /** A leap year, so that "02-29" is a valid yearly date. */
    private static final int LEAP_YEAR = 2024;

    /** MMDD for a yearly date, YYYYMMDD for one in a given year; start and end are the same kind. */
    final int start, end;
    final Set<String> names;

    WallpaperEvent(int start, int end, Set<String> names) {
        this.start = start;
        this.end = end;
        this.names = names;
    }

    /** True when the event is on that day, the end date included. */
    boolean covers(LocalDate day) {
        int month = day.getMonthValue() * 100 + day.getDayOfMonth();
        int now = start > 9999 ? day.getYear() * 10000 + month : month;
        return start <= end ? now >= start && now <= end : now >= start || now <= end;
    }

    /** The first event on that day, or null. */
    static WallpaperEvent on(List<WallpaperEvent> events, LocalDate day) {
        for (WallpaperEvent event : events) if (event.covers(day)) return event;
        return null;
    }

    /**
     * The pictures to rotate through on that day when an event is on and has some of the collection's pictures, in the
     * collection's order; null when there is no event, so the usual rotation applies.
     */
    static List<File> today(List<File> all, List<WallpaperEvent> events, LocalDate day) {
        WallpaperEvent event = on(events, day);
        if (event == null) return null;
        List<File> pictures = new ArrayList<>();
        for (File file : all) if (event.names.contains(file.getName())) pictures.add(file);
        return pictures.isEmpty() ? null : pictures;
    }

    /** The collection without any event's pictures, which belong to their dates; all of it when that leaves none. */
    static List<File> without(List<File> all, List<WallpaperEvent> events) {
        if (events.isEmpty()) return all;
        List<File> rest = new ArrayList<>();
        outer:
        for (File file : all) {
            for (WallpaperEvent event : events) if (event.names.contains(file.getName())) continue outer;
            rest.add(file);
        }
        return rest.isEmpty() ? all : rest;
    }

    /** wallpaper_events as JSON text (config.xml); empty or unreadable is no events. */
    static List<WallpaperEvent> parse(String text) {
        if (text.trim().isEmpty()) return Collections.emptyList();
        try {
            return parse(new JSONObject(text));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * {"12-20 to 12-26": ["xmas-001.jpg", ...], ...}, in the order written: each key's dates and its picture names. An
     * event whose key is not a span of dates (see {@link #span}) or that has no pictures is left out.
     */
    static List<WallpaperEvent> parse(JSONObject o) {
        List<WallpaperEvent> events = new ArrayList<>();
        for (Iterator<String> keys = o.keys(); keys.hasNext(); ) {
            String key = keys.next();
            int[] dates = span(key);
            JSONArray list = o.optJSONArray(key);
            if (dates == null || list == null) continue;
            Set<String> names = new HashSet<>();
            for (int i = 0; i < list.length(); i++) {
                String name = list.optString(i, "").trim();
                if (ThemeFiles.collectionImage(name)) names.add(name);
            }
            if (!names.isEmpty()) events.add(new WallpaperEvent(dates[0], dates[1], Collections.unmodifiableSet(names)));
        }
        return Collections.unmodifiableList(events);
    }

    /**
     * An event's key as {start, end}: "12-20 to 12-26", or one date for a single day. Null when a date is not one, when
     * the two are of different kinds ("12-20 to 2026-12-26"), or when the dates of one year are the wrong way round.
     */
    static int[] span(String key) {
        String[] parts = TO.split(key.trim(), -1);
        if (parts.length > 2) return null;
        int start = date(parts[0]), end = parts.length == 2 ? date(parts[1]) : start;
        if (start < 0 || end < 0 || (start > 9999) != (end > 9999) || (start > 9999 && start > end)) return null;
        return new int[]{start, end};
    }

    /** "MM-DD" as MMDD, "YYYY-MM-DD" as YYYYMMDD; -1 for anything else, including a day the calendar does not have. */
    static int date(String text) {
        Matcher m = DATE.matcher(text.trim());
        if (!m.matches()) return -1;
        int month = Integer.parseInt(m.group(2)), day = Integer.parseInt(m.group(3));
        try {
            LocalDate.of(m.group(1) == null ? LEAP_YEAR : Integer.parseInt(m.group(1)), month, day);
        } catch (DateTimeException e) {
            return -1;
        }
        return (m.group(1) == null ? 0 : Integer.parseInt(m.group(1)) * 10000) + month * 100 + day;
    }
}
