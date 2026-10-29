# Grundig 50VLX22LDLBV (Arçelik, `sinchon`)

| | |
|---|---|
| Model | Grundig 50VLX22LDLBV 50", reports itself as "Grundig Android TV"; board `CO3Plus`, device `sinchon`, manufacturer ARCELIK, Netherlands |
| OS | Android TV 11 (API 30), build `RTO1.240312.001`, MediaTek (`m7322`) |
| RAM | 1.4 GB. `MemAvailable` went from 438 MB (stock) to 582 MB after the list, the launcher and a reboot |
| Display | UI at 1920×1080, density 320, `display_density_forced` empty |
| ADB | USB debugging alone opens port 5555, with no pairing. `ro.adb.secure=1` |
| Home | `com.google.android.tvlauncher` (classic Android TV). Only `com.android.tv.settings/.system.FallbackHome` also claims HOME |

The same platform as [`beko-b43b880b`](../beko-b43b880b/), on an older build (`RTO1.240312.001` against
`RTO1.250922.001`). This build ships without the Beko's Anoki ACR, FAST channels and brand channel app.

## What worked

- **`pm disable-user` works** for every package in [`debloat.txt`](debloat.txt). After a reboot, `pm list packages -d`
  is identical, so this firmware re-enables nothing at boot.
- **The launcher installs** over `pm install` with no verifier prompt. `launcher/build-and-install.sh` detects
  `com.google.android.tvlauncher` by itself. `dumpsys activity activities | grep mFocusedApp` shows the new Home
  in front, also after a reboot.
- **The Inputs pill** lists HDMI 1–3 and Live TV.
- **Remote buttons:** Home, Input, Settings and the app buttons all work with the stock launcher disabled, with no
  `HOME_KEY_SERVICE` or `INPUT_KEY_SERVICE`. The real buttons were pressed on the remote, not injected.
- **Screensaver:** set it to `com.gathin.tvhome/.ClockDream` before disabling `com.google.android.backdrop`.
- **Themes** push with `tools/theme.sh`; `nature-calm` with `tile_menu` and `app_library` was checked on the set.

## Keep

- `com.google.android.tungsten.setupwraith` claims no HOME on this build, but `dumpsys wifi` shows it owns the
  saved Wi-Fi network: disabling it would take the TV off Wi-Fi and ADB.
- The Arçelik remote, backlight and TV packages and the factory packages, as listed in the header of
  [`debloat.txt`](debloat.txt). The factory packages were never opened.

## Notes

- **`HOME_KEY_SERVICE` costs key repeat.** With the helper bound, holding Volume Up on the real remote gave one step
  per press instead of a ramp; with it off again, the ramp came back. The same trade as
  [`docs/devices/tcl-55p71b-g09-key-repeat.md`](../../docs/devices/tcl-55p71b-g09-key-repeat.md). No button on this
  set needs the helper.
- **`power_on_home` did not redirect Live TV here.** The set powers on into Live TV
  (`com.mediatek.wwtv.tvcenter/.nav.TurnkeyUiMainActivity`), about 4 s after Home has come up. With
  `power_on_home` and `HOME_KEY_SERVICE=1` it still landed on Live TV; the cause was not found, and the
  owner kept the firmware's behaviour. Home is one press away.
- **The power button does a full restart**, not a standby: uptime starts again from zero after off and on.
- Tested on one set.
