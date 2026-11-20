# Hikeen smart TV (Realtek RTD285O, Android TV 9)

A white-label smart TV: the set itself reports only "Smart" / "SmartTV"; "Hikeen" is the OEM whose
packages (`com.hikeen.*`, `cn.zeasn.overseas.tv.hikeen.orion`) the firmware carries. Zeasn's "orion"
launcher is the stock Home. The debloat list disables 12 packages; 14 more - this owner's preloaded
apps, casting and voice - are commented out for a reader to choose.

## Quirks of this board

- **No screenshots**: `screencap` writes a 0-byte file in every app on this firmware (Live TV, Home,
  system dialogs). Verify a change with `tools/tv.py 'dumpsys window | grep mCurrentFocus'` instead.
- **ADB is open to anyone**: userdebug build, `ro.adb.secure` unset — port 5555 answers with no
  "Allow" prompt while debugging is on. Close debugging when the session is done.
- **Settings** is `com.hikeen.settings`; `com.android.tv.settings` has no launcher entry on this
  build. The folder's config.json seeds the Hikeen settings app into the launcher's library
  (`library_apps`) and pins it in the dock.
- **One line needs a hand**: `tools/disable.sh` skips
  `com.google.android.leanbacklauncher.recommendations` on this firmware, because its dumpsys prints
  no permission block for the input-driver check. After a reboot with the inputs verified, run the
  list again with `TVINPUT_ALLOW=1` to take that line.
- The two Zeasn RRO overlays are not listed: disabling their base apps leaves them inert.
- Never start `com.hikeen.factorymenu` or the `com.factory.*` packages on this board.

Undo:

    TV_HOST=<tv ip> tools/restore.sh examples/hikeen-smart-rtd285o/debloat.txt com.zeasn.oriontv.open.launcher

## JVC LT-58N785 (second list)

The same firmware (`PTT1.190222.001`, Realtek `rtd285o`) sold as a JVC LT-58N785 (`SmartTV-D58A212`).
Its list is `examples/hikeen-smart-rtd285o/debloat-jvc-lt-58n785.txt`: the first list's lines that apply,
plus ten Android leftovers, with the photo screensaver kept. It was applied in one session and then used
for a day (Netflix, YouTube and Prime Video, with nothing re-enabled), with reboots and several cold power cycles.

- **Screenshots work** on this unit in apps and the new launcher; only the stock Zeasn Home came back empty.
- **Developer options is hidden** even while ADB is on from the factory, and it hides again after a cold
  power cycle. In Android TV Settings (the launcher's Settings pill), Device Preferences → About → press
  Build 7 times brings it back under Device Preferences; the Hikeen SettingCenter app has no such entry.
  `tools/tv.py 'settings put global development_settings_enabled 1'` shows it too, until the next cold start.
  USB debugging off there plus a cold power cycle closed port 5555.
- **A cold power cycle starts on the last TV input** (AV1 on the tested unit), not on Home: about 1.4 s
  after Home is up, the firmware starts `com.android.tv/.MainActivity` on the last input. A reboot over ADB
  does not do this. No menu setting changes it. `power_on_home_always` alone fires too early, and
  `HomeKeyService` with `key_filter` on stops a held Volume key from repeating. What worked on this unit:
  `power_on_home` and `power_on_home_always` true, `key_filter` false and `HOME_KEY_SERVICE=1`, with the
  launcher fix in #4176 (without it, every input picked from the Inputs menu in the first 3 minutes went
  back to Home).

Undo:

    TV_HOST=<tv ip> tools/restore.sh examples/hikeen-smart-rtd285o/debloat-jvc-lt-58n785.txt com.zeasn.oriontv.open.launcher
