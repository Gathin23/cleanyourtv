# Brightness

Dims the whole picture below what the TV's own settings allow, and warms it at night, over every app, Live
TV and the home screen. For the [Clean Your TV](../../README.md) launcher it declares a
[module card](../../docs/modules.md): once you answer **Show card** its tile reads "Brightness" with an amber
glow. On any other Android TV home it is an ordinary app.

![Brightness in night mode at 40 %, warm, over its own settings](screenshot.jpg)

A remote button mapped to its quick bar (the launcher's `app_keys`:
`hold:191:io.github.maxhayim.brightness/.QuickActivity` puts it on a held Netflix button) opens a small bar at
the bottom over whatever is playing: Left/Right step the brightness, OK closes it. `.NightToggleActivity` in its
place switches night mode in one press; Sleep Timer's bedtime button uses it too. The tile and the card open the
settings, one list worked with the remote alone:

| Row | What it does |
|---|---|
| **Dimming** | The master switch, focused first. **Off puts the screen back to normal at once**; the other settings stay for next time. |
| **Brightness** | A slider from 100 % down to 1 % (◀ ▶): 5 % steps, then 1 % steps below 10 %. Used outside night mode. |
| **Night mode** | Off, On (now, until switched off) or Scheduled (every day between two times). |
| **Night brightness** | Its own slider, 1 % to 100 %. |
| **Warmth** | Night mode only: an amber tint that takes blue out of the picture, 0 to 100 %. |
| **Night starts**, **Night ends** | The schedule, in 15 minute steps; a night may run past midnight (21:00 to 07:00 is the default). |
| **TV picture at night** | Realtek-based TVs only: also turns the TV's own backlight, contrast and brightness down for the night, with dynamic backlight on, and puts the day's picture back in the morning. See below. |
| **Night backlight**, **Night contrast**, **Night TV brightness**, **Night dynamic backlight** | The TV's own picture for the night: backlight 0, contrast 15, brightness 35 and dynamic backlight on by default. Changes reach the TV at once while it is night. |
| **Use the TV's picture now** | Takes what is set in the TV's own picture menu as the night values. |

Every change shows at once, over the settings screen too, so you see the result as you move the slider. The
settings are kept, and the layer and the schedule come back after a reboot.

The layer never goes below 1 %. Below about 5 % the picture is hard to see, so the switch back is always the first row. If it is ever too
dark to find the way, any of these gives the normal screen back:

- open Brightness and press OK on **Dimming**;
- from a computer, `adb shell am broadcast -a io.github.maxhayim.brightness.OFF -n io.github.maxhayim.brightness/.OffReceiver`;
- `adb shell am force-stop io.github.maxhayim.brightness`, or uninstalling it.

## The TV's own picture

The dimming layer darkens the picture, but the panel's backlight keeps glowing behind it, and that glow is most
of the light in a dark room. On TVs built on Realtek chips, like the test TV,
the TV's picture menu is made of settings slices, and Brightness drives the same controls the menu does. When
night begins it notes the TV's current picture, sets the night one, and puts the noted one back when night ends
or dimming is switched off. If the TV already shows the night picture when night begins (set by hand earlier),
the day picture is the one noted the night before, or the TVs' Standard default (backlight 100, contrast 50,
brightness 50, dynamic backlight off).

The TV asks once, "Allow Brightness to show RtkSliceProvider slices?"; the app shows a row that opens the prompt.
On other TVs these rows don't appear.

## How it works

This is software dimming. A full-screen window of one colour sits over everything (`TYPE_APPLICATION_OVERLAY`),
black at the missing brightness with the amber folded in for warmth. It never takes focus or keys, so the remote
keeps controlling whatever is on screen. A foreground service holds the window and switches night
mode at the scheduled times, and it stops itself when there is nothing to draw and no schedule to follow.

## Permissions

| Permission | Why |
|---|---|
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | To draw the dimming layer over other apps. Granted by `install.sh`, since many TVs hide this setting. Without it the app says so and draws nothing. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | One service holds the layer and follows the schedule. It runs only while dimming is on and there is a layer to draw or a schedule to follow. |
| Start at boot (`RECEIVE_BOOT_COMPLETED`) | To put the layer and the schedule back after a reboot or an update, if dimming was on. |

On Realtek TVs, the system's slice permission for the TV's picture settings, asked once on the TV (see "The TV's
own picture"). It lets the app read and change the picture menu's settings and nothing else.

No internet permission: nothing leaves the TV. No accounts, telemetry, ads or libraries. Settings stay in the
app's own storage (backups are off). The `OffReceiver` is open to any sender on purpose: it can only switch
dimming off.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher. From the repo root, with ADB on:

```bash
TV_HOST=<tv ip> addons/brightness/install.sh
```

It builds the add-on, installs it, allows display over other apps (and TCL's auto-start), and opens it. Press
Home: the launcher asks once whether to show the card. Change the answer later by holding OK on Settings, then
Module cards. To build only: `cd addons/brightness && ./gradlew assembleRelease`; the JVM tests run with
`./gradlew testReleaseUnitTest`.

The release build is signed with the debug key so it installs over ADB like the launcher. Sign your own builds
with your own key if you hand them out; the launcher pins its Show answer to the signing key.

## Limits

- **The layer darkens the picture, not the backlight.** Whites get dimmer but blacks don't get blacker. On
  Realtek TVs the TV picture option lowers the backlight itself; elsewhere, lower the backlight in the TV's own
  picture settings and use the layer for the rest.
- **The TV picture option uses the TV's settings slices, which aren't public API.** A firmware update could rename
  them; then the rows still show but the TV no longer follows, and the TV's own menu works as always.
- **Some things are drawn above it.** Android keeps the on-screen keyboard, the volume bar, toasts and the
  system's own panels above app overlays, so they show at full brightness.
- **Some screens hide it.** Since Android 12, permission prompts and the app installer can hide overlays while
  they are open.
- **Menus drawn by the TV itself** are not Android windows on some sets (picture menus on several TCL and Sony
  models), so they show at full brightness.
- **Video planes vary.** On most TVs video, HDMI inputs and the tuner sit under Android's graphics and are
  dimmed with the rest. Some chips put protected, HDR or tunnelled video on their own plane, which may show
  undimmed or lose the tint. Check on your TV with each source you use.
- **The setting may be hidden.** Some firmware (TCL, some Fire TV builds) has no screen to allow display over
  other apps; `install.sh` allows it over ADB, or the app shows the ADB command.
- **Aggressive memory managers** can stop the service on 1 GB TVs. If they do, the layer goes and the screen
  is brighter, never darker; opening the app starts it again.
- **Below 20 % an air-mouse pointer can't click through.** Since Android 12 a window that lets touches through
  is drawn at most 80 % opaque, so darker than that the layer keeps touches. TVs have no touchscreen and the
  remote's keys are never affected; only a pointer (an air mouse) is.
- Screenshots and screen recordings include the layer.

## Tested on

MOKA R8G (Android 14), over ADB, with its own home app: the overlay permission granted by ADB, the layer drawn
over the settings screen and over Home, the scheduled night mode taking over at 21:13 inside a 21:00 to 07:00
window (40 %, then down to 1 %, warm), each slider changing the layer at once, **Dimming** Off and the ADB
broadcast removing the layer at once and On bringing it back, the TV picture option asking for and getting
the slice permission and moving the TV's own backlight to 1 and back to 0 as the slider moved, the TV's day
picture (backlight 100, contrast 50, brightness 50) put back by itself at 07:00 after a night at backlight 0,
the card prompt, the card first in the grid and opening the app (on a home app that implements the module-card
spec; not yet on this repo's launcher), and the layer back by itself after the app was updated. Memory: about 22 MB with the layer up.

The JVM tests cover the levels, the colour arithmetic, the schedule (past midnight, edges, empty windows), the
1 % floor, the fine steps and the clamping of stored values.

Still to check on a TV running this repo's launcher (the test TV's home app has no module cards):

1. The card on this repo's launcher.
2. The start of the night without opening the app, and the day picture coming back when dimming is switched
   off.
3. The layer over a streaming app playing video, Live TV and an HDMI input, with every remote key still
   reaching the app below.
4. A reboot with dimming on brings the layer and the schedule back.

## Maintainer

[@maxhayim](https://github.com/maxhayim)
