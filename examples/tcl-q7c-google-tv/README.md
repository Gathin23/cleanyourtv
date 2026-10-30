# TCL Q7C (G08, Google TV, Android 14), European firmware

TCL Q7C QD-Mini LED, device `G08`, model "Smart TV Pro", product `G08_4K_GB`, firmware `UTT2.250416.001 AU02`,
MediaTek `tcl9618`, about 2.4 GB RAM. The same firmware build as the 85RM7L list and the same G08 board as the
C755, QM6K and 75T7 (US) lists. Set up on Ethernet in Austria. Both halves work on this set: the debloat list and the
Clean Your TV launcher as Home.

| File | What it is |
|---|---|
| `debloat.txt` | The 37 packages that were disabled, grouped and commented |
| `config.json` | Dock, hidden TCL tiles, Live TV app, the Settings and YouTube buttons; push it with `tools/theme.sh` |

**How much testing is behind it:** one set, from 21 October 2026. The first 35 packages were disabled in five batches
and survived a reboot. launcherx and setupwraith were disabled afterwards, for the new launcher. YouTube, Live TV,
the HDMI list in the Inputs menu, the Input, Settings and YouTube buttons, and D-pad navigation were checked on the
TV.

All commands run from the repo root with the TV's IP in `TV_HOST`. USB debugging must be on while you run them.
On this set, switching it on opens port 5555 by itself. Wireless debugging cannot be switched on over Ethernet,
and it is not needed.

## Install

The order matters: setupwraith goes while Google TV's home still owns Home, then `build-and-install.sh` starts the
new launcher and only then disables launcherx. `disable.sh` skips launcherx on the first run (its Home guard), which
is what you want here.

```bash
FORCE_SETUPWRAITH=1 TV_HOST=<tv-ip> tools/disable.sh examples/tcl-q7c-google-tv/debloat.txt
INPUT_KEY_SERVICE=1 HOME_KEY_SERVICE=1 TV_HOST=<tv-ip> launcher/build-and-install.sh
TV_HOST=<tv-ip> tools/theme.sh examples/tcl-q7c-google-tv
```

`FORCE_SETUPWRAITH=1` is only for a set on Ethernet with no saved Wi-Fi: there `dumpsys wifi` shows no network
owner, and `disable.sh` then plays safe and skips setupwraith. On Wi-Fi, run
`tools/tv.py "dumpsys wifi | grep -o 'cname=[^ ]*'"` first. If the network belongs to setupwraith, forget it and
join it again in Settings before you disable the wizard.

`build-and-install.sh` needs JDK 17 for Gradle 8.9. On a Mac where `java_home -v 17` answers with a newer JDK, pass
`JAVA_HOME` explicitly.

## Undo everything

```bash
TV_HOST=<tv-ip> tools/restore.sh examples/tcl-q7c-google-tv/debloat.txt com.google.android.apps.tv.launcherx
```

## Why the Home key takes `pm disable-user` and nothing else

Confirmed again on the zh-TW unit of this board. Four other routes were tried on a live set and do not work on
this firmware:

| Route | Result |
|---|---|
| `cmd package set-home-activity --user 0 <component>` | Prints `Success`, but nothing changes: `resolve-activity` still returns the old launcher and no preferred activity is recorded. A no-op on this build. |
| `android:priority` on the HOME `<intent-filter>` | Ignored for third-party apps. The installed APK was pulled back and verified to carry `priority="2"`, then `"1000"`; the package manager still reported `priority=0` and kept resolving to `setupwraith`. |
| `cmd role add-role-holder android.app.role.HOME <pkg>` | Succeeds — `dumpsys role` shows `holders=<pkg>` — but HOME resolution ignores it. |
| `pm disable-user <setupwraith>/<...>.RecoveryActivity` | Refused: `SecurityException: Shell cannot change component state for ...` |

The last one is why the whole `setupwraith` package has to go, and why the Wi-Fi-owner check above matters.

## Remote buttons

Every button on this remote arrives as scancode 240 (`KEY_UNKNOWN`). TCL's `TclKeyLayoutMap` tells them apart by HID
usage and turns them into keycodes, so map them by keycode (scancode entries would all collide). Found with
`logcat -v time` while pressing the real buttons: look for `InputReader ... TIDD: keyCode=`.

| Button | Keycode | Without Google TV's home | Here |
|---|---|---|---|
| Home | 3 | works | |
| Input | 178 (`KEYCODE_TV_INPUT`) | works: TCL draws its own source panel (`com.tcl.suspension`) | |
| Settings (gear) | 83 (`KEYCODE_NOTIFICATION`) | does nothing (it was launcherx's dashboard) | `settings_key_keycodes: [83]`, `HOME_KEY_SERVICE=1` |
| YouTube | 190 (`KEYCODE_BUTTON_3`) | does nothing | `app_keys: 190:com.google.android.youtube.tv`, `INPUT_KEY_SERVICE=1` |
| Netflix | 4062 | does nothing | left unmapped (Netflix is off) |
| Prime Video | 4099 | does nothing | left unmapped |
| TCL Channel | 4020 | does nothing | left unmapped |
| (app button) | 4057 | does nothing | left unmapped |
| (app button) | 4141 | does nothing | left unmapped |
| Mic | 84 (`KEYCODE_SEARCH`) | with katniss off, opens the Play Store search | |

The YouTube button sends 190 here. The G09 list maps 4065 for its YouTube button, so check yours. Re-pass both
`*_KEY_SERVICE=1` flags on every reinstall: a reinstall drops the services from the accessibility list.

## Notes for this set

- **Check that ADB is really closed when you are done.** Here the USB debugging switch in Developer options
  did not take: `settings get global adb_enabled` still said `1`, and port 5555 was still open after a reboot.
  `tools/tv.py 'settings put global adb_enabled 0'` closed it. The connection drops mid-command, so a non-zero exit is
  expected. Then revoke the authorisations on the TV, reboot, and check with `tools/adb-closed.py <tv-ip>`
  ("Connection refused").
- The UI runs at a 1920×1080 override on the 3840×2160 panel, at 60 Hz: the only display mode Android
  offers. This is the stock setting, not something the launcher changed.
- The Inputs menu lists the HDMI-CEC device names (here "Soundbar · HDMI 1", "PC · HDMI 2") from the TV input
  framework, plus Live TV (`com.tcl.tv`).
- Screenshots of Live TV and HDMI come back empty (0 bytes). `dumpsys activity activities | grep topResumedActivity`
  shows `com.tcl.tv/com.tcl.player.TVActivity`.
- Ambient mode (`dreamx`) was left enabled. Whether its screensaver still works with launcherx off was not checked.
