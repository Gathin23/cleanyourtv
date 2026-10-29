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

Source: `createSqlParams()` in `packages/providers/TvProvider/src/com/android/providers/tv/TvProvider.java`
(AOSP) holds the `READ_TV_LISTINGS` and `searchable` logic; both permissions are defined in that
module's `AndroidManifest.xml`.
