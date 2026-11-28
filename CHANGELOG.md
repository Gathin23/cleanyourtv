# Changelog

All notable changes to this fork (branch `streamer`). Upstream's own changes are in its git history; this file
lists only what the fork adds on top. Dates are YYYY-MM-DD.

## [0.1.0] - 2026-11-28

Based on upstream `main` at `be608705` ("Round BI"). Tested on a Google TV Streamer (`kirkwood`, Google TV 14,
`UTTK.260317.003`).

### Added
- **Continue watching in the top shelf** (`continue_watching`, on): the focused dock app's Watch Next rows as cards
  with a progress bar; Up opens a browse view with the title's artwork full-screen; OK opens the row in its app.
  ProfetGit's two `tora-shelf` commits, moved onto current upstream.
- **Preview channels on the shelf** (`shelf_previews`, off by default): after an app's Continue watching, its
  preview-channel programs (picks, subscriptions, trending), without a progress bar. When no channel is browsable
  (Google TV's home disabled), all of the app's channels are read. Channel reading from Mehdi-Ks's `my-tv`.
- **`shelf_hidden_apps`**: apps whose rows the shelf leaves out.
- **Weather line** (`weather_line`, `weather_latitude`, `weather_longitude`, off by default): one Open-Meteo line
  under the clock. `WeatherLine.java` and its test from Mehdi-Ks's `my-tv`; German strings added.
- **`dock_order`**: `"fixed"` (default, as upstream) or `"recent"`, the dock filled with the apps Home opened last,
  newest first, `dock_apps` filling the rest. Home rebuilds on return when the order would change.
- Tests: `RecentDockTest`, `ShelfPreviewsTest`, `WeatherLineTest`.

### Changed
- `INTERNET` and `READ_TV_LISTINGS` permissions, for the shelf's artwork, the weather line and the TV provider.
- A shelf card whose link points into the launcher itself opens the app's front door instead.

### Known limits
- The release APK (about 609 KB) is over upstream's 576 KB gate.
- Only launches from Home count for `dock_order: "recent"`, not an app a remote button starts.
- Which apps fill the shelf depends on what they publish (YouTube publishes no Watch Next rows).
