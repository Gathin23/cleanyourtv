package com.gathin.tvhome;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class MediaSelectionTest {
  private static final long NOW = 200 * MediaSelection.DAY;

  private MediaEntry item(
      String id, String app, int type, long engaged, long pos, long duration, boolean preview) {
    return new MediaEntry(
        id,
        app,
        "A title",
        "Episode 2",
        "https://image",
        "https://wide",
        "app://title",
        type,
        3,
        engaged,
        pos,
        duration,
        preview);
  }

  @Test
  public void unfinishedAndNextDoNotRequireTheSameProgress() {
    MediaEntry ongoing = item("1", "a", 0, NOW, 30, 100, false),
        next = item("2", "b", 1, NOW, 0, 0, false),
        finished = item("3", "c", 0, NOW, 98, 100, false);
    List<MediaEntry> result =
        MediaSelection.watchNext(
            Arrays.asList(ongoing, next, finished),
            Collections.emptySet(),
            Collections.emptySet(),
            NOW);
    assertEquals(2, result.size());
    assertTrue(result.contains(next));
    assertFalse(result.contains(finished));
  }

  @Test
  public void staleRowsExcludedButUndatedEpisodesSurvive() {
    MediaEntry stale = item("1", "a", 1, NOW - 91 * MediaSelection.DAY, 0, 0, false),
        undated = item("2", "b", 1, 0, 0, 0, false);
    assertEquals(
        Collections.singletonList(undated),
        MediaSelection.watchNext(
            Arrays.asList(stale, undated), Collections.emptySet(), Collections.emptySet(), NOW));
  }

  @Test
  public void duplicateLinksKeepMostRecentAndRespectSourceChoices() {
    MediaEntry old = item("1", "a", 0, NOW - 1, 10, 100, false),
        recent = item("2", "a", 0, NOW, 20, 100, false),
        other = item("3", "b", 1, NOW, 0, 0, false);
    List<MediaEntry> result =
        MediaSelection.watchNext(
            Arrays.asList(old, recent, other),
            Collections.singleton("b"),
            Collections.emptySet(),
            NOW);
    assertEquals(Collections.singletonList(recent), result);
    assertEquals(
        1,
        MediaSelection.watchNext(
                Arrays.asList(old, recent), Collections.emptySet(), Collections.singleton("2"), NOW)
            .size());
  }

  @Test
  public void missingAndInvalidRowsNeverPretendToBeContinueWatching() {
    MediaEntry unknown = item("1", "a", -1, NOW, 0, 0, false);
    MediaEntry missing = new MediaEntry("2", "b", "", "", "", "", "", 0, 0, 0, 0, 0, false);
    assertTrue(
        MediaSelection.watchNext(
                Arrays.asList(unknown, missing),
                Collections.emptySet(),
                Collections.emptySet(),
                NOW)
            .isEmpty());
  }

  @Test
  public void recommendationsHaveArtworkAndAreNotWatchNext() {
    MediaEntry watch = item("1", "a", 1, NOW, 0, 0, false),
        preview = item("2", "b", -1, 0, 0, 0, true);
    assertEquals(
        Collections.singletonList(preview),
        MediaSelection.recommendations(
            Arrays.asList(watch, preview), Collections.emptySet(), Collections.emptySet()));
  }

  @Test
  public void progressIsBoundedWithoutIntegerOverflow() {
    MediaEntry huge = item("1", "a", 0, NOW, Long.MAX_VALUE, Long.MAX_VALUE, false);
    assertEquals(100, huge.progress());
    assertEquals(0, item("2", "a", 1, NOW, 50, 0, false).progress());
  }

  @Test
  public void labelsCannotInjectBidiControlsOrNewlines() {
    assertEquals("Movie   title", MediaEntry.text("Movie\n\u202E title"));
  }
}
