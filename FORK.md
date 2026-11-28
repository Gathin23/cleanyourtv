# Streamer Home

A fork of [cleanyourtv](https://github.com/Gathin23/cleanyourtv)'s launcher for a **Google TV Streamer**
(`kirkwood`, Google TV 14, `UTTK.260317.003`), on branch `streamer`. It keeps upstream's launcher and adds what
Google TV's own home did well: the apps' Continue watching and picks above the dock, a weather line, and a dock
that can sort itself. Everything new is off unless a theme or `config.xml` turns it on, except
`continue_watching`, which this fork turns on.

What changed, release by release: [CHANGELOG.md](CHANGELOG.md).

## What it adds

| Key | What it does | From |
|---|---|---|
| `continue_watching` | While a dock app is focused, its Continue watching rows (Watch Next) show as cards under its name, with a progress bar. Up onto them turns Home into a browse view with the title's artwork full-screen behind it; OK opens the row in its app. On in this fork | [ProfetGit](https://github.com/ProfetGit/cleanyourtv/blob/tora-shelf/FORK.md) |
| `shelf_previews` | The cards also take the app's preview-channel programs (its picks, subscriptions or trending rows) after its Continue watching, without a progress bar. With Google TV's home disabled no channel is marked browsable, so all of an app's channels are read | Shelf reading from [Mehdi-Ks](https://github.com/Mehdi-Ks/cleanyourtv/blob/my-tv/FORK.md) |
| `shelf_hidden_apps` | Packages whose rows the shelf leaves out, for apps that publish only promotions | Mehdi-Ks |
| `weather_line`, `weather_latitude`, `weather_longitude` | One line under the clock: now, the next hours and the night's low or the day's high, from Open-Meteo. English, French and German | Mehdi-Ks |
| `dock_order` | `"fixed"` (default) keeps `dock_apps` as written; `"recent"` fills the dock with the apps Home opened last, newest first, with `dock_apps` filling the rest | This fork |
| `weather_style` | `"line"` (default) keeps the sentence under the clock; `"compact"` shows only the temperature and condition beside the top buttons | This fork |
| `peek_row` | `false` hides the grid row under the dock while the dock, the shelf or the top bar has focus, so only the dock and its cards show; Down still scrolls to the grid | This fork |
| `scroll_hint` | A small chevron under the dock while it has focus and more apps follow below | This fork |

Example theme `config.json` (Berlin as the weather place):

```json
{
  "continue_watching": true,
  "shelf_previews": true,
  "shelf_hidden_apps": [],
  "weather_line": true,
  "weather_latitude": "52.52",
  "weather_longitude": "13.40",
  "dock_order": "fixed",
  "weather_style": "compact",
  "peek_row": false,
  "scroll_hint": true,
  "cinematic_dots": false
}
```

## Permissions and network

- `READ_TV_LISTINGS`, to read the rows other apps publish (only rows they mark searchable). Grant it once:
  `adb shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS`. Without it the shelf stays plain.
- `INTERNET`, for two things only: the artwork the apps link in their rows (from each app's own image host), and
  Open-Meteo (`api.open-meteo.com`, latitude and longitude only, every 30 minutes while Home is on screen) when
  `weather_line` is on. No analytics, no other hosts, nothing written to disk.
- A program link that points into the launcher itself is not followed.
- The release APK is about 611 KB, over upstream's 576 KB limit.

## What the apps publish, on this Streamer

Prime Video fills Continue watching; SmartTube fills the shelf from its preview channels (subscriptions and
recommendations). YouTube publishes no Watch Next rows. Other apps vary: see upstream's `docs/watch-next.md`.

## Build and install

As upstream (JDK 17 or newer, Android SDK 34), from the repo root:

```bash
TV_HOST=<tv ip> INPUT_KEY_SERVICE=1 launcher/build-and-install.sh
TV_HOST=<tv ip> tools/tv.py 'pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS'
TV_HOST=<tv ip> tools/theme.sh themes/glass   # or your own theme with the keys above
```

The application ID is upstream's, so it installs over a build signed with the same debug key and keeps the dock
and settings. Debloating the TV is upstream's job: follow its `PROMPT.md`.

## Credit

The Continue watching cards and browse view are ProfetGit's (`tora-shelf`, two commits, kept with their
authorship). The preview-channel reading and the weather line come from Mehdi-Ks's `my-tv` branch, which ports
bjrb20's top shelf ([upstream PR #363](https://github.com/Gathin23/cleanyourtv/pull/363)); `WeatherLine.java` and
its test are taken unchanged. Released under upstream's MIT licence.
