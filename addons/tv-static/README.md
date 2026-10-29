# TV Static

An old set coming on, every time the screen turns on: the picture opens out of a bright line like a picture
tube warming up, then snow and hiss as if the set were off the air, and after a few seconds it fades into
whatever was on screen. Any key ends it at once and is not passed on. Separate from the launcher.

Package `io.github.luyzfernando08.tvstatic`. You see it as **TV Static** (Portuguese: **Chiado**). On
cleanyourtv's Home it is also a module card, **TV Static**, which opens its settings.

## Settings

Open the card (or the app's own tile). OK on a row changes it:

| Setting | Choices |
|---|---|
| Effect on wake | on (default), off |
| Style | snow (default), colour bars, no signal, random |
| Sound | on (default), muted |
| Length | 3 s (default), 5 s, 10 s |
| Try it now | plays it once |

The styles: **snow**, hard-edged static with a dark band rolling down and a hiss; **colour bars**, a 75% SMPTE
test card with a 1 kHz tone; **no signal**, an old set's blue screen with a blocky on-screen display (a green
"CH 03" in the corner, "NO SIGNAL" blinking in the middle, scan lines and dark tube corners), silent; and
**random**, one of the three each time.

A TV with Android built in shows a picture as soon as it wakes, and 3 seconds is plenty. A box behind HDMI is
different: its screen turns on before the TV has switched itself on and over to that input. On a Mi Box S
the TV only showed a picture about seven seconds after the box woke, so there 10 seconds is the setting that
shows a few seconds of snow.

Nothing plays when the screen turns *off*: by the time an app hears of it the screen is already dark, and
the power key goes to the system first.

## Install

JDK 17 and Android SDK 34, as for the launcher, and the `tools/venv` from the main README. The `gradlew`
scripts reuse the launcher's pinned Gradle wrapper with this folder as the project.

```sh
TV_HOST=192.168.1.50 addons/tv-static/build-and-install.sh            # plays it once when done
TV_HOST=192.168.1.50 addons/tv-static/build-and-install.sh --no-play  # someone is watching
TV_HOST=192.168.1.50 addons/tv-static/build-and-install.sh --uninstall
```

```powershell
powershell -ExecutionPolicy Bypass -File addons\tv-static\build-and-install.ps1 -TvHost 192.168.1.50 [-NoPlay] [-Uninstall]
```

Then press Home and answer **Show card** if you want the card; Keep hidden leaves an ordinary app tile.

## Memory

The snow is a 240x135 bitmap refilled each frame and stretched over the screen without smoothing, so the
dots stay hard blocks. The hiss is generated white noise in one static `AudioTrack` buffer (about 130 KB at
10 seconds). The watcher service, which stays up, used about 8 MB on a Mi Box S.

## Permissions

- **Foreground service** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`): `WatchService` keeps one
  receiver for `SCREEN_ON` registered, which Android delivers only to receivers registered at run time, never
  from the manifest. It does nothing else: no polling, no timer, no wake lock. Its notification sits in a
  minimum-importance channel.
- **Start at boot** (`RECEIVE_BOOT_COMPLETED`): to start the watcher again after a reboot, and to play the
  effect once then. On TCL sets the install script also allows the `APP_AUTO_START` app-op.
- **Display over other apps** (`SYSTEM_ALERT_WINDOW`), granted by the install script with `appops`: Android 10
  and later only let an app open a screen from the background when it holds this. The app draws no overlay
  window; it only opens its own full-screen activity.
- No network, no storage permission, nothing collected. Settings stay in the app's private preferences.
- **`WatchService`** is exported only to callers holding `WRITE_SECURE_SETTINGS`, which the adb shell has and
  ordinary apps cannot get, so the install script can start it and nothing else can.
- **`SettingsActivity`** (exported) is the app's tile and the module card.

## Tested on

- Xiaomi Mi Box S (MIBOX4, Android TV 9), with the reference launcher 2.6: the effect with sound on waking
  from standby with the real remote (at 10 seconds, see above), the card prompt and the card, the settings
  screen and Try it now.
- Philco Smart TV by Skyworth (sunnyvale, MediaTek, Android TV 14): the effect on waking from standby with the
  real remote, which needs the display-over-other-apps grant.
