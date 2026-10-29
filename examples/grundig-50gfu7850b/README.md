# Grundig 50 GFU 7850 B 50" 4K Android TV (Arçelik, `sinchon`)

| | |
|---|---|
| Model | Grundig 50 GFU 7850 B 50" Ultra HD (4K) Android TV, product `CO3Plus`, device `sinchon`, manufacturer ARCELIK, Turkey |
| OS | Android TV 11 (API 30), build `RTO1.251027.001`, MediaTek M7322 SoC |
| RAM | 1.38 GB. `MemAvailable` went from ~280 MB (stock) to ~499 MB after the list, CleanYourTV, and a cold reboot (+360 MB resident RAM recovered) |
| Display | 4K panel, UI natively rendered at 1920×1080 @ 60 Hz, density 320 dpi |
| ADB | USB debugging opens port 5555 over Wi-Fi and Ethernet. `ro.adb.secure=1` |
| Home | `com.google.android.tvlauncher` (classic Android TV leanback, not Google TV launcherx). Only `com.android.tv.settings/.system.FallbackHome` also claims HOME |

## What worked

- **`pm disable-user` works** cleanly for every package in [`debloat.txt`](debloat.txt). After cold reboots, `pm list packages -d` remains identical.
- **The launcher installs** and owns Home (`mFocusedApp`) reliably across cold reboots. CleanYourTV runs at ~38.3 MB PSS (compared to ~140–160 MB for the stock launcher suite and ~140 MB for Google Backdrop with its Chromium sandbox process).
- **Remote buttons:** Home, Input, Settings and app keys work directly with no extra helper service.
- **Live TV:** Routes to `com.mediatek.wwtv.tvcenter/.nav.TurnkeyUiMainActivity` directly from CleanYourTV's Inputs menu.
- **Screensaver:** Replaced `com.google.android.backdrop` with `com.gathin.tvhome/.ClockDream`.
- **HDMI-CEC Native Device Names:**
  On this firmware, Android TV Settings pre-assigns generic labels ("Game console", "Blu-ray") to ports in `tv_input_custom_labels`, which overrides the CEC broadcast names. Clearing this setting via:
  ```bash
  adb shell settings put secure tv_input_custom_labels '""'
  ```
  allows CleanYourTV's native **Inputs** dropdown to surface connected HDMI-CEC device names directly (e.g. `PlayStation 5 · HDMI 2`, `BD HTS · HDMI 3`) without needing a separate add-on app.

## Keep

- `fusion.android.tv.demo`: Handles `android.intent.action.TV_INPUT_BUTTON` (the remote's Input key).
- `com.arcelik.tv.arcelikbacklight`: Persistent backlight control daemon (preserves picture & brightness settings).
- `com.mediatek.tv.service`, `com.mediatek.tvinput`, `com.mediatek.wwtv.tvcenter`: Core TV tuner and HDMI framework.
- `com.google.android.tungsten.setupwraith`: Manages Wi-Fi credentials and network state.
- `com.arcelik.ChannelEditor`: TV channel management tool.
- Factory packages, never opened: `arcelik.factorymenu.hatone`, `com.mediatek.tv.factory`, `mediatek.factorymenu.ui`.

## Tested on

Grundig 50 GFU 7850 B (50", 4K, 2021/2022). Debloat applied, verified over multiple cold boots, and tested with connected CEC devices (PlayStation 5, LG Home Theatre System) and streaming apps (Stremio, Netflix, YouTube TV, Spotify).
