package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * dock_most_used: the dock holds the apps Home opened most in the last WINDOW_DAYS days, today included. Home counts
 * its own launches, with no permission and nothing read from other apps, so an app started some other way (a remote's
 * app button) is not counted. Only package names and launch counts per day are stored, in this app's own preferences.
 * The ranking is worked out once per RANK_HOURS period of the local day (00:00, 08:00 and 16:00), so the dock stays
 * put while the TV is in use. Until anything is counted the dock is dock_apps; a window with no launches from Home
 * keeps the last ranking.
 */
final class MostUsed {
    private static final String PREFS = "most_used";
    /** count:&lt;epoch day&gt;:&lt;package&gt; holds that day's launches of the app. */
    private static final String COUNT = "count:";
    /** The period the saved dock was ranked in (period()). */
    private static final String PERIOD = "period";
    private static final String DOCK = "dock";
    /** sort_by_use: every app the window counted, highest first, then dock_apps, for the whole grid (ranked()). */
    private static final String ALL = "all";
    /** The dock size the saved ranking was cut to: a new size (columns) ranks again at once. */
    private static final String SIZE = "size";
    static final int WINDOW_DAYS = 7;
    static final int RANK_HOURS = 8;

    private MostUsed() {
    }

    /** Notes that Home just opened this app. */
    static void record(Context ctx, String pkg) {
        SharedPreferences p = prefs(ctx);
        String key = countKey(today(), pkg);
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply();
    }

    /** The dock: the ranking saved this period, or a new one from the window's launches, kept for the period. */
    static List<String> dock(Context ctx, List<String> dockApps, int size) {
        SharedPreferences p = prefs(ctx);
        long today = today(), now = period();
        String saved = p.getString(DOCK, "");
        if (p.getLong(PERIOD, -1L) == now && p.getInt(SIZE, -1) == size && !saved.isEmpty()) {
            return Arrays.asList(saved.split(","));
        }
        Map<String, ?> stored = p.getAll();
        SharedPreferences.Editor ed = p.edit();
        for (String key : stored.keySet()) {
            if (key.startsWith(COUNT) && dayOf(key) <= today - WINDOW_DAYS) ed.remove(key);
        }
        Map<String, Float> counts = counts(stored, today);
        List<String> base = dockApps;
        if (counts.isEmpty() && !saved.isEmpty()) {
            base = new ArrayList<>(Arrays.asList(saved.split(",")));
            for (String pkg : dockApps) if (!base.contains(pkg)) base.add(pkg);
        }
        List<String> all = rank(counts, base, Integer.MAX_VALUE);
        List<String> dock = new ArrayList<>(all.subList(0, Math.min(size, all.size())));
        ed.putLong(PERIOD, now).putInt(SIZE, size).putString(DOCK, String.join(",", dock))
                .putString(ALL, String.join(",", all)).apply();
        return dock;
    }

    /** sort_by_use: the period's ranking of every counted app, highest first, saved by the last dock() call. */
    static List<String> ranked(Context ctx) {
        String all = prefs(ctx).getString(ALL, "");
        return all.isEmpty() ? new ArrayList<>() : Arrays.asList(all.split(","));
    }

    /** Launches per app over the last WINDOW_DAYS days up to today, from the stored count: entries. */
    static Map<String, Float> counts(Map<String, ?> stored, long today) {
        Map<String, Float> out = new HashMap<>();
        for (Map.Entry<String, ?> e : stored.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(COUNT) || !(e.getValue() instanceof Integer)) continue;
            long day = dayOf(key);
            if (day <= today - WINDOW_DAYS || day > today) continue;
            String pkg = key.substring(key.indexOf(':', COUNT.length()) + 1);
            out.merge(pkg, (float) (Integer) e.getValue(), Float::sum);
        }
        return out;
    }

    /** The day in a count: key, or Long.MIN_VALUE (outside every window) when the key is malformed. */
    private static long dayOf(String key) {
        int end = key.indexOf(':', COUNT.length());
        try {
            return end < 0 ? Long.MIN_VALUE : Long.parseLong(key.substring(COUNT.length(), end));
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    static String countKey(long day, String pkg) {
        return COUNT + day + ":" + pkg;
    }

    /** Scored apps, highest first (a tie keeps the dock_apps order), then the rest of dock_apps, cut to size. */
    static List<String> rank(Map<String, Float> scores, List<String> dockApps, int size) {
        List<String> ranked = new ArrayList<>(scores.keySet());
        ranked.sort((a, b) -> {
            int byScore = Float.compare(scores.get(b), scores.get(a));
            if (byScore != 0) return byScore;
            int ia = dockApps.indexOf(a), ib = dockApps.indexOf(b);
            if (ia != ib) return Integer.compare(ia < 0 ? Integer.MAX_VALUE : ia, ib < 0 ? Integer.MAX_VALUE : ib);
            return a.compareTo(b);
        });
        for (String pkg : dockApps) if (!ranked.contains(pkg)) ranked.add(pkg);
        return new ArrayList<>(ranked.subList(0, Math.min(size, ranked.size())));
    }

    static long today() {
        return LocalDate.now().toEpochDay();
    }

    /** The current ranking period: RANK_HOURS-long slices of the local day, numbered on from the epoch. */
    static long period() {
        return period(LocalDateTime.now());
    }

    static long period(LocalDateTime t) {
        return t.toLocalDate().toEpochDay() * (24 / RANK_HOURS) + t.getHour() / RANK_HOURS;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
