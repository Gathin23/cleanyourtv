# Grundig Android UHD TV (Arçelik, `kunyang`)

| | |
|---|---|
| Model | Grundig Android UHD TV, product `GxGrNM`, device `kunyang`, manufacturer ARCELIK, Turkey |
| OS | Android TV 14 (API 34), build `UKNV.260115.001`, MediaTek |
| RAM | 1.8 GB. `MemAvailable` went from 531 MB (stock) to 821 MB after the list, the launcher and a reboot |
| Display | 4K panel, UI at 1920×1080 (factory `Override size`), density 320, `display_density_forced` empty |
| ADB | USB debugging alone opens port 5555, with no pairing. `ro.adb.secure=1` |
| Home | `com.google.android.tvlauncher` (classic Android TV, not launcherx). Only `com.android.tv.settings/.system.FallbackHome` and user-installed apps also claim HOME |

## What worked

- **`pm disable-user` works** for every package in [`debloat.txt`](debloat.txt). After a reboot, `pm list packages -d`
  is identical, so this firmware re-enables nothing at boot.
- **The launcher installs** over `pm install` with no verifier prompt. `launcher/build-and-install.sh` detects
  `com.google.android.tvlauncher` by itself. `dumpsys activity activities | grep mFocusedApp` shows the new Home
  in front, also after a reboot.
- **The Inputs pill** lists HDMI 1–3 and Live TV. Live TV opens `com.mediatek.wwtv.tvcenter/.nav.TurnkeyUiMainActivity`.
  The stock input panel is `com.mediatek.wwtv.tvcenter/.nav.inputpanel.InputsPanelActivity`.
- **Remote buttons:** Home, Input, Settings and the app buttons all work with the stock launcher disabled, with no
  `HOME_KEY_SERVICE` or `INPUT_KEY_SERVICE`. The real buttons were pressed on the remote, not injected.
- **Screensaver:** set it to `com.gathin.tvhome/.ClockDream` before disabling `com.google.android.backdrop`. Backdrop
  held about 160 MB resident.
- **Private DNS** works on this set.

## Keep

Each of these was checked with `dumpsys package` without starting it:

- `fusion.android.tv.demo` answers `android.intent.action.TV_INPUT_BUTTON`, the remote's Input key. It is the same
  package that Vestel-built sets (`jvc-android-tv-menlo/`) keep.
- `com.arcelik.smartremoteservice` receives `GLOBAL_BUTTON` and runs the remote server.
- `com.mediatek.autopair` (persistent) handles Bluetooth remote pairing.
- `com.arcelik.tv.arcelikbacklight` (persistent) is the backlight service.
- `com.mediatek.android.leanbacklauncher.partnercustomizer` stays, following `cecotec-shandao/`.
- The factory packages are never opened: `arcelik.factory.factorystart`, `com.mediatek.tv.factory`,
  `mediatek.factorymenu.ui`, `com.arcelik.tv.rctest`.

`com.google.android.tungsten.setupwraith` claims no HOME on this build, so it stays enabled. It owned one of the
two saved Wi-Fi networks. The network in use belonged to `android.uid.system`.

## Notes

- `com.arcelik.arserviceapp` lives in `/system_ext/priv-app/HotelService`: it is hotel/B2B mode.
- `com.grundig.tv.channel` carries `com.hoppr.tiles` (Hoppr FAST/ad tiles) and the home-screen program rows.
- `adb exec-out screencap` prints `Init wrapper sys mutex successful` first. `tools/tv.py --screenshot` handles it.
- On first boot after the reboot the set came up in Live TV (its power-on source), and Home opened the new launcher.
- Tested on one set. The list was applied and checked the same day, including a reboot.
