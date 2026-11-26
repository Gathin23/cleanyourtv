# TCL 55Q7D Pro (G08, G08_4K_GB), Google TV 14, EU build

55" Q7D Pro, model string "Smart TV Pro": product `G08_4K_GB`, client type `TCL-EU-MT9653-S1`, firmware
`UTT2.250416.001 AU02`, board G08 / MediaTek `tcl9618`, remote `BT_RC833A_B5` (Bluetooth). The same board as
the US `G08_4K_US` list and the C755 / QM6K / 85RM7L Google TV lists, on the EU build, which ships
fewer preloads than the US one. A second unit, a 50QM8B from Germany on the same firmware, is in the
section at the end.

`debloat.txt` is the list: 33 packages, disabled with `tools/disable.sh` in batches, reboot-checked. Undo:

```bash
TV_HOST=<tv ip> tools/restore.sh examples/tcl-55q7d-pro-google-tv/debloat.txt com.google.android.apps.tv.launcherx
```

## Remote buttons

Measured on this set with `getevent` and `logcat`, then pressed on the real remote. `BT_RC833A_B5` reports
every key with scancode 240 and `KEY_UNKNOWN` at the input-device level; the keycode comes from
`TclKeyLayoutMap` in `logcat`.

| Button | Android keycode | Handled by |
| --- | --- | --- |
| D-pad, OK, Back, Home, volume, power | — | the firmware |
| Netflix | 4062 | `app_keys`, or Netflix's own receiver starts it |
| Prime Video | 4099 | `app_keys` |
| Disney+ | 4141 | TCL starts Disney+ itself |
| TCL Channels | 4020 | `app_keys` |
| YouTube | 190 (`BUTTON_3`, a global key) | `app_keys` via `InputKeyService` |
| TV icon | 4001 | `app_keys` via `InputKeyService`, see below |
| Settings (gear) | 83 (`NOTIFICATION`) | `settings_key_keycodes` and `83:settings` in `app_keys` |
| Input / Source | — | the firmware; `com.tcl.interactive` disabled does not break it on this build |

4062, 4099, 4141 and 4020 reach the focused window, so the launcher can map them without any helper. 190 and
4001 are consumed before that, which is what `InputKeyService` (`INPUT_KEY_SERVICE=1`) is for. 83 does reach the
window, but only the launcher answers it there, so `settings_key_keycodes` opens Settings on Home alone;
`83:settings` in `app_keys` adds the helper's path for every app, and the `settings_key_keycodes` entry is kept
as the Home-only fallback for when the helper is switched off.

### The TV icon button does nothing

TCL's own `TclShortCutKeyManager` inside `system_server` takes keycode 4001 in `interceptKeyBeforeDispatching`:
`logcat` shows `handle custom key in press down:4001`, `start PackageName:null`, a `startForegroundService`
warning from `handleKeyBehavior`, then `not continue dispatch`. Nothing is launched and no window sees the key,
so neither `VIEW_INPUTS` nor `com.tcl.tv` starts.

The Live TV app itself is fine — `am start -n com.tcl.tv/com.tcl.player.TVActivity` shows it, and the input is
`com.tcl.channelplus/com.tcl.iptv.service.InputService`. With `key_filter` on (the default) the accessibility
helper still receives 4001 before the policy eats it, so this entry in the theme's `config.json` brings the
button back, from any app:

```json
"app_keys": ["4001:com.tcl.tv/com.tcl.player.TVActivity"]
```

`app_keys` is read from a theme pushed with `tools/theme.sh` or from
`launcher/app/src/main/res/values/config.xml` when the launcher is built.

## What was changed on this set

- Launcher `com.gathin.tvhome` (Clean Your TV) as Home, stock `com.google.android.apps.tv.launcherx` disabled.
- Theme `config.json`: black background, four apps per row, four dock tiles, Live TV = `com.tcl.tv`.
- The 33 packages in `debloat.txt`, with `com.tcl.ttvs` kept as the firmware updater, plus the Assistant,
  Cast, TTS, AirPlay/Miracast, the Play Store and the streaming apps.
- Held volume steps once per press instead of ramping while `InputKeyService` is bound; that is the cost of
  the YouTube and TV-icon buttons on this firmware.

## Second unit: TCL 50QM8B (Germany)

50" 50QM8B, model "Smart TV Pro", product `G08_4K_GB`, fingerprint
`TCL/G08_4K_GB/G08:14/UTT2.250416.001/AU02:user/release-keys`, security patch 2025-12-05, about 2.4 GB RAM,
remote `BT_RC833_U1` (Bluetooth LE). The same firmware as the list above, so the same list applies; this owner
started from the QM6K list instead and compared the two afterwards.

- **What was applied:** the 21 packages of `examples/tcl-qm6k-google-tv/debloat.txt` that exist on this build
  (`com.tcl.tv.plus`, `com.tcl.smartlink.core`, `com.tcl.hotelmenu` and `com.tcl.browser` do not), on
  20 October 2026 with `tools/disable.sh`. Sixteen are on this list. `pm list packages -d` on 13 November showed them
  all still disabled, after a reboot and several standby cycles.
- **`com.tcl.ttvs` was off for those 24 days, and it is TCL's updater.** `dumpsys package com.tcl.ttvs` lists
  `versionUpdateApp` activities (`SystemUpdateActivity`, `NetworkUpdateActivity`, `CheckoutVersionActivity`) on this
  build, which agrees with the header above. Firmware and security patch did not change in that time; that does not
  show whether TCL had an update, only that none arrived. It was switched on again on 13 November
  (`pm enable --user 0 com.tcl.ttvs`). Keep it, as this list does.
- **`com.tcl.channelplus` was off too.** Its "Live TV" input (`com.tcl.iptv.service.InputService`) is then missing from
  `dumpsys tv_input`; the HDMI inputs and the tuner input of `com.tcl.tvinput` are there. Broadcast TV was not tried,
  so keep it unless you only use HDMI and apps.
- **`com.tcl.smartalexa` and `com.tcl.ui_mediaCenter`** (Alexa, and TCL's player for USB drives) were off for the 24 days
  without a loss, because this owner uses neither. They are optional, commented lines at the end of `debloat.txt`.
- **`com.google.android.tungsten.setupwraith`** could be disabled without the TV losing Wi-Fi: its saved network did not
  belong to the wizard here, so `tools/disable.sh` did not hold it back.
- **The Assistant** (`com.google.android.katniss`, kept) answered the remote's microphone key with "not available in this
  profile" on the first days. Switching `launcherx` and `setupwraith` back on for a few minutes did not change that, and
  the key worked later with no package change, so it was not the list. The cause was not found.
- **ADB:** this set offers Wireless debugging only. Pair with the code (`adb pair <ip>:<port>`); a plain port 5555 shows up
  after pairing, and the connect port changes after a reboot or a Wi-Fi drop. On macOS the stock `adb` lost its route
  to the TV after the Mac slept ("No route to host", `kill-server` fixed it); `tools/tv.py` kept working.

## Third unit: TCL 65Q7D Pro, Google TV kept as Home

65" Q7D Pro, model "Smart TV Pro", product `G08_4K_GB`, client type `TCL-EU-MT9653-S1`, firmware
`UTT2.250416.001 AU02`, about 2.4 GB RAM. The same build as the first unit: all 33 packages of `debloat.txt` are
on this set, and nothing was disabled out of the box.

- **Stock Home kept.** This owner stays on Google TV, so `com.google.android.apps.tv.launcherx` and
  `com.google.android.tungsten.setupwraith` were left out of the batches. `dumpsys wifi` showed the saved Wi-Fi
  network owned by setupwraith on this set (`cname=com.google.android.tungsten.setupwraith`), so disabling it
  would have taken the TV off Wi-Fi; forget and re-join the network in Settings first if you replace the launcher.
- **What was applied, on 24 November 2026:** the rest of `debloat.txt` but one line (see the screensaver below),
  plus `tv.mopa.ginga` and `com.tcl.magiconnectfree` from the optional lines, in three batches with
  `tools/disable.sh` and a screenshot between them; then the owner's own choices in a fourth: Netflix, Prime Video,
  HBO Max, AirPlay and Miracast. After `sync` and a reboot all of them were still disabled; Home, the Input panel
  (`com.tcl.suspension`), YouTube and YouTube Music worked, and Cast and `com.tcl.ttvs` stayed on. Later that
  evening `com.netflix.tokenmanager` (no use without Netflix), `com.tcl.smartalexa` and `com.tcl.gallery` went as
  well.
- **TCL Channel** was gone from the app row after the batches, while `com.tcl.channelplus` stayed enabled.
- **Screensaver:** with `com.google.android.apps.tv.dreamx` disabled, the screensaver button at the top of Google TV's
  Home did nothing, and `settings get secure screensaver_default_component` still named it. The owner wanted it back:
  `pm enable --user 0 com.google.android.apps.tv.dreamx` brought the button back, and `dumpsys dreams` showed its
  `Backdrop` running. Leave that line out if you keep Google TV and want the screensaver. `com.tcl.gallery` holds
  TCL's own screensavers; with it off, Google's still runs.
- **`com.tcl.ttvs` turns disabled TCL apps back on when it updates them.** Besides the firmware, TCL's updater
  installs newer versions of its own apps into `/data/app`, disabled or not, and the update leaves the package
  enabled. `dumpsys package` shows it: a new `versionName` under a `/data/app` `codePath`, `lastUpdateTime` at that
  moment, and `lastDisabledCaller: com.tcl.ttvs`. On this set `com.tcl.exhibit` and `com.tcl.messagebox` came back
  that way about two minutes after the reboot, and `com.tcl.esticker` and `com.tcl.waterfall.overseas` the next
  day, 17 hours later. Disabling them again is all it takes, and they stay off until the next update; keep
  `com.tcl.ttvs` for the firmware, and check `pm list packages -d` against the list now and then.
- **Media player:** `com.tcl.ui_mediaCenter` (the optional line above) was switched back on. The Input panel always
  shows a "Media player" tile, which only closes the panel while the package is off.

## Picture calibration on this build: the DDC menu and its 3D LUT import

Measured on the third unit (65Q7D Pro) on 24 and 25 November 2026 with an X-Rite i1 Display Pro, DisplayCAL and
HCFR. None of it needs a package from the debloat list: the menu lives in TCL's Settings app
(`com.android.tv.settings`, classes `PictureManager2`, `DDCFileUtil` and `AutoCalibration*VM`) and the native
picture service (`tcl_tv_display_service`, which logs as `VIDEOFUNC: TclDDC`). The import below worked with
`com.tcl.ui_mediaCenter` disabled. It is an ordinary picture-settings page, not a factory menu.

### Where it is and what it holds

Settings → Picture → **DDC** (seen in Movie and in Game mode), with five items: connect a DDC client (network calibration
software), calibration date, import data, export data and delete data. Before anything was imported the date was
empty, and export stayed greyed out with "check the USB drive" even with a working FAT32 stick in: export only
appears once the TV holds calibration data (`hasCalibrationData` reads `getCalStatus`, which logged 1 after the
import). The import page says it takes LUTs and Dolby Vision data from a FAT16 or FAT32 stick.

### The file the import looks for

Import reads the root of the first USB volume and keeps only files whose name is exactly

```
PID<projectId>_<signal>_Movie_<type>
```

- `<projectId>` comes from TCL's device info. It is printed when you press import:
  `adb logcat -d | grep 'projectId = '` gives `Util: projectId = 376061` on this 65Q7D Pro. Read yours rather than
  copy that number, and the same line shows `fileNameStart = PID<projectId>_SDR_Movie_`, which is the prefix it wants.
- `<signal>` is the type of the signal on screen when you press import: `SDR`, `HDR` (HDR10) or `HLG`. Dolby
  Vision goes through a separate lookup with `_DV_Movie_`.
- `<type>` is one of `3DLUT.cube` (an IRIDAS cube, what DisplayCAL writes), `3DLUT.3tcl`, `POST1D.1tcl` or
  `CMS.dat`; the last three are TCL formats of their own. The code also names a `Movie_EndUserCalibration.cfg`;
  what it holds was not looked into.
- There is one slot per signal type, and it is `Movie` in every picture mode: pressed in Game mode, the import
  still asked for `PID376061_SDR_Movie_` (`PictureMode = GAME` in the same log), and a copy named
  `PID376061_SDR_Game_3DLUT.cube` beside it was not offered.

So the SDR 3D LUT for this set is `PID376061_SDR_Movie_3DLUT.cube`, imported while an SDR picture is showing.
`logcat` confirms the import with `importCalFile type=2` and then
`getCalFileList ret=[PID376061_SDR_Movie_3DLUT.cube]`.

### The LUT has to be 17×17×17

The TV keeps a 17-point grid (the log prints node values 0, 64, … 960, 1023). A 33³ cube is accepted all the same
and read as if it were 17³: the first 4913 rows land on the 17³ nodes, so node 16, which should hold full red,
gets the row for half red. The import then logs a run of
`TclDDC [save3dlutByData] … negative overrange! … value=1023 , cal value = 492 ,new value = 511`, because no node
may move further than ±511 (of 1023) from its nominal value, and the picture is wrong. Choose **3D LUT resolution
17x17x17** in DisplayCAL.

A 33³ cube made already does not need a new measurement: the 17-point grid (j/16) is exactly the even nodes of the
33-point grid (2j/32), so taking every second node gives the same values DisplayCAL would have computed at 17.
`.cube` rows run with red fastest, then green, then blue:

```python
rows = [l.split() for l in open("in-33.cube") if l[:1] in "0123456789.-"]
with open("PID376061_SDR_Movie_3DLUT.cube", "w", newline="\n") as f:
    f.write("LUT_3D_SIZE 17\nDOMAIN_MIN 0.0 0.0 0.0\nDOMAIN_MAX 1.0 1.0 1.0\n")
    for b in range(0, 33, 2):
        for g in range(0, 33, 2):
            for r in range(0, 33, 2):
                f.write(" ".join(rows[r + 33 * g + 33 * 33 * b]) + "\n")
```

The 17³ cube behind the results below was made that way.

### Where the LUT applies

After a successful import the TV adds a picture mode of its own, shown as "Калибровка фильма" in the Russian UI
(roughly "film calibration"; the English label was not seen), and the results below were measured there. Whether
the other modes get the LUT too was not checked. The LUT corrects the TV as it was measured, so that mode's gamma, white balance, backlight,
local dimming and peak brightness have to match the mode the profile was taken in. A LUT from an earlier profile,
taken before the white balance was changed, moved the white from 6355 K to 6216 K for that reason.

### Settings that worked

DisplayCAL 3.x, profile taken in Movie with the menu closed, LUT deleted, the measurement window on the TV:

| Tab | Setting |
|---|---|
| Display & instrument | i1 Display Pro, mode Refresh (generic), correction `Samsung Q9 75_ (i1 Pro).ccss` ("LCD Quantum Dot LED (Samsung QLED Q9)" from DisplayCAL's online database) |
| Calibration | white point 6500 K, white level, black level and tone curve all *As measured* (no video-card curves; the TV gets none of them) |
| Profiling | XYZ LUT + matrix, auto-optimized test chart, 175 patches |
| 3D LUT | Rec. 709 source, Rec. 1886 tone curve, gamma 2.4 absolute, black output offset 0 %, inverse device-to-PCS, absolute colorimetric with white point scaling, IRIDAS `.cube`, full range in and out, **17x17x17** |
| Verification | extended verification test chart, simulation profile Rec. 709 used as display profile, Rec. 1886 gamma 2.4 |

Result, both DisplayCAL verification reports with the same correction, brightness 11 (about 143 cd/m²),
local dimming Medium, peak brightness Minimum:

| | Menu calibration only | With the 17³ LUT |
|---|---|---|
| White | 6542 K, ΔE00 0.57 from D65 | 6463 K, ΔE00 0.44 |
| Average ΔE00 | 1.50 | 0.70 |
| Maximum ΔE00 | 4.93 (blue 0/0/128) | 1.87 (blue 0/0/255) |
| Blue 0/0/255 | 4.26, 14 % too dark in HCFR | 1.87 |
| Grey 10–95 % | ΔE00 1.0–2.3 | 0.19–1.03 |
| Gamma | 2.36 in the shadows, falling to 2.07 near white | 2.37–2.43 |

The menu values are this panel's, not a recipe: measure your own.

### What the menu can and cannot fix

- **White balance** is two-point: gain and offset for R, G and B. That set white and the grey tint well, but not
  the gamma bump near white or the blue.
- **No per-colour CMS on this build.** The picture menu has one global tint (`PICTURE_TINT` is the only hue key in
  the Settings app). Blue on this panel is about 14 % too dark and slightly purple; only the LUT corrects it.
- **Gamma** offers 2.2, 2.4 and BT.1886. With 2.4 instead of BT.1886 grey from 10 to 50 % tracked within 2 %,
  and the 60–90 % range stayed 6–15 % too bright until the LUT. Movie stayed on BT.1886 for the profile, and the
  LUT's Rec. 1886 target (pure 2.4 with this set's black of 0 cd/m²) does the rest.
- **Local dimming and peak brightness change the reading.** With the picture menu open over the patch, white read
  ΔE 0.1; with the menu closed, 1.1. A small patch on black is the worst case for a Mini LED backlight, so set the
  white balance with the menu closed and the patch the profile will use.

### The colorimeter correction decides the white

The first LUT made things worse in HCFR (white ΔE 0.4 → 2.1) because HCFR was set to "LCD PFS Phosphor IPS" while
DisplayCAL used the quantum-dot correction: each called a different white D65. Use one correction in every tool.
TCL sells this model as quantum-dot Mini LED, which is why the QD correction was chosen; only a spectrometer
would tell which is right. HCFR lists the same Argyll `.ccss` once it is in `%APPDATA%\color`, under its
`TECHNOLOGY` field and the display name in brackets, so the Samsung file shows up as "Unknown (SAMSUNG)".

### Other things seen in the picture settings

- The Settings app reports every menu you open, and how long you stay, through
  `DataReportImpl: reportFocusedData` and `doReportQualityData`. It is part of `com.android.tv.settings`, which the
  list keeps.
- The internal picture modes, from `logcat`: `SDR_AI_ADAP, VIVID, STANDARD, STADIUM, SDR_FILMMAKER, STUDIO, GAME, PC`.
