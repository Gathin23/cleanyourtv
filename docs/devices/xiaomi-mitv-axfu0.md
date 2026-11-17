# Xiaomi TV A Pro 32" / A 32 (`MiTV-AXFU0` / `arctictale`): Android TV 14 / Google TV Profile

Tested on **Xiaomi TV A Pro 32-inch** (model `MiTV-AXFU0`, codename `arctictale`, board `t6d`, Amlogic `AMLT950D5`, ~1.5 GB physical RAM / ~970 MB usable by Android OS), running **Google TV on Android 14** (SDK 34, build `UKG3.241124.001.V816.0.9.0.UXFAATV`).

---

## 1. Hardware & System Details

| Property | Value | Notes |
|---|---|---|
| Retail Model | Xiaomi TV A Pro 32" / A 32 (2025/2026 series) | Global / European release |
| System Model (`ro.product.model`) | `MiTV-AXFU0` | |
| Device Codename (`ro.product.device`) | `arctictale` | |
| Product Board (`ro.product.board`) | `arctictale` | |
| Board Platform (`ro.board.platform`) | `t6d` | Amlogic platform identifier |
| Chipset / Hardware (`ro.hardware`) | `amlogic` (`AMLT950D5`) | Quad-core ARM Cortex-A55 |
| Display Resolution | 1280x720 physical (`tvdpi`, 213 dpi) | UI scaled to 720p; panels are HD/WXGA |
| Total RAM | ~970 MB usable (`/proc/meminfo`) | 1.5 GB physical board memory with GPU/VPU reserve |
| Android Version / SDK | Android 14 / API 34 | Google TV experience |
| Firmware Build (`ro.build.display.id`) | `UKG3.241124.001.V816.0.9.0.UXFAATV` | Xiaomi HyperOS-derived ATV base |
| Stock Launcher | `com.google.android.apps.tv.launcherx` | Disables cleanly after setupwraith |
| Live TV App | `com.mitv.livetv` | Uses Amlogic DVB hardware tuner |
| Live TV Input Provider | `com.droidlogic.tvinput` | Hardware TV input provider |

---

## 2. Google TV 14 Intent Resolution & Setup Wizard Quirk

### Home Intent Priorities
Google TV on Android 14 handles `android.intent.category.HOME` differently from older Android TV releases:
- Stock launcher (`com.google.android.apps.tv.launcherx`): priority `2`
- Setup wizard recovery activity (`com.google.android.tungsten.setupwraith/.RecoveryActivity`): priority `1`
- Sideloaded / user launchers (`com.gathin.tvhome`): priority `0`

If `launcherx` is disabled while `setupwraith` remains active, pressing the **Home** button resolves to `RecoveryActivity`. This activity attempts to launch the initial setup flow, fails because the TV is already provisioned, and triggers a `system_server` crash loop (soft reboots every ~60 seconds with dropping Wi-Fi).

### Safe Mitigation
1. Do not disable `launcherx` without also disabling `com.google.android.tungsten.setupwraith`.
2. **Wi-Fi Credential Warning**: On Android TV 14, if the television was set up using the Google Home mobile app, `com.google.android.tungsten.setupwraith` owns the saved Wi-Fi network configuration. Ensure Ethernet is connected or verify network stability before disabling it.
3. If you ever need to perform a factory reset via the system settings menu, restore `setupwraith` and `launcherx` first using `tools/restore.sh` so the first-run wizard operates normally.

---

## 3. Remote Control & Bluetooth Pairing Quirk

The television uses a Xiaomi Bluetooth Voice Remote with dedicated buttons for Power, Inputs, Mi / Xiaomi TV+, Settings, D-Pad, Back, Home, Volume, and streaming shortcuts (Netflix, Prime Video, YouTube, Apps).

### Critical Service Dependency: `com.mitv.overseaservice`
- **Do not disable** `com.mitv.overseaservice`.
- Although named "overseaservice", on `MiTV-AXFU0` this service hosts the Bluetooth Low Energy (BLE) background daemon responsible for remote reconnect handshakes after deep standby (`suspend/resume`).
- Disabling `com.mitv.overseaservice` leads to the remote losing connection after TV standby, requiring a physical power cycle or re-pairing via the back-panel recovery button.

### Keycode Map

| Button | Keycode / Action | CleanYourTV Integration |
|---|---|---|
| Home | `KEYCODE_HOME` (3) | Opens CleanYourTV home screen |
| Back | `KEYCODE_BACK` (4) | Standard back navigation |
| Settings | `KEYCODE_SETTINGS` (176) / `KEYCODE_MENU` (82) | Maps directly to TV quick settings |
| Live TV / Guide | `KEYCODE_GUIDE` (172) / `KEYCODE_TV` (170) | Launches native EPG / Live TV |
| Netflix | `KEYCODE_BUTTON_4` / App intent | Re-enabled automatically by firmware |
| Prime Video | `KEYCODE_BUTTON_5` / App intent | Standard app shortcut |
| YouTube | `KEYCODE_BUTTON_6` / App intent | Standard app shortcut |
| Apps | `KEYCODE_ALL_APPS` (284) | Opens launcher app drawer |

---

## 4. Live TV & EPG Integration

On this Amlogic platform, native terrestrial/cable television (DVB-T/T2/C) and external inputs are handled by:
- **Tuner App**: `com.mitv.livetv`
- **EPG Activity**: `com.mitv.livetv.epg.EpgActivity`
- **Tuning Intent**: `android.intent.action.VIEW` `content://android.media.tv/program`
- **Hardware Provider**: `com.droidlogic.tvinput/.services.TvInputService`

In `config.xml`, configure the live TV app and channel inputs as:
```xml
<string name="live_tv_package">com.mitv.livetv</string>
<bool name="show_inputs_pill">true</bool>
```

Keeping `com.droidlogic.tvinput` enabled ensures instant HDMI switching and hardware signal decoding without overhead.

---

## 5. Performance Tuning for 1 GB RAM Devices

With ~970 MB available to Android OS, aggressive bloat reduction yields noticeable fluidity improvements:
1. **PatchWall & Fast Channels**: Disabling `com.mitv.tvhome.atv`, `com.mitv.tvhome.mitvplus`, `com.mitv.tvhome.michannel`, and `com.mitv.tvhome.oemtab` frees over 200 MB of continuous RAM.
2. **Telemetry & Background Daemons**: Disabling `com.miui.tv.analytics`, `com.xiaomi.statistic`, `com.xiaomi.mitv.detection`, `com.xiaomi.tvqs.overseas.y24`, and `com.xiaomi.mitv.tvmanager` eliminates constant CPU wakeups and background garbage collection pauses.
3. **Launcher Configuration Recommendations**:
   - Enable icon-only buttons (`icon_only_buttons` and `icon_only_inputs` set to `true`) for a minimalist, lightweight top bar.
   - The sky backdrop (`sky_intensity` set to `60`) renders cleanly with hardware acceleration without impacting frame rates.
   - Use CleanYourTV's minimalist screensaver instead of `com.google.android.apps.tv.dreamx` to prevent background network downloads of 4K artwork into RAM.
