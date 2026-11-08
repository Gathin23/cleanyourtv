package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** App-published data only. Explicit projections, no SQL selection or privileged EPG permission. */
final class TvContentRepository {
  static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
  static final Uri WATCH = Uri.parse("content://android.media.tv/watch_next_program");
  static final Uri PREVIEW = Uri.parse("content://android.media.tv/preview_program");
  static final Uri CHANNEL = Uri.parse("content://android.media.tv/channel");
  private static final String[] BASE = {
    "_id", "package_name", "title", "poster_art_uri", "intent_uri"
  };
  private static final String[] WATCH_BASE = {
    "_id", "package_name", "title", "poster_art_uri", "intent_uri", "watch_next_type"
  };
  private static final String[] DETAILS = {
    "_id",
    "package_name",
    "title",
    "poster_art_uri",
    "intent_uri",
    "thumbnail_uri",
    "type",
    "season_display_number",
    "episode_display_number",
    "last_playback_position_millis",
    "duration_millis"
  };
  private static final String[] WATCH_DETAILS = {
    "_id",
    "package_name",
    "title",
    "poster_art_uri",
    "intent_uri",
    "thumbnail_uri",
    "type",
    "season_display_number",
    "episode_display_number",
    "last_playback_position_millis",
    "duration_millis",
    "watch_next_type",
    "last_engagement_time_utc_millis"
  };

  static final class Snapshot {
    final List<MediaEntry> watch = new ArrayList<>(), previews = new ArrayList<>();
    final Map<String, int[]> counts = new LinkedHashMap<>();
    final Map<String, Boolean> availableSources = new LinkedHashMap<>();
    final List<String> notes = new ArrayList<>();
    boolean permission, watchRead, previewRead;
    int channels;
    long checkedAt;
  }

  private final Context context;

  TvContentRepository(Context c) {
    context = c.getApplicationContext();
  }

  boolean permitted() {
    return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
  }

  Snapshot read(CancellationSignal signal) {
    Snapshot r = new Snapshot();
    r.permission = permitted();
    r.checkedAt = System.currentTimeMillis();
    if (!r.permission) return r;
    r.watchRead = readTable(WATCH, false, r, signal);
    r.previewRead = readTable(PREVIEW, true, r, signal);
    try (Cursor c =
        context
            .getContentResolver()
            .query(CHANNEL, new String[] {"_id"}, null, null, null, signal)) {
      if (c != null) r.channels = c.getCount();
    } catch (RuntimeException ignored) {
      r.notes.add("Channel count unavailable.");
    }
    return r;
  }

  private boolean readTable(Uri uri, boolean preview, Snapshot r, CancellationSignal signal) {
    Cursor cursor;
    try {
      cursor =
          context
              .getContentResolver()
              .query(uri, preview ? DETAILS : WATCH_DETAILS, null, null, null, signal);
    } catch (RuntimeException first) {
      signal.throwIfCanceled();
      try {
        cursor =
            context
                .getContentResolver()
                .query(uri, preview ? BASE : WATCH_BASE, null, null, null, signal);
        r.notes.add(
            (preview ? "Recommendations" : "Watch Next") + ": limited metadata from this TV.");
      } catch (RuntimeException second) {
        signal.throwIfCanceled();
        r.notes.add(
            (preview ? "Recommendations" : "Watch Next")
                + ": provider unavailable ("
                + second.getClass().getSimpleName()
                + ").");
        return false;
      }
    }
    if (cursor == null) {
      r.notes.add("TV provider returned no cursor.");
      return false;
    }
    try (Cursor c = cursor) {
      int inspected = 0;
      while (c.moveToNext() && inspected++ < 2000) {
        signal.throwIfCanceled();
        String source = string(c, "package_name");
        if (source.isEmpty()) continue;
        int[] counts = r.counts.computeIfAbsent(source, ignored -> new int[2]);
        counts[preview ? 1 : 0]++;
        if (!r.availableSources.computeIfAbsent(source, this::launchable)) continue;
        String season = string(c, "season_display_number"),
            episode = string(c, "episode_display_number");
        String subtitle =
            episode.isEmpty() ? "" : (season.isEmpty() ? "" : "S" + season + " · ") + "E" + episode;
        MediaEntry e =
            new MediaEntry(
                (preview ? "p:" : "w:") + number(c, "_id", 0),
                source,
                string(c, "title"),
                subtitle,
                string(c, "poster_art_uri"),
                string(c, "thumbnail_uri"),
                string(c, "intent_uri"),
                (int) number(c, "watch_next_type", -1),
                (int) number(c, "type", -1),
                number(c, "last_engagement_time_utc_millis", 0),
                number(c, "last_playback_position_millis", 0),
                number(c, "duration_millis", 0),
                preview);
        (preview ? r.previews : r.watch).add(e);
      }
      if (inspected > 2000) r.notes.add("Scan capped at 2,000 rows per table.");
      return true;
    } catch (RuntimeException failure) {
      signal.throwIfCanceled();
      r.notes.add("Some provider rows could not be decoded.");
      return false;
    }
  }

  private boolean launchable(String source) {
    PackageManager pm = context.getPackageManager();
    try {
      if (!pm.getApplicationInfo(source, 0).enabled) return false;
      return pm.getLeanbackLaunchIntentForPackage(source) != null
          || pm.getLaunchIntentForPackage(source) != null;
    } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
      return false;
    }
  }

  private static String string(Cursor c, String name) {
    int i = c.getColumnIndex(name);
    return i < 0 || c.isNull(i) ? "" : MediaEntry.text(c.getString(i));
  }

  private static long number(Cursor c, String name, long fallback) {
    int i = c.getColumnIndex(name);
    if (i < 0 || c.isNull(i)) return fallback;
    try {
      return c.getLong(i);
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }
}
