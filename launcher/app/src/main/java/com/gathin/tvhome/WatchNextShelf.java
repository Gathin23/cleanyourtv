package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.Handler;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The apps' Continue watching rows (TvContract.WatchNextPrograms) for the top shelf's cards (config.xml,
 * continue_watching). Read on one background thread when Home resumes and when the table changes; the filtering and
 * grouping are plain Java (select) so a JVM test covers them. docs/watch-next.md has the provider's rules: no
 * selection (a caller without ACCESS_ALL_EPG_DATA gets a SecurityException) and a named projection.
 */
final class WatchNextShelf {

    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final int MAX_PER_APP = 8;
    /** With shelf_previews, an app's cards in all: its Continue watching first, then its preview programs. */
    static final int MAX_WITH_PREVIEWS = 16;
    /** A row last engaged longer ago than this is stale (rows linger for months on some apps). */
    static final long MAX_AGE_MS = 60L * 24 * 60 * 60 * 1000;
    /** A "continue" row this far into its video is as good as finished. */
    static final float DONE_AT = 0.95f;
    private static final long DEBOUNCE_MS = 500;

    /** watch_next_type 0: the viewer stopped part-way, as against 1 (next episode) and 2 (new). */
    private static final int TYPE_CONTINUE = 0;

    private static final String[] PROJECTION = {
            TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
            TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
            TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
            TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_TYPE,
            TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION,
    };

    /** A preview program's columns (shelf_previews), in the order previewItem reads them. */
    private static final String[] PREVIEW_PROJECTION = {
            TvContract.PreviewPrograms.COLUMN_TITLE,
            TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,
            TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.PreviewPrograms.COLUMN_INTENT_URI,
            TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
            TvContract.PreviewPrograms.COLUMN_TYPE,
            TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
            TvContract.PreviewPrograms.COLUMN_WEIGHT,
            TvContract.PreviewPrograms.COLUMN_BROWSABLE,
    };

    /** One row of the table, as the cards need it. Unknown numbers are -1, unknown texts null. */
    static final class Item {
        final String pkg, title, episodeTitle, season, episode, poster, thumbnail, intentUri, description;
        final int watchNextType;
        final long engagedMs, positionMs, durationMs;
        final int mediaType;

        Item(String pkg, String title, String episodeTitle, String season, String episode, String poster,
             String thumbnail, String intentUri, int watchNextType, long engagedMs, long positionMs,
             long durationMs, int mediaType, String description) {
            this.pkg = pkg;
            this.title = title;
            this.episodeTitle = episodeTitle;
            this.season = season;
            this.episode = episode;
            this.poster = poster;
            this.thumbnail = thumbnail;
            this.intentUri = intentUri;
            this.watchNextType = watchNextType;
            this.engagedMs = engagedMs;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.mediaType = mediaType;
            this.description = description;
        }

        /** Share of the video watched, 0 to 1, or -1 when the app did not say. */
        float progress() {
            if (durationMs <= 0 || positionMs < 0) return -1f;
            return Math.min(1f, (float) positionMs / durationMs);
        }

        /** The artwork to draw: the poster art, else the thumbnail; null when there is neither. */
        String art() {
            return !isEmpty(poster) ? poster : !isEmpty(thumbnail) ? thumbnail : null;
        }

        /** "S1 · E4", "E4" or "S1", or null for a film (no display numbers). */
        String episodeLabel() {
            boolean s = !isEmpty(season), e = !isEmpty(episode);
            if (s && e) return "S" + season.trim() + " · E" + episode.trim();
            if (e) return "E" + episode.trim();
            if (s) return "S" + season.trim();
            return null;
        }

        /** "Movie", "Series" or null: the row's type column. Episodes and seasons count as a series. */
        String kind() {
            switch (mediaType) {
                case TvContract.PreviewPrograms.TYPE_MOVIE:
                    return "Movie";
                case TvContract.PreviewPrograms.TYPE_TV_SERIES:
                case TvContract.PreviewPrograms.TYPE_TV_SEASON:
                case TvContract.PreviewPrograms.TYPE_TV_EPISODE:
                    return "Series";
                default:
                    return null;
            }
        }

        /** Browse mode's small line over the title: "Series \u00B7 S2 \u00B7 E132", "Movie", or null. */
        String meta() {
            String kind = kind(), label = episodeLabel();
            if (kind != null && label != null) return kind + " \u00B7 " + label;
            return kind != null ? kind : label;
        }

        /** Browse mode's text under the title: the episode's title, then the description on the next line. */
        String detail() {
            boolean t = !isEmpty(episodeTitle), d = !isEmpty(description);
            if (t && d) return episodeTitle.trim() + "\n" + description.trim();
            if (t) return episodeTitle.trim();
            return d ? description.trim() : null;
        }

        /** The card's second line: the episode label, then the episode's own title when there is one. */
        String subtitle() {
            String label = episodeLabel();
            boolean t = !isEmpty(episodeTitle);
            if (label != null && t) return label + " · " + episodeTitle.trim();
            if (label != null) return label;
            return t ? episodeTitle.trim() : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Item)) return false;
            Item i = (Item) o;
            return watchNextType == i.watchNextType && engagedMs == i.engagedMs && positionMs == i.positionMs
                    && durationMs == i.durationMs && mediaType == i.mediaType && Objects.equals(pkg, i.pkg)
                    && Objects.equals(title, i.title) && Objects.equals(episodeTitle, i.episodeTitle)
                    && Objects.equals(season, i.season) && Objects.equals(episode, i.episode)
                    && Objects.equals(poster, i.poster) && Objects.equals(thumbnail, i.thumbnail)
                    && Objects.equals(intentUri, i.intentUri) && Objects.equals(description, i.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pkg, title, episodeTitle, season, episode, poster, thumbnail, intentUri,
                    watchNextType, engagedMs, positionMs, durationMs, mediaType, description);
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * What the shelf shows per app: rows without a deep link or a title dropped, rows last engaged over MAX_AGE_MS ago
     * (when the app set a time) dropped, "continue" rows at DONE_AT or more dropped; then newest engagement first and
     * at most MAX_PER_APP each, grouped by package in the order the newest row of each appears.
     */
    static Map<String, List<Item>> select(List<Item> rows, long nowMs) {
        List<Item> kept = new ArrayList<>();
        for (Item r : rows) {
            if (isEmpty(r.pkg) || isEmpty(r.intentUri) || (isEmpty(r.title) && isEmpty(r.episodeTitle))) continue;
            if (r.engagedMs > 0 && nowMs - r.engagedMs > MAX_AGE_MS) continue;
            if (r.watchNextType == TYPE_CONTINUE && r.progress() >= DONE_AT) continue;
            kept.add(r);
        }
        Collections.sort(kept, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(b.engagedMs, a.engagedMs);   // stable: rows without a time keep the table's order
            }
        });
        Map<String, List<Item>> byApp = new LinkedHashMap<>();
        for (Item r : kept) {
            List<Item> list = byApp.get(r.pkg);
            if (list == null) {
                list = new ArrayList<>();
                byApp.put(r.pkg, list);
            }
            if (list.size() < MAX_PER_APP) list.add(r);
        }
        return byApp;
    }

    /**
     * shelf_previews: each app's Continue watching (from select) followed by its preview programs, a program whose
     * deep link a card already has left out, at most MAX_WITH_PREVIEWS cards; apps in hidden are dropped. Apps with
     * Continue watching keep their place, apps with only previews follow in the order given.
     */
    static Map<String, List<Item>> merge(Map<String, List<Item>> watchNext, Map<String, List<Item>> previews,
                                         Collection<String> hidden) {
        Map<String, List<Item>> out = new LinkedHashMap<>();
        Set<String> pkgs = new java.util.LinkedHashSet<>(watchNext.keySet());
        pkgs.addAll(previews.keySet());
        for (String pkg : pkgs) {
            if (hidden.contains(pkg)) continue;
            List<Item> list = new ArrayList<>();
            Set<String> links = new HashSet<>();
            for (List<Item> source : java.util.Arrays.asList(watchNext.get(pkg), previews.get(pkg))) {
                if (source == null) continue;
                for (Item it : source) {
                    if (list.size() >= MAX_WITH_PREVIEWS) break;
                    if (isEmpty(it.intentUri) || (isEmpty(it.title) && isEmpty(it.episodeTitle))) continue;
                    if (links.add(it.intentUri)) list.add(it);
                }
            }
            if (!list.isEmpty()) out.put(pkg, Collections.unmodifiableList(list));
        }
        return out;
    }

    /**
     * An app's preview channels as {id, browsable}, in id order: the browsable ones, those someone put on the home
     * screen. With the stock launcher disabled nothing marks them, so when none is browsable all stand in. From
     * Mehdi-Ks's top shelf (github.com/Mehdi-Ks/cleanyourtv, ShelfContent.pickChannels).
     */
    static List<Long> pickChannels(List<long[]> channels) {
        List<Long> out = new ArrayList<>();
        for (long[] ch : channels) if (ch[1] != 0) out.add(ch[0]);
        if (out.isEmpty()) for (long[] ch : channels) out.add(ch[0]);
        return out;
    }

    private final Context context;
    private final Handler main;
    private final Runnable onChanged;
    private final ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "watch-next");
        t.setDaemon(true);
        return t;
    });
    private final ContentObserver observer;
    private final Runnable debounced = this::refresh;
    private boolean observing;
    private final boolean previews;
    private final Set<String> hidden;
    /** Replaced whole on the main thread; the lists in it are never changed. */
    private Map<String, List<Item>> shelf = Collections.emptyMap();
    private volatile boolean closed;

    /** onChanged runs on the main thread after every read, whether or not anything differs. */
    WatchNextShelf(Context context, Handler main, Runnable onChanged) {
        this(context, main, onChanged, false, Collections.<String>emptyList());
    }

    /** previews: also the apps' preview-channel programs (shelf_previews); hidden: apps left out (shelf_hidden_apps). */
    WatchNextShelf(Context context, Handler main, Runnable onChanged, boolean previews, Collection<String> hidden) {
        this.previews = previews;
        this.hidden = new HashSet<>(hidden);
        this.context = context.getApplicationContext();
        this.main = main;
        this.onChanged = onChanged;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                main.removeCallbacks(debounced);
                main.postDelayed(debounced, DEBOUNCE_MS);
            }
        };
    }

    boolean permitted() {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** The app's cards, newest first; empty when it has none or the feature has no permission. */
    List<Item> itemsFor(String pkg) {
        List<Item> list = pkg == null ? null : shelf.get(pkg);
        return list == null ? Collections.<Item>emptyList() : list;
    }

    /** Home is in front: read now and again whenever the table changes. */
    void start() {
        if (!observing && permitted()) {
            try {
                context.getContentResolver().registerContentObserver(
                        TvContract.WatchNextPrograms.CONTENT_URI, true, observer);
                if (previews) {
                    context.getContentResolver().registerContentObserver(
                            TvContract.PreviewPrograms.CONTENT_URI, true, observer);
                    context.getContentResolver().registerContentObserver(
                            TvContract.Channels.CONTENT_URI, true, observer);
                }
                observing = true;
            } catch (SecurityException ignored) {
                // Permission taken away since the check: the read below reports nothing.
            }
        }
        refresh();
    }

    void stop() {
        main.removeCallbacks(debounced);
        if (observing) context.getContentResolver().unregisterContentObserver(observer);
        observing = false;
    }

    void shutdown() {
        closed = true;
        stop();
        reader.shutdownNow();
    }

    void refresh() {
        if (closed) return;
        if (!permitted()) {
            publish(Collections.<String, List<Item>>emptyMap());
            return;
        }
        try {
            reader.execute(() -> {
                ContentResolver resolver = context.getContentResolver();
                Map<String, List<Item>> next = select(read(resolver), System.currentTimeMillis());
                publish(merge(next, previews ? readPreviews(resolver) : Collections.<String, List<Item>>emptyMap(),
                        hidden));
            });
        } catch (RuntimeException ignored) {
            // Shut down while a refresh was queued.
        }
    }

    private void publish(Map<String, List<Item>> next) {
        main.post(() -> {
            if (closed) return;
            shelf = next;
            onChanged.run();
        });
    }

    /** The whole table with the named columns and no selection; an unreadable table is an empty one. */
    private static List<Item> read(ContentResolver resolver) {
        List<Item> rows = new ArrayList<>();
        try (Cursor c = resolver.query(TvContract.WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null)) {
            if (c == null) return rows;
            while (c.moveToNext()) {
                rows.add(new Item(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                        c.getString(5), c.getString(6), c.getString(7), c.isNull(8) ? -1 : c.getInt(8),
                        c.isNull(9) ? -1 : c.getLong(9), c.isNull(10) ? -1 : c.getLong(10),
                        c.isNull(11) ? -1 : c.getLong(11), c.isNull(12) ? -1 : c.getInt(12), c.getString(13)));
            }
        } catch (RuntimeException e) {
            // SecurityException without the permission, SQLiteException on a TV whose table lacks a column.
            rows.clear();
        }
        return rows;
    }

    /**
     * shelf_previews: each app's preview programs, channel by channel (pickChannels), each channel in the app's own
     * order (weight, highest first). The channel list and a channel's programs are read whole, without a selection,
     * as the provider requires; an unreadable table is an empty one.
     */
    private static Map<String, List<Item>> readPreviews(ContentResolver resolver) {
        Map<String, List<long[]>> channelsByApp = new LinkedHashMap<>();
        Map<String, List<Item>> out = new LinkedHashMap<>();
        try (Cursor c = resolver.query(TvContract.Channels.CONTENT_URI, new String[]{TvContract.Channels._ID,
                TvContract.Channels.COLUMN_BROWSABLE, TvContract.Channels.COLUMN_PACKAGE_NAME,
                TvContract.Channels.COLUMN_TYPE}, null, null, null)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                String pkg = c.getString(2);
                if (isEmpty(pkg) || !TvContract.Channels.TYPE_PREVIEW.equals(c.getString(3))) continue;
                List<long[]> list = channelsByApp.get(pkg);
                if (list == null) channelsByApp.put(pkg, list = new ArrayList<>());
                list.add(new long[]{c.getLong(0), c.isNull(1) ? 0 : c.getInt(1)});
            }
        } catch (RuntimeException e) {
            return out;
        }
        for (Map.Entry<String, List<long[]>> app : channelsByApp.entrySet()) {
            List<long[]> channels = app.getValue();
            Collections.sort(channels, (a, b) -> Long.compare(a[0], b[0]));
            List<Item> items = new ArrayList<>();
            for (long channel : pickChannels(channels)) {
                if (items.size() >= MAX_WITH_PREVIEWS) break;
                List<Object[]> progs = new ArrayList<>();   // {Item, weight}
                try (Cursor c = resolver.query(TvContract.buildPreviewProgramsUriForChannel(channel),
                        PREVIEW_PROJECTION, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        if (!c.isNull(12) && c.getInt(12) == 0) continue;   // browsable 0: the app hid it
                        progs.add(new Object[]{previewItem(app.getKey(), c), c.isNull(11) ? 0 : c.getInt(11)});
                    }
                } catch (RuntimeException e) {
                    continue;
                }
                Collections.sort(progs, (a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
                for (Object[] p : progs) items.add((Item) p[0]);
            }
            if (!items.isEmpty()) out.put(app.getKey(), items);
        }
        return out;
    }

    private static Item previewItem(String pkg, Cursor c) {
        return new Item(pkg, c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                c.getString(5), c.getString(6), -1, -1, c.isNull(7) ? -1 : c.getLong(7),
                c.isNull(8) ? -1 : c.getLong(8), c.isNull(9) ? -1 : c.getInt(9), c.getString(10));
    }

    /** The deep link as an Intent, or null when it does not parse. */
    static Intent intentFor(String intentUri) {
        try {
            return Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
