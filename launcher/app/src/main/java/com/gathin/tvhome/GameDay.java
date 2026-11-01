package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * game_day (config.xml or a theme): each team's next game in the race panel's place, from an add-on such as
 * Game Day, rotating with the theme's F1 weekend. The launcher only reads what the add-on publishes and stays
 * offline. Contract: docs/modules.md.
 */
final class GameDay {
    static final String META = "com.gathin.tvhome.games";
    static final int TURN_DEFAULT = 30;
    private static final String[] COLUMNS = {"top_line", "word", "start_ms", "end_ms", "colour", "calendar", "turn_seconds"};

    private final Context ctx;
    private final Uri uri;
    private final Handler main;
    private final Runnable onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private boolean watching;
    private List<Turn> turns = Collections.emptyList();
    private int turnSeconds = TURN_DEFAULT;

    private GameDay(Context ctx, Uri uri, Handler main, Runnable onChange) {
        this.ctx = ctx;
        this.uri = uri;
        this.main = main;
        this.onChange = onChange;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };
    }

    /**
     * Null when the key is empty, the package is not installed, or it names no provider of its own in a
     * {@value #META} meta-data. Naming the package is the owner's opt-in.
     */
    static GameDay create(Context ctx, String pkg, Handler main, Runnable onChange) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            // The provider must be the named package's own, not another app's that claims the authority.
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim()).appendPath("turns").build();
            return new GameDay(ctx, uri, main, onChange);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Home is on screen: read now and watch for changes. */
    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on's provider went away; the panel keeps what it had.
            }
        }
        read();
    }

    /** Home is off screen: nothing runs until it is back. */
    void stop() {
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    /** One query off the main thread (the provider lives in the add-on's process), then onChange on the main thread. */
    void read() {
        try {
            io.execute(() -> {
                List<Object[]> rows = new ArrayList<>();
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        rows.add(new Object[] {c.getString(0), c.getString(1), c.getLong(2), c.getLong(3), c.getInt(4),
                                c.getString(5), c.getInt(6)});
                    }
                } catch (RuntimeException gone) {
                    // Uninstalled or crashed mid-query: keep what is on screen.
                    return;
                }
                List<Turn> parsed = fromRows(rows);
                int seconds = turnSeconds(rows);
                main.post(() -> {
                    turns = parsed;
                    turnSeconds = seconds;
                    onChange.run();
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    List<Turn> turns() {
        return turns;
    }

    int turnSeconds() {
        return turnSeconds;
    }

    /** The add-on's rows as turns; a row without a top line or with impossible times is skipped. */
    static List<Turn> fromRows(List<Object[]> rows) {
        List<Turn> out = new ArrayList<>();
        for (Object[] r : rows) {
            if (!(r[0] instanceof String) || ((String) r[0]).isEmpty()) continue;
            long start = r[2] instanceof Number ? ((Number) r[2]).longValue() : -1;
            long end = r[3] instanceof Number ? ((Number) r[3]).longValue() : -1;
            if (start < 0 || end <= start) continue;
            String word = r[1] instanceof String ? (String) r[1] : "";
            int colour = r[4] instanceof Number ? ((Number) r[4]).intValue() : 0xFFFFFFFF;
            out.add(new Turn((String) r[0], word, start, end, colour));
        }
        return out;
    }

    /** 10 to 120 seconds per turn, as the add-on's settings allow; anything else is the default. */
    static int turnSeconds(List<Object[]> rows) {
        if (rows.isEmpty() || !(rows.get(0)[6] instanceof Number)) return TURN_DEFAULT;
        int s = ((Number) rows.get(0)[6]).intValue();
        return s >= 10 && s <= 120 ? s : TURN_DEFAULT;
    }

    /** One turn in the rotation: a game from the add-on, or the theme's F1 weekend ({@link #f1}). */
    static final class Turn {
        final String topLine;
        /** "Puck drop": the countdown reads "Puck drop in 2h 18m". */
        final String word;
        /** For the F1 turn: when its next session starts, or now while one is live (the sort key). */
        final long startMs;
        final long endMs;
        /** ARGB, for the short bar. */
        final int colour;
        final boolean f1;

        Turn(String topLine, String word, long startMs, long endMs, int colour) {
            this(topLine, word, startMs, endMs, colour, false);
        }

        private Turn(String topLine, String word, long startMs, long endMs, int colour, boolean f1) {
            this.topLine = topLine;
            this.word = word;
            this.startMs = startMs;
            this.endMs = endMs;
            this.colour = colour;
            this.f1 = f1;
        }
    }

    /**
     * The turns to rotate through, soonest first: the games that have not ended, and the F1 weekend as one turn
     * while the race panel would show it (sorted by its next session, or now while a session is live).
     */
    static List<Turn> merge(List<Turn> games, RaceWeekend.State f1, long nowMs) {
        List<Turn> out = new ArrayList<>();
        for (Turn t : games) {
            if (t.endMs > nowMs) out.add(t);
        }
        if (f1 != null) {
            long key = f1.live == null && f1.next != null ? f1.next.start.toEpochMilli() : nowMs;
            out.add(new Turn("", "", key, Long.MAX_VALUE, 0, true));
        }
        Collections.sort(out, Comparator.comparingLong(t -> t.startMs));
        return out;
    }

    /** Which turn is on screen, {@code sinceMs} after the rotation started. */
    static int current(int count, long sinceMs, int turnSeconds) {
        if (count <= 0) return 0;
        return (int) ((Math.max(0, sinceMs) / (turnSeconds * 1000L)) % count);
    }

    /** Milliseconds until the next turn, {@code sinceMs} after the rotation started. */
    static long delayToNextTurn(long sinceMs, int turnSeconds) {
        long turnMs = turnSeconds * 1000L;
        return turnMs - Math.floorMod(sinceMs, turnMs);
    }

    static boolean live(Turn t, long nowMs) {
        return nowMs >= t.startMs && nowMs < t.endMs;
    }

    static Duration until(Turn t, long nowMs) {
        return Duration.ofMillis(t.startMs - nowMs);
    }
}
