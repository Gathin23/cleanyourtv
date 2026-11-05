# Open on wake

Opens the app you choose every time the screen turns on, so the TV wakes straight into YouTube, your live
TV app or whatever you watch first, instead of the home screen. Separate from the launcher.

Package `io.github.luyzfernando08.wakeapp`. You see it as **Open on wake** (Portuguese: **Abrir ao ligar**).
On cleanyourtv's Home it is also a module card, **Open on wake**, which opens its settings.

## Settings

Open the card (or the app's own tile). OK on a row changes it:

| Setting | Choices |
|---|---|
| Open on wake | on (default), off |
| App | opens a grid of every app a launcher would show, except home screens; OK on one picks it. None is chosen at first, so nothing opens until you pick |
| Link | shown only when one is set (see below); OK clears it |
| Wait after waking | 0 s (default), 3 s, 5 s, 10 s, 15 s |
| Try it now | opens the chosen app once |

Choosing another app clears the link, since a link belongs to the app it was meant for.

Home screens (any launcher, including cleanyourtv's) are left out of the list and never opened, even if one is set
from the install script: waking into a launcher that opens the app that opens the launcher would loop.

Nothing opens on a screen that turns on and off again inside the wait, and not twice within 5 seconds. After a
reboot the app opens 8 seconds late, so the home screen is already up and does not cover it.

A box behind HDMI is slower than a TV with Android built in: its screen turns on before the TV has switched
itself on and over to that input, so a few seconds of wait lets the TV catch up before the app starts (on a
Mi Box S the TV took about seven seconds). Used together with TV Static, set the wait to its length or a bit
more, so the app opens after the effect.

## Opening at a link (a channel, a video)

Some apps take an address and open on it: the YouTube app a video or playlist, a live TV app a channel. Setting
one is done from a computer, because a TV remote is a poor keyboard:

```powershell
addons\wake-app\build-and-install.ps1 -TvHost 192.168.1.50 -App com.google.android.youtube.tv -Link 'https://www.youtube.com/watch?v=VIDEO_ID'
```

```sh
TV_HOST=192.168.1.50 addons/wake-app/build-and-install.sh --app <package> --link '<address>'
```

The app is sent a plain `VIEW` for that address and nothing else. If it does not take the address, the app opens
normally. Which address each app understands is the app's own business and not checked here.

## Install

JDK 17 and Android SDK 34, as for the launcher, and the `tools/venv` from the main README. The `gradlew`
scripts reuse the launcher's pinned Gradle wrapper with this folder as the project.

```sh
TV_HOST=192.168.1.50 addons/wake-app/build-and-install.sh
TV_HOST=192.168.1.50 addons/wake-app/build-and-install.sh --uninstall
```

```powershell
powershell -ExecutionPolicy Bypass -File addons\wake-app\build-and-install.ps1 -TvHost 192.168.1.50 [-App <package>] [-Link <address>] [-Uninstall]
```

Then press Home and answer **Show card** if you want the card; Keep hidden leaves an ordinary app tile.

## Permissions

- **Foreground service** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`): `WatchService` keeps one
  receiver for `SCREEN_ON` and `SCREEN_OFF` registered, which Android delivers only to receivers registered at
  run time, never from the manifest. It does nothing else: no polling, no wake lock. Its notification sits in a
  minimum-importance channel.
- **Start at boot** (`RECEIVE_BOOT_COMPLETED`): to start the watcher again after a reboot, and to open the app
  once then. On TCL sets the install script also allows the `APP_AUTO_START` app-op.
- **Display over other apps** (`SYSTEM_ALERT_WINDOW`), granted by the install script with `appops`: Android 10
  and later only let an app start another app's screen from the background when it holds this. The app draws no
  overlay window. On Android 9 it is not needed. On Android 10 and later, without it nothing opens: on a Philco
  (Android 14) with the grant revoked the screen woke and the app stayed shut, and with it granted the app opened.
  The install script grants it; if you install the APK another way, run
  `adb shell appops set io.github.luyzfernando08.wakeapp SYSTEM_ALERT_WINDOW allow`.
- **App list**: a `<queries>` entry for launcher apps, so the picker can list them. No `QUERY_ALL_PACKAGES`.
- No network, no storage permission, nothing collected. Settings stay in the app's private preferences.
- **`WatchService`** is exported only to callers holding `WRITE_SECURE_SETTINGS`, which the adb shell has and
  ordinary apps cannot get, so the install script can start it, and set the app and link, and nothing else can.
- **`SettingsActivity`** (exported) is the app's tile and the module card.

## Tested on

- Xiaomi Mi Box S (MIBOX4, Android TV 9), with the reference launcher 3.1: the picker and the settings card
  with the real remote; opening the chosen app by itself after the screen went off and on again (standby, woken
  with the remote); a link: the YouTube app opened straight on a video screen, not on its home page. A launcher
  chosen from the install script is refused. Not tried: a boot (the 8-second delay) or a link to a live TV
  channel.
- Philco Smart TV by Skyworth (sunnyvale, MediaTek, Android TV 14), launcher 3.1: opening tinyCam by itself on
  waking from standby, and not opening it with the overlay grant revoked (see Permissions). Not tried on a TCL set.
