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
**Scheduled wake depends on firmware:** an Android wake lock can request screen wake only when
the TV delivers the scheduled alarm. Deep standby may delay alarms or prevent screen wake, even
with exact-alarm access. Test sleep and wake separately on the target device.
Schedules are restored after boot, app updates and clock/time-zone changes. A late event is ignored
when more than ten minutes late. Without exact-alarm access Android may delay an event.

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
- **Wake lock** (`WAKE_LOCK`): briefly wakes the screen for a scheduled wake event; no continuously held lock.
- **Start at boot** (`RECEIVE_BOOT_COMPLETED`): restores the saved alarms after restart.
- **Exact alarms** (`SCHEDULE_EXACT_ALARM`), granted with `appops` where it exists (Android 12+), so the
  timer fires on the minute; without it the alarm may come a few minutes late.
- **Auto-start** (`AUTO_START` or `APP_AUTO_START`, TCL only), granted with `appops` and saved with
  `appops write-settings`: TCL's TclAppBoot otherwise refuses to bind `SleepService` while the app is not
  running, so the timer ends without putting the TV to standby. Other TVs do not have the op and ignore it.
- No network or storage permission. Alarms drive the timer; the add-on does not run an app
  foreground service or poll settings.
- **`AlarmReceiver`** is exported only to callers holding `WRITE_SECURE_SETTINGS`, which the adb shell has and
  ordinary apps cannot get, for the test action above. **`TimerActivity`** (exported) is the tile and the card; `QuickActivity` and `BedtimeActivity` (exported) answer a remote button.

To test wake from standby using the protected ADB test receiver:

```sh
# Set a one-off screen wake in 60 seconds, then put the TV in standby with its remote.
tools/venv/bin/python -I tools/tv.py 'am broadcast -a io.github.luyzfernando08.sleeptimer.TEST -n io.github.luyzfernando08.sleeptimer/.AlarmReceiver --ez wake true --ei seconds 60'
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
On that box, a scheduled one-off wake alarm was deferred in standby and fired 272 ms after the
remote woke the box. Exclusion periods and reboot were not tested on this box.
