# Reading Continue watching

A launcher can show the rows other apps publish to the system's Continue watching (Watch Next)
and preview channels. They live in the TV provider, behind `TvContract.WatchNextPrograms` and
`TvContract.PreviewPrograms`.

- **Permission:** `android.permission.READ_TV_LISTINGS`, a runtime permission. Request it with
  the normal dialog, or grant it from a shell: `pm grant <package> android.permission.READ_TV_LISTINGS`.
- **Not needed:** `com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA` is
  `signature|privileged`, so a sideloaded app cannot hold it. With only `READ_TV_LISTINGS` a query returns
  the caller's own rows plus every row marked `searchable`.
- **Query without a selection:** a caller without `ACCESS_ALL_EPG_DATA` gets a `SecurityException`
  when it passes one. Read the whole table and filter in code.
- **Some apps publish nothing.** Rows exist only if the app writes them and marks them
  searchable, and some apps do neither, so query on the target TV before relying on an app.
  Query from an app that holds `READ_TV_LISTINGS`, not from the shell: the shell lacks it, so
  `content query --uri content://android.media.tv/watch_next_program` (or `channel`,
  `preview_program`) prints `No result found.` and cannot tell whether any app publishes rows.
- **Seen on a Chromecast with Google TV 4K** (Google TV 14, `UTTC.250917.004`): a test build
  holding `READ_TV_LISTINGS` and querying without a selection saw 6 channels and 51 preview
  programs, all from YouTube and Spotify, and no Watch Next rows. Poster URIs were `https://` (one
  Spotify poster aside, `android.resource://`), so a reader needs network access to show most of
  them; `intent_uri` held `intent:` URIs. Plex (`com.plexapp.android` 2026.20.0, which requests
  `WRITE_EPG_DATA`) had no channel, preview or Watch Next rows visible. Without
  `ACCESS_ALL_EPG_DATA` a reader sees only its own rows plus rows marked `searchable`, so Plex rows
  that are not searchable would be invisible too: the probe cannot tell "publishes nothing" from
  "publishes only non-searchable rows".
- **Seen on a TCL 75Q7C** (G08, Google TV 12, `STT2.221228.001 AS36`), the same kind of probe: 43 Watch Next
  rows, from Prime Video (14), HBO Max (`com.wbd.stream`, 10), Jellyfin (7), Wholphin (6), Moonfin (4) and
  SkyShowtime (2), plus 339 preview programs (the 60 the probe printed were all SkyShowtime's). Netflix,
  installed, had no rows visible. Of the Watch Next posters, 32 were `https://` (Prime Video, HBO Max,
  SkyShowtime) and 11 were `content://` from the Jellyfin clients' own image providers, which a reader opens
  without network access. `watch_next_type` 0, 1 and 2 (continue, next, new) all occurred; Jellyfin's
  next-episode rows had no `last_playback_position_millis`. Prime Video's `intent_uri` opened the app at its
  profile picker on a cold start.
- **Seen on a TCL C6K** (Google TV 14, `UTT2.250416.001 AU02`): the same kind of test build saw 25 Watch Next
  rows: Crunchyroll 19, Prime Video 3, Netflix 2 and Jellyfin for Android TV 1. All four fill `title`,
  `season_display_number`, `episode_display_number`, `poster_art_uri`, `intent_uri`, `watch_next_type`,
  `last_engagement_time_utc_millis`, `last_playback_position_millis` and `duration_millis`; only Crunchyroll and
  Jellyfin add `episode_title`. `watch_next_type` 0 (continue), 1 (next) and 2 (new, from Prime Video) all
  appeared. Posters were `https://` except Jellyfin's `content://`. Rows linger: the oldest was six months old,
  and a "continue" row sat at 99% of its episode, so a reader filters by engagement time and progress.
  YouTube published no Watch Next rows, only preview channels ("Recommended for you", "Trending", "YouTube
  Music"). Stremio (`com.stremio.one`) published none either; it keeps a preview channel named "Continue
  Watching" whose rows carry `last_playback_position_millis` and `duration_millis` but no date column at all, so
  they cannot be sorted or aged by time.

Source: `createSqlParams()` in `packages/providers/TvProvider/src/com/android/providers/tv/TvProvider.java`
(AOSP) holds the `READ_TV_LISTINGS` and `searchable` logic; both permissions are defined in that
module's `AndroidManifest.xml`.
