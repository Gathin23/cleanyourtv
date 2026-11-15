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

