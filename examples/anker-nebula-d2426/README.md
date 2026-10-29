# Anker Nebula D2426 (Android TV 14): debloated, with an Apple TV style home

Model D2426 (firmware name `D2426_CapsuleIIIPro`, device `baekdu`, SDMC board, MediaTek m7632, about 1.7 GB RAM),
Android TV 14 / SDK 34, build `UKNV.260603.001`. Done over network ADB on 2026-10-22 and checked with
screenshots after every step, one reboot and the real remote.

All commands below run from the repository root. Replace the IP with the projector's address
(Settings > Network on the projector).

```bash
export TV_HOST=192.168.68.51
```

## What was changed

| | |
|---|---|
| **Disabled** (17 packages, never uninstalled) | Nebula log uploader, Google feedback, Privacy Sandbox ad services, Anoki "Free TV" channels, partner auto-installer and attribution, store demo, Ambient screensaver, Google Assistant, Play Games, TalkBack, the stock home screen and its recommendation/partner rows. The full list with reasons is in [`debloat.txt`](debloat.txt) |
| **Back after every boot** | The firmware turns `com.cltv.fast` (Free TV) and `com.google.android.katniss` (Assistant/voice) back on each boot. Nothing shows them without the stock home, so they are left that way |
| **Kept** | YouTube, Prime Video, YouTube Music, Nebula Play, Chromecast built-in, AirPin (AirPlay), all apps you installed, and everything the projector needs (HDMI, remote, Bluetooth pairing, picture, keyboard, Play Store, Play Services). The setup wizard stays: it owns the saved Wi-Fi |
| **Screensaver** | Switched from Ambient to the built-in "Colors" screensaver before Ambient was disabled |
| **Home** | `com.gathin.tvhome`, configured by [`config.json`](config.json): dock YouTube, Netflix, Disney+, Prime Video; greeting "Sedir"; the Wi-Fi pill opens NordVPN |

A backup of the state before any change (package lists, getprop, all settings, Home, display size) is in
`backup/nebula-laser-3-20261022-0140/` (not committed: it holds the serial number and device names).

## Undo everything

```bash
examples/anker-nebula-d2426/restore.sh                     # re-enable all 17, stock Home back, screensaver back
examples/anker-nebula-d2426/restore.sh --remove-launcher   # same, and uninstall the new launcher
```

## Re-enable one app

On the projector: Settings > Apps > See all apps > show system apps > pick the app > Enable.

Or over ADB (ADB needs to be on again, see below):

```bash
tools/venv/bin/python tools/tv.py 'pm enable com.google.android.play.games'
```

To keep it enabled the next time the list is applied, put a `#` in front of its line in `debloat.txt`.

## Change the dock order or the dock apps

On the projector: hold OK on the Settings pill (top right) and choose the reorder option, or open
Launcher settings from the same menu.

Or edit `dock_apps` in [`config.json`](config.json), in the order you want from left to right, and push it:

```bash
tools/theme.sh examples/anker-nebula-d2426
```

Package names: YouTube `com.google.android.youtube.tv`, Netflix `com.netflix.mediaclient`,
Disney+ `com.disney.disneyplus`, Prime Video `com.amazon.amazonvideo.livingroom`, Globoplay `com.globo.globotv`,
Crunchyroll `com.crunchyroll.crunchyroid`, Apple TV `com.apple.atve.androidtv.appletv`, Plex `com.plexapp.android`,
Kodi `org.xbmc.kodi`, VLC `org.videolan.vlc`. The full list: `tools/venv/bin/python tools/tv.py 'pm list packages'`.

Netflix on this projector is the phone app, which has no TV entry, so the launcher only shows it after it was
added from **App library** (the pill under the last row). If it disappears from the dock (after a reset of the
launcher's data, for example), add it there again.

Pushing `config.json` also clears layout choices made on the projector in Launcher settings.

## Change the wallpaper

Put a 1920×1080 JPEG named `wallpaper.jpg` next to `config.json` in this folder and push the folder:

```bash
tools/theme.sh examples/anker-nebula-d2426
```

Delete `wallpaper.jpg` and push again for the plain black background. `themes/` has ready-made looks
(`tools/theme.sh themes/aurora`), and `tools/theme.sh --reset` goes back to the launcher's built-in defaults
(which drops this dock and greeting).

## Rebuild and reinstall the launcher

```bash
examples/anker-nebula-d2426/build-and-install.sh
```

It builds the APK, installs it, applies `debloat.txt` (which disables the stock home only once the new one can
take over), makes the new launcher Home and pushes `config.json`.

## Notes from this projector

- The Inputs pill lists HDMI 1 (the only input); it opens in Google's passthrough player. Screenshots of HDMI
  come back empty, which is the projector, not a fault.
- The Wi-Fi pill shows a red "VPN desligada" badge while NordVPN is off. The Portuguese badge is long, so the
  network name is left out until the VPN is on (the green "VPN" badge is shorter). Hold the pill for Wi-Fi settings.
- AdGuard Private DNS (the optional extra in the main README) works on this build; YouTube still plays with it.
- All remote buttons (Home, Back, Settings, app buttons, mic, focus) were tested on the real remote after the
  stock home was disabled and still work.

## ADB

ADB was left on at the end of the setup, by choice. Port 5555 is plaintext, so close it when you no longer need it:
Settings > Device Preferences > Developer options > revoke USB debugging authorisations, switch off Wireless
debugging and USB debugging, restart, then check from the repo:

```bash
tools/venv/bin/python tools/adb-closed.py 192.168.68.51
```

Only "Connection refused" (exit 0) proves the port is closed; `nc` on macOS can be blocked by Local Network
privacy and its "No route to host" proves nothing.

To use any of the commands above later, turn USB debugging (and Wireless debugging) back on, accept the
"Allow USB debugging?" prompt, and if port 5555 is closed, pair as described in the main README.
