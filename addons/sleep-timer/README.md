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

## Weekly schedule

Open **Schedule** in the timer picker. Add multiple wake or sleep events, choose any weekdays and a
24-hour time for each. Times belong to the actual calendar day: Monday 01:00 means Monday morning.
**Save** applies the list and its On/Off switch; Back discards edits. The schedule screen keeps
Save visible below the scrolling event list. Wake and sleep entries use separate accent colours.

Time and exclusion dates use D-pad numeric fields, without a keyboard or number buttons. Left/Right
select a field, Up/Down change its value (holding accelerates), OK moves to the next field and then
Save. Back cancels the picker. Hours and minutes wrap independently; date fields clamp invalid days
when changing month or year. Select Save in the main schedule screen to apply the completed edits. Existing weekday/weekend sleep
settings are migrated to Monday-Friday and Saturday-Sunday events.

Add any number of date ranges without automatic wake, for example during closures, maintenance
or any planned break in use. Both end dates are included; overlapping ranges work. These
ranges suppress scheduled wake events only, leaving sleep events and manual remote power available.
Sleep events enter standby immediately at the selected time, without the timer countdown.

Wake requires power and Android standby; it cannot start an unplugged or fully powered-off TV.
**Scheduled wake is firmware-dependent:** it needs a working Shizuku power path or firmware that
allows Android alarms to wake the screen. Some devices defer alarms entirely during standby;
Shizuku alone cannot run a wake event whose alarm has not been delivered. Sleep may still work
when scheduled wake does not, so test both actions on the actual TV in its intended standby mode.
Schedules are restored after boot, app updates and clock/time-zone changes. A late event is ignored
when more than ten minutes late. Without exact-alarm access Android may delay an event.

## How it turns the TV off

When Shizuku is connected, the timer uses a small Shizuku user service running as the ADB shell
(UID 2000) for scheduled wake, sleep and the countdown. It accepts only a wake/sleep choice, not
arbitrary commands, and refuses root mode. On CVTE firmware with native fake standby already enabled,
it reads the current standby state before sending the power key, so repeated sleep or wake requests
do not toggle the TV back. This preserves the firmware's network-connected screen-off mode. Other
devices use Android's explicit wake/sleep keys; validate their effect on the actual device.

### Optional Shizuku connection

Some firmware continually removes third-party accessibility services. Install the official
[Shizuku](https://shizuku.rikka.app/guide/setup/) manager and start it with ADB, following the command
shown in that version of the manager. Open Sleep Timer, select the Shizuku row and allow access.
The row then confirms the connection. Accessibility is unnecessary while that connection is ready;
the existing accessibility method remains available otherwise. The SDK is pinned to 13.1.5.

Shizuku access is optional and grants the app shell-level capability; only approve a build whose
source you trust. This implementation exposes only fixed power operations. Turning off access in
Shizuku restores the original accessibility/wake-lock paths. No root or `WRITE_SECURE_SETTINGS`
grant to Sleep Timer is needed.

ADB-started Shizuku must be started again after a full TV reboot. Its saved app authorization does
not start the server. Ordinary screen-off in network-connected standby keeps it running. The timer
shows when Shizuku needs starting; saved weekly events alone cannot restart it. This add-on does not
enable debugging or native fake standby and does not modify the firmware's accessibility policy.

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
- **Wake lock** (`WAKE_LOCK`): briefly wakes the screen for a scheduled wake event; no continuously held lock.
- **Start at boot** (`RECEIVE_BOOT_COMPLETED`): restores the saved alarms after restart.
- **Shizuku access** (`moe.shizuku.manager.permission.API_V23`): optional, approved in Shizuku for the
  fixed power commands above. Its provider is protected by `INTERACT_ACROSS_USERS_FULL`; the timer
  does not request that system permission itself.
- **Exact alarms** (`SCHEDULE_EXACT_ALARM`), granted with `appops` where it exists (Android 12+), so the
  timer fires on the minute; without it the alarm may come a few minutes late.
- **Auto-start** (`AUTO_START` or `APP_AUTO_START`, TCL only), granted with `appops` and saved with
  `appops write-settings`: TCL's TclAppBoot otherwise refuses to bind `SleepService` while the app is not
  running, so the timer ends without putting the TV to standby. Other TVs do not have the op and ignore it.
- No network or storage permission. Alarms drive the timer; Shizuku mode also keeps a small shell user
  service available between calls. The add-on does not run an app foreground service or poll settings.
- **`AlarmReceiver`** is exported only to callers holding `WRITE_SECURE_SETTINGS`, which the adb shell has and
  ordinary apps cannot get, for the test action above. **`TimerActivity`** (exported) is the tile and the card; `QuickActivity` and `BedtimeActivity` (exported) answer a remote button.

To test wake from standby using the protected ADB test receiver:

```sh
# Set a one-off screen wake in 60 seconds, then put the TV in standby with its remote.
python tools/tv.py 'am broadcast -a io.github.luyzfernando08.sleeptimer.TEST -n io.github.luyzfernando08.sleeptimer/.AlarmReceiver --ez wake true --ei seconds 60'
```

This test leaves the saved weekly schedule unchanged.

## Schedule calculation checks

[tests/ScheduleCheck.java](tests/ScheduleCheck.java) checks weekdays, exact boundaries, overlapping
exclusion periods, the year boundary, daylight saving time, empty lists and simultaneous sleep/wake events.
Run with JDK 17 from the repository root (output stays in the ignored work folder):

```sh
mkdir -p work/schedule-check
javac -d work/schedule-check addons/sleep-timer/app/src/main/java/io/github/luyzfernando08/sleeptimer/ScheduleRules.java addons/sleep-timer/tests/ScheduleCheck.java
java -cp work/schedule-check io.github.luyzfernando08.sleeptimer.ScheduleCheck
```

## Tested on

Xiaomi Mi Box S (MIBOX4, Android TV 9) with the reference launcher 2.6: the card, the picker, the warning at
one minute, standby on the second, and the TV turned off with it over HDMI-CEC.

The add-on author also tested this PR on Xiaomi Mi Box S (MIBOX4, Android 9, launcher 3.3): the
Schedule screen, event dialog, weekday list and Portuguese strings worked with a D-pad; the one-off
countdown still put the box to sleep through accessibility, and Open on wake installed and started.
Without Shizuku, a scheduled one-off wake alarm was deferred in standby and fired 272 ms after the
remote woke the box. The Shizuku path, exclusion periods and reboot were not tested on this box.

DEXP 43UCY1 (Android 11, firmware RP1A.200720.011 dev-keys): Shizuku 13.6.0 started as ADB shell,
native CVTE fake standby already enabled. Two one-off sleep/wake cycles changed the native standby
state, kept ADB reachable and reopened Fully Kiosk through the separately configured Wake App add-on.
The second sleep started a previously killed app process. Accessibility remained disabled for this
add-on; Shizuku authorization survived an APK update. Full-reboot server restart remains manual.

### DEXP/CVTE findings and rollback

On this tested firmware, `com.yandex.tv.services.platform` continually rewrites the enabled
accessibility-service list. A direct settings write, including a temporary self-restoration test,
did not persist. The OEM accessibility confirmation activity reported that it was unsupported in
this firmware variant. The temporary broad permission and repair code were removed. Do not disable
the platform service: it also provides essential TV functions. No verified firmware patch was found
during the investigation; this add-on changes no system APK and needs no root.

Native CVTE fake standby turns off the backlight and mutes audio while Android remains awake.
The user confirmed the screen and backlight were off; the indicator differs from deep standby.
This keeps networking available but does not provide deep standby's power savings. The firmware
owns `IS_FAKE_STANDBY_STATUS` and `persist.fake.standby`; do not write either state value manually.
The power backend only uses fake standby when `fake_standby_switch` is already `true`; enabling
this vendor feature is not part of the add-on installer or the repository's debloating procedure.

For the tested TV the original `fake_standby_switch` value was absent. To undo that local
experiment, first wake the TV with its physical remote, then use an authorized ADB session:

```sh
python tools/tv.py 'settings delete global fake_standby_switch'
```

This restores the original value on that device, not a universal default for other firmware.
If a different value was backed up, restore that value instead. A temporary Wi-Fi optimization
setting did not preserve the network in deep standby and was reverted.

Fake standby does not emit Android `SCREEN_ON`; use the existing [Wake App add-on](../wake-app/#screen-only-standby)
with its optional vendor-flag observer to reopen the chosen app. Its physical remote test passed;
the two Shizuku alarm cycles were also verified from native state, ADB reachability and activity logs.
These tests cover one device and short standby intervals, not a long-term reliability or energy claim.
