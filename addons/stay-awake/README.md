# Stay Awake

Stops an Android TV from turning its screen off on its own. Two rows, worked with the remote alone:

| Row | What it does |
|---|---|
| **Screen timeout** | OK steps through 15 min, 30 min, 1 h, 2 h, 4 h, 8 h and **Never**. It writes Android's own screen-off timeout (`Settings.System.SCREEN_OFF_TIMEOUT`); **Never** is the largest value Android accepts. |
| **Stay awake while plugged in** | OK switches Android's "stay on while plugged in" (`Settings.Global.STAY_ON_WHILE_PLUGGED_IN`) between Off and On. A TV is always plugged in, so On keeps the screen awake. |

For the [Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md): once you
answer **Show card**, its tile reads "Stay Awake". On any other Android TV home it is an ordinary app. The screen
is in English and Hindi.

## How it works

There is no service, receiver or overlay. The app only writes two Android settings when you press OK, and
Android keeps them across restarts. Nothing runs in the background, so it costs no memory on a 1 GB TV.

## Install

Build it from source (see the fork's `MY-BUILD.md`, or `cd addons/stay-awake && ./gradlew assembleRelease`), copy
`app-release.apk` to a pendrive and install it on the TV, then give it its two permissions once, from a
computer with the TV connected over ADB:

```bash
adb shell appops set io.github.kunalsharma.stayawake WRITE_SETTINGS allow
adb shell pm grant io.github.kunalsharma.stayawake android.permission.WRITE_SECURE_SETTINGS
```

Open the app: each row that still lacks a permission shows its command on screen. Nothing else needs ADB.

## Permissions

| Permission | Why |
|---|---|
| `WRITE_SETTINGS` | To change the screen-off timeout. On TVs that have the screen for it, the app opens it; on most it is granted with the first command above. |
| `WRITE_SECURE_SETTINGS` | To switch "stay awake while plugged in". Android only lets ADB grant it (second command above). |

No internet permission: nothing leaves the TV. No accounts, telemetry, ads or libraries.

## Limits

- **Some TVs have their own power timer on top of Android's**, for example "no signal, power off after 4 hours"
  or an auto standby. Those live in the TV maker's own Power settings and no ordinary app can change them. If
  the TV still switches off after Stay Awake is set, look there.
- **The "plugged in" setting depends on the TV reporting itself as powered.** Most TV firmware does; if yours
  does not, the row still turns On but the screen timeout row is the one that works.
- **A screen that never turns off is on all the time.** On an OLED TV this risks burn-in; use a long timeout
  instead of Never. The timeout also does not stop a screensaver, which is a separate Android setting.
- To undo: set **Screen timeout** back to a finite value and the second row to Off, or uninstall the app. The
  two settings stay as they are after uninstalling, so set them first.

## Build and test

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher. In this folder:
`./gradlew testReleaseUnitTest assembleRelease`. The release build is signed with the debug key so it installs
from a pendrive or over ADB; sign your own builds with your own key if you hand them out.

## Tested on

Built and unit-tested on GitHub's runner. Not yet tried on a real TV: that is the next step, and it is the check
the repo asks for before anything is called finished.
