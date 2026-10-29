# Sleep Timer

A sleep timer for Android TV: pick 15, 30, 60, 90 or 120 minutes, and the TV goes to standby when it runs
out. One minute before, a small countdown shows over whatever is playing with **15 more minutes** and
**Turn off now**; if nobody answers, the TV goes to standby. Back leaves the warning but not the timer.

Package `io.github.luyzfernando08.sleeptimer`. You see it as **Sleep Timer** (Portuguese: **Desligar**). On
cleanyourtv's Home it is also a module card, **Sleep Timer**, which opens the picker. A remote button mapped to
its quick bar (the launcher's `app_keys`: `hold:178:io.github.luyzfernando08.sleeptimer/.QuickActivity`) opens a
small bar at the bottom over whatever is playing: Left/Right pick the time, OK closes it. `.BedtimeActivity` in its
place is bedtime in one press: a 30-minute timer plus the Brightness add-on's night mode when it is installed, both
off again on the next press. The picker shows when the
TV will turn off and can cancel the timer.

## How it turns the TV off

An ordinary app cannot put Android to sleep: there is no permission for it. A device admin's `lockNow()` is
ignored on boxes without the device-admin feature (a Xiaomi Mi Box S is one). What works is the accessibility
"lock screen" global action, which since Android 9 sends the device to sleep, so the app has an accessibility
service that does that one thing and nothing else.

On a box behind HDMI, sleeping also turns the TV off when the box has HDMI-CEC on: Android sends
`<Inactive Source>`, waits for pending CEC actions to clear and then sends `<Standby>` to the TV, exactly as
for the remote's power key. One catch, seen on the Mi Box S: for a few seconds after waking, the box runs a
"one touch play" action that holds the `<Standby>` back, so only the box sleeps. A timer of 15 minutes or more
never hits that.

## Install

JDK 17 and Android SDK 34, as for the launcher, and the `tools/venv` from the main README. The `gradlew`
scripts reuse the launcher's pinned Gradle wrapper with this folder as the project. Android 9 or later.

```sh
TV_HOST=192.168.1.50 addons/sleep-timer/build-and-install.sh
TV_HOST=192.168.1.50 addons/sleep-timer/build-and-install.sh --uninstall
```

```powershell
powershell -ExecutionPolicy Bypass -File addons\sleep-timer\build-and-install.ps1 -TvHost 192.168.1.50 [-Uninstall]
```

Then press Home and answer **Show card** if you want the card; Keep hidden leaves an ordinary app tile. To try
it without waiting, the first lines of `build-and-install.sh` have an adb command for a 70-second timer.

## Permissions

- **Accessibility service** (`SleepService`), switched on by the install script: only for the lock-screen
  global action. It asks for no events (`typeAnnouncement` only, which it ignores), cannot retrieve window
  content and reads no keys. The uninstall takes it off the enabled list and leaves any other services alone.
- **Display over other apps** (`SYSTEM_ALERT_WINDOW`), granted with `appops`: Android 10 and later only let an
  app open its warning from the background when it holds this. The app draws no overlay window.
- **Exact alarms** (`SCHEDULE_EXACT_ALARM`), granted with `appops` where it exists (Android 12+), so the
  timer fires on the minute; without it the alarm may come a few minutes late.
- **Auto-start** (`AUTO_START` or `APP_AUTO_START`, TCL only), granted with `appops` and saved with
  `appops write-settings`: TCL's TclAppBoot otherwise refuses to bind `SleepService` while the app is not
  running, so the timer ends without putting the TV to standby. Other TVs do not have the op and ignore it.
- No network, no storage permission, no background service: between setting the timer and its end there are
  two alarms and nothing running.
- **`AlarmReceiver`** is exported only to callers holding `WRITE_SECURE_SETTINGS`, which the adb shell has and
  ordinary apps cannot get, for the test action above. **`TimerActivity`** (exported) is the tile and the card; `QuickActivity` and `BedtimeActivity` (exported) answer a remote button.

## Tested on

Xiaomi Mi Box S (MIBOX4, Android TV 9) with the reference launcher 2.6: the card, the picker, the warning at
one minute, standby on the second, and the TV turned off with it over HDMI-CEC.
