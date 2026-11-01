# Testing TV Hub

## Automated (runs on any computer, and in CI)

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

- `M3uParserTest`: attributes, quoted commas, `#EXTGRP`, `#EXTVLCOPT`, skipped schemes, BOM and CRLF, empty and non-playlist input.
- `XmltvParserTest`: time formats and offsets, wanted-channel filtering, the time window, display-name matching, bad times, entities, an empty guide.
- `ChannelMergerTest`: merging by `tvg-id` only, merging off, stable keys for duplicates, guide ids by name, ordering by channel number.
- `ProvidersTest`: Dispatcharr addresses, HDHomeRun lineup (DRM skipped, transcode), the discovery packet's CRC, log redaction.

To check the card made it into the APK: `aapt2 dump xmltree app/build/outputs/apk/debug/app-debug.apk --file res/xml/module_card.xml`
should print `label="TV Hub"`, `caption="Live channels"`, `spec=1` and `accent=#ff4fa3e0`.
None of this proves anything on a TV; the rest of this page does.

## Demo sources

```bash
python3 tools/make_fixtures.py <this computer's LAN IP>
python3 -m http.server --directory fixtures/out 8000
```

| Address | Exercises |
| :- | :- |
| `http://IP:8000/demo.m3u` | Six entries: one working HLS stream (Big Buck Bunny, CC BY), a 404 stream, a stream that falls back, a channel with no guide, an unreachable host, and a `udp://` entry that is skipped. Its header names `demo.xml` |
| `http://IP:8000/backup.m3u` | Shares `tvg-id` with channel 3: with merging on, 3 fails on demo and plays from here |
| `http://IP:8000/demo.xml` | Listings for three channels from an hour ago to two days ahead (regenerate it each day) |
| `http://IP:8000/empty.m3u` | Empty playlist: the source shows an error, nothing else breaks |
| `http://IP:8000/invalid.xml` | Broken guide: "The guide is not valid XMLTV", channels stay |

For a stream that does not leave your network, any HLS or MPEG-TS file served over http works, for example
`ffmpeg -re -f lavfi -i testsrc2=size=1280x720:rate=25 -f lavfi -i sine -c:v libx264 -c:a aac -f hls -hls_time 4 -hls_list_size 6 -hls_flags delete_segments fixtures/out/local.m3u8`.

## On a TV

Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Write down the TV model and Android version for each run.

### 1. Install and first launch
- [ ] TV Hub appears in the app row (Android TV / Google TV) or Apps > Your Apps (Fire TV) with its banner.
- [ ] First launch shows the welcome page; **Skip for now** opens Home with "No sources yet".
- [ ] Settings > Run setup again brings the welcome page back.

### 2. Sources
- [ ] Add `demo.m3u`; **Check** reports "Found 5 channels, 1 skipped and a guide address".
- [ ] After saving, Sources shows 5 channels, "1 entries skipped", and a time.
- [ ] A wrong address (`http://IP:8000/nope.m3u`) shows "Nothing at that address (HTTP 404)" on Check and, if saved, in red on Sources.
- [ ] `empty.m3u` shows an error; `invalid.xml` as an XMLTV source shows "not valid XMLTV"; the demo channels stay.
- [ ] Stop the http.server and press Refresh: sources show errors, channels from the last refresh stay.
- [ ] Turn a source off: its channels leave Live TV and the Guide. Turn it on: they come back.

### 3. Guide and lists
- [ ] Guide: rows for channels 1 to 5; 1 to 3 have listings, 4 says "No guide information", the now line is at the current time.
- [ ] Up/Down move between channels, Left/Right between programmes; Left from the first programme returns to the menu.
- [ ] OK on a programme on air plays it; OK on a later one opens details with Watch and Favorite.
- [ ] Hold OK: star appears; the Favorites page and the Favorites chip list it.
- [ ] Live TV: categories (All, Favorites, Demo, Sport, Films), search "spo" finds Demo Sport, number "3" finds it too.
- [ ] Home: Recently watched fills after playing; Favorites row; Apps row if apps are picked.

### 4. Player
- [ ] Channel 1 plays within a few seconds with a spinner while loading; the banner shows now/next.
- [ ] Up/Down and Channel +/- change channel; Left returns to the previous one; typing 4 tunes channel 4.
- [ ] Channel 2 (404) shows "The stream was not found…" or "refused it"; OK retries; Up moves on.
- [ ] Channel 5 (unreachable) retries, then shows "Can't reach the stream's server".
- [ ] With `backup.m3u` added and merging on, channel 3 shows "Trying …backup" and plays.
- [ ] Menu (or hold OK) lists audio tracks and "Subtitles: off".
- [ ] Back returns to the page you started from. Home button during playback, then `adb shell dumpsys media.audio_flinger | grep -i tvhub` or `top`: no TV Hub playback continues.

### 5. Providers that need hardware
- [ ] HDHomeRun: **Scan the network** finds the tuner; channels play (tick H.264 on an EXTEND if there is sound but no picture).
- [ ] Dispatcharr: server address and an output profile; with the TV's network not allowed, the 403 message appears.
- [ ] Streaming apps: installed known apps are pre-ticked; "Show every TV app" lists the rest; the Home shortcut opens the app; uninstall the app and it disappears from Home.

### 6. CleanYourTV module card
On a TV running CleanYourTV (com.gathin.tvhome) or a fork that keeps its card category:

```bash
adb shell pm dump io.github.maxhayim.tvhub | grep -B2 -A6 DOCK_CARD
adb shell input keyevent KEYCODE_HOME
adb logcat -d -s TvHomeCards
```

- [ ] The prompt names TV Hub, its package and the key's first twelve hex digits. **Keep hidden** leaves the ordinary tile.
- [ ] Hold OK on Settings > Module cards: set TV Hub to Shown; the card replaces the tile, shows "TV Hub · Live channels" focused, and glows blue in the dock.
- [ ] OK on the card opens TV Hub; holding OK opens App info.
- [ ] `adb logcat -d -s TvHomeCards` has no rejection line for io.github.maxhayim.tvhub.
- [ ] Uninstall: the card is gone; reinstall the same build: no new prompt.

### 7. Platforms
| Check | Android TV | Google TV | Fire TV |
| :- | :- | :- | :- |
| Installs from adb | | | |
| Banner in the app list | | | |
| D-pad, Back, Menu work | | | Menu is the ≡ button |
| Text entry opens the TV keyboard | | | |
| HLS demo plays | | | |
| 720p and 4K output: text readable, nothing cut off | | | |

### 8. Constrained hardware
On a 1 GB TV: add a playlist with a few thousand channels and a full XMLTV guide; time the refresh;
scroll the guide with Down held: it should keep up without the TV stalling. `adb shell dumpsys meminfo io.github.maxhayim.tvhub`
after scrolling should stay well under 150 MB total PSS. Settings > Animations off for slow TVs.

## Results so far

### 2026-10-22, MOKA R8G (Realtek, Android 14, 1080p output, 2 GB RAM), debug build (Media3 1.5.1, SDK 35; the add-on builds with 1.4.1 and SDK 34), launcher: a custom home without module-card support

Passed on the TV:
- Install over adb; first launch shows the welcome page; M3U form with the TV keyboard; Check reports "Found 5 channels, 1 skipped and a guide address".
- Header guide (`x-tvg-url`) read; Guide grid with logos, now line, listings, "No guide information" rows; Left/Right/Up/Down keep the cursor's time; programme details for a later programme.
- Playback of HLS (H.264 + AAC, Media3 READY in about 3 s), channel up/down, number entry, previous channel, audio/subtitle menu, Back releases the player (`ExoPlayerImpl: Release`).
- Errors: 404 stream message; unreachable host gives up after about 20 s (was 62 s before the load-retry limit).
- Merge and fallback: channel 3's dead stream fails and plays from the second source about 3 s later.
- Favourites by holding OK in the guide; Favorites page and Home rows; search by name and number.
- Streaming apps: six installed apps detected through `<queries>` (Pluto TV, Tubi, Plex, YouTube TV, Sling TV, Philo), shown with their own banners as "Opens the app".
- Database upgrade from schema 1 to 2 on an installed copy.
- Memory: about 54 MB PSS while browsing.

Fixed during this run: guide wiped when a source failed a refresh; focus switching the page after returning from the editor; card text drawn behind logos; zoomed focus clipped; error text for 404s; playlist status plurals.

Rechecked the same day with the add-on build itself (Media3 1.4.1, SDK 34): first launch, adding the demo playlist, HLS playback (READY in about 3 s), the HTTP 404 message, and the player released on Back.

Not tested yet: the module card (this TV's launcher does not read cards), HDHomeRun and Dispatcharr (no hardware or server on hand), Fire TV, Google TV, 720p and 4K output modes, a large real playlist and guide.
