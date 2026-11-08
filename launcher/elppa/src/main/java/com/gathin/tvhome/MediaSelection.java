package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class MediaSelection {
  static final long DAY = 86_400_000L;

  private MediaSelection() {}

  static List<MediaEntry> watchNext(
      List<MediaEntry> raw, Set<String> excluded, Set<String> dismissed, long now) {
    List<MediaEntry> sorted = new ArrayList<>();
    for (MediaEntry e : raw) {
      if (!usable(e, excluded, dismissed) || e.preview || e.watchType < 0 || e.watchType > 3)
        continue;
      if (e.watchType == 0 && e.progress() >= 97) continue;
      if (e.engaged > 0 && e.engaged < now - 90 * DAY) continue;
      sorted.add(e);
    }
    sorted.sort(
        Comparator.comparingLong((MediaEntry e) -> e.engaged).reversed().thenComparing(e -> e.key));
    return unique(sorted, 40);
  }

  static List<MediaEntry> recommendations(
      List<MediaEntry> raw, Set<String> excluded, Set<String> dismissed) {
    List<MediaEntry> result = new ArrayList<>();
    for (MediaEntry e : raw) {
      // TV types 0–3 are movie, series, season, episode; retain unknown as fallback.
      if (usable(e, excluded, dismissed)
          && e.preview
          && e.contentType <= 3
          && !e.heroArt().isEmpty()) result.add(e);
    }
    return unique(result, 30);
  }

  private static boolean usable(MediaEntry e, Set<String> excluded, Set<String> dismissed) {
    return !e.source.isEmpty()
        && !e.title.isEmpty()
        && !e.intentUri.isEmpty()
        && !excluded.contains(e.source)
        && !dismissed.contains(e.key);
  }

  private static List<MediaEntry> unique(List<MediaEntry> sorted, int max) {
    List<MediaEntry> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (MediaEntry e : sorted)
      if (seen.add(e.identity())) {
        result.add(e);
        if (result.size() == max) break;
      }
    return result;
  }
}
