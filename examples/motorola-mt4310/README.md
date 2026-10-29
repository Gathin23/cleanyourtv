# Motorola MT4310 (43", Newsan / Hisense hayward, Android TV 11)

What was done on this TV on 2026-10-21, and how to change or undo it. Commands run from the repo root
on Windows; replace `<tv ip>` with your TV's address.

## What changed

- **Disabled, not uninstalled:** every package in [debloat.txt](debloat.txt) (store demo, Hisense silent
  installs, telemetry, partner promo rows, calendar and printing, Play Movies, Play Games, TalkBack/TTS,
  Arkiv) plus the stock launcher `com.google.android.tvlauncher`.
- **Not in the list:** voice search `com.google.android.katniss` and `com.google.android.tvrecommendations`.
  The TV's factory manager turns them back on at every boot, so disabling them doesn't stick (see the notes
  below).
- **New Home:** the launcher in `launcher/` (`com.gathin.tvhome`), with this TV's dock in
  [theme/config.json](theme/config.json): Netflix, YouTube, Disney+, Magis TV.
- **Left alone:** Netflix, Prime Video, YouTube, YouTube Music, Chromecast built-in, BrowseHere, the
  ambient screensaver, your own apps, and everything the TV needs (Live TV, HDMI, remote keys, Bluetooth,
  keyboard, Play Store, Play services).

## Connecting again

Turn on USB debugging (Settings > Device Preferences > About > press Build 7 times, then Developer
options). This TV opens network ADB on port 5555 by itself. Then:

```powershell
$env:TV_HOST = '<tv ip>'
tools\venv\Scripts\python.exe -I tools\tv.py --probe
```

## Re-enable one app

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py 'pm enable --user 0 com.google.android.videos'
```

Swap in the package name from `debloat.txt`. To keep an app on for good, put `#` in front of its line
in `debloat.txt`.

`katniss` is voice search; the mic button also needs the remote paired over Bluetooth (the pairing screen
the mic button opens).

## Undo everything

```sh
TV_HOST=<tv ip> tools/restore.sh examples/motorola-mt4310/debloat.txt com.google.android.tvlauncher
```

(`tools\restore.ps1` on Windows.) It re-enables everything the list disabled and gives Home back to the
stock launcher.

## Change the dock order

Edit `dock_apps` in [theme/config.json](theme/config.json) (package names, left to right; four fit),
then push it, no rebuild needed:

```powershell
tools\theme.ps1 -TvHost <tv ip> examples\motorola-mt4310\theme
```

Package names: `tools\venv\Scripts\python.exe -I tools\tv.py 'pm list packages -3'`. Netflix
`com.netflix.ninja`, YouTube `com.google.android.youtube.tv`, Disney+ `com.disney.disneyplus`, Magis
TV `com.android.mgstv`, Prime Video `com.amazon.amazonvideo.livingroom`, Spotify
`com.spotify.tv.android`. Reordering also works on the TV: long-press the Settings pill.

## Change the wallpaper

Put a `wallpaper.jpg` (1280x720 is enough for this TV) next to `theme/config.json`, set
`"wallpaper": true` there, and push the theme with the command above. `"wallpaper_dim": 40` darkens
it so the tiles stay readable. Back to plain black: `"wallpaper": false` and push again. All theme
keys: [themes/README.md](../../themes/README.md).

## Rebuild / reinstall the launcher

```sh
TV_HOST=<tv ip> launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/motorola-mt4310/theme
```

The first builds the APK (JDK 17 and the Android SDK), installs it as Home and disables the stock
launcher; the second pushes the dock theme. On Windows: `launcher\build-and-install.ps1` and
`tools\theme.ps1`.

## Notes from this TV

- At every boot `com.at.factory.factorymanager` re-enables `katniss` and `tvrecommendations` (and TalkBack
  comes back too, but never runs). Disabling them, clearing the voice and notification-listener settings
  and background restrictions were all undone at boot, so the list leaves both on. Together they take
  about 63 MB of RAM (about 315 MB available after a reboot with them running, about 349 MB without, of
  934 MB). Never open or disable the factory manager itself.
- The screen goes to sleep after 5 minutes without a key press (the factory setting) and Wi-Fi drops
  with it. If the TV is unreachable a few minutes after a reboot, it is asleep: press Power.

- The remote's Input, Netflix, YouTube, Prime Video and Disney+ buttons are handled by the TV's own
  MediaTek/CVTE key services, not by the stock launcher, so they keep working with the new Home.
- The Inputs menu lists HDMI 1, HDMI 2 and Live TV. The TV registers no composite (AV) input.
- The mic button opens Bluetooth pairing: the remote talks IR until it is paired, and voice search is off.
- Disabling `partnercustomizer` removes the Inputs and Wi-Fi icons from the *stock* launcher's top bar,
  which only matters if you go back to it.
- `com.google.android.tungsten.setupwraith` is left on: it doesn't claim Home on this firmware, and it
  owned a Wi-Fi network saved during setup, which Android would delete if it were disabled.
