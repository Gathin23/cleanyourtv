# Vidda VL7R-PRO: ADB, Projector Optical Engine, and Home Handover on an MT9655 Set

The [debloat.txt](debloat.txt) beside this file is the tested package list; the deep technical
firmware background is documented in [`docs/devices/vidda-vl7r-pro.md`](../../docs/devices/vidda-vl7r-pro.md).
What is here covers what you need to know before and after applying it.

## Model

| Item | Value |
|---|---|
| Retail model | Vidda VL7R-PRO 4K Laser Projector, China |
| Android model / platform | `VIDAA_TV` / MediaTek `MT9655` (platform `mt5877`, hardware `mt9952`) |
| Board | `LASER_MICRO_3DTOF` |
| Android | 14 / API 34 |
| Build display ID | `UQ1A.240105.004.A1 release-keys` |
| Incremental | `07.02.70.202512281739` |
| Security patch | `2024-08-05` |
| RAM | 3.7 GB (`MemTotal: 3890148 kB`) |
| UI resolution | 1080p (1920x1080), density 320 dpi |

`ro.product.model` is `VIDAA_TV` on this platform. The model identifier survives only in
`ro.build.description` (`LASER_MICRO_3DTOF-user ...`), so match on the build ID before applying the list.

## ADB

The USB debugging toggle directly opens **TCP port 5555**. Because `ro.adb.secure=0`, the projector
never prompts "Allow USB debugging?" on screen: any computer on the local network gains immediate shell
access without RSA authentication.

When debloating is complete, turn USB debugging off in Settings and verify the port is closed:
```bash
tools/adb-closed.py <projector ip>
```

## ⚠ Projector Optical Engine: DO NOT disable `com.hisense.screen`

**This is the single most critical difference between Hisense TVs and Vidda Laser Projectors.**

On Hisense TVs (e.g. `hisense-85e7q`), `com.hisense.screen` is often disabled as part of casting debloat.
**On Vidda laser projectors, `com.hisense.screen` (`/system_ext/app/Screen/Screen.apk`) houses the physical
optical motor and lens calibration subsystem:**

- `ElectricFocusingActivity` (电动对焦 / Electric manual focus)
- `CalibrationFocusActivity` (自动对焦校准 / Autofocus calibration)
- `KeystoneActivity` (梯形校正 / Keystone correction)
- `AutomaticEntryActivity` (自动入幕 / Auto screen fit)
- `LenPositionActivity` / `LenZoomActivity` (镜头位移与变焦 / Lens zoom and shift)
- `ProjectorSensorService` (ToF distance sensor, anti-glare eye protection, and gyro motion detection)

If `com.hisense.screen` is disabled, **the physical remote focus button stops responding, boot autofocus fails,
and keystone correction ceases to function.** If you ever find your projector unable to focus after debloating,
restore it immediately via:
```bash
adb shell pm enable --user 0 com.hisense.screen
```

### Note on `com.android.camera2` vs ToF Autofocus
Do not confuse the Android framework API `android.hardware.camera2` (the system Java framework classes
inside `framework.jar` used by `com.hisense.screen` and `cameraserver` to access the ToF sensor) with the
package `com.android.camera2` (`/product/app/Camera2/Camera2.apk`).
`com.android.camera2` is merely the vanilla AOSP camera app UI (for taking photos/videos) and contains 0
exported services or background drivers. On this firmware, the vendor service (`com.jamdeo.tv.service`)
already disables `com.android.camera2` out-of-the-box (`enabled=2`). Autofocus and ToF depth sensing do not
rely on `com.android.camera2`.

## The Home Handover

Three things govern launcher replacement on this Android 14 MT9655 firmware:

1. **`persist.vt.use_home_pkg` must be configured:**
   ```bash
   adb shell setprop persist.vt.use_home_pkg app.orchard.tv
   ```
2. **Manifest priority resolution:** The stock JuUI launcher `com.jamdeo.tv.vod` declares `priority=50`.
   A replacement launcher (e.g. Aerial `app.orchard.tv`) declares `0`. JuUI's fallback second-screen activities
   (`com.keylab.speech.view.vidaa` and `com.hisense.widget.pro`) declare `-150`.
   Disabling `com.jamdeo.tv.vod` ensures the replacement launcher wins every Home resolution.
3. **Recovery path preserved:** `com.hisense.widget.pro` remains enabled in this list (`priority=-150`).
   If the third-party launcher is uninstalled or crashes, the system gracefully falls back to the second screen
   rather than bootlooping.

## Factory Mode M (Design) vs Mode U (User): OTA Upgrades

Entering the factory menu (Settings → Sound → Balance → enter `1969` on the remote):
- **Mode M (Design mode):** Native Android AOSP settings (`com.android.settings`) become visible and ADB
  remains persistently connected. **However, Mode M completely blocks OTA firmware updates.**
- **Mode U (User mode):** Standard shipping mode. OTA firmware updates work normally. AOSP settings is frozen
  (`enabled=2`).
- **Recommendation:** Keep the projector in **Mode U** for daily use so security patches and firmware updates
  are received. Aerial TV and Key Mapper operate completely stably in Mode U.

## Remote Voice Key Remapping (Mode U)

The physical remote's voice button emits Linux scancode 166 (`HISENSE_VOICE_NANOSIC`), mapped to Android
`KEYCODE 4381`. By default, pressing it triggers JuUI's voice assistant.

To repurpose the voice key into a custom launcher or shortcut using **Key Mapper**:
1. Disable the 6 voice bloat packages: `com.keylab.speech.core.vidaa`, `com.keylab.speech.view.vidaa`,
   `com.hispeech.ai.area`, `com.intelligent.speech`, `com.hisense.msvoicetome`, `com.vt.mic.service`.
2. Keep `com.hisense.vidaaassistant` enabled (required for APK installation permissions).
3. Whitelist Key Mapper from battery optimizations and grant background ops:
   ```bash
   adb shell dumpsys deviceidle whitelist +io.github.sds100.keymapper
   adb shell cmd appops set io.github.sds100.keymapper RUN_IN_BACKGROUND allow
   adb shell cmd appops set io.github.sds100.keymapper SYSTEM_ALERT_WINDOW allow
   ```
4. Key Mapper will now intercept keycode 4381 without any system voice UI interference.

## Validation

Verified on **2026-11-17** on Vidda VL7R-PRO (Android 14, MT9655) with Aerial TV 1.99:

| Feature | Result |
|---|---|
| Cold boot directly to Aerial TV | Working |
| Physical remote Home key returns to Aerial | Working |
| Physical remote Focus key & manual electric focus | Working (powered by `com.hisense.screen`) |
| Autofocus calibration on displacement | Working |
| Automatic & manual keystone correction | Working |
| Remote Voice button remapped via Key Mapper | Working (keycode 4381 intercepted cleanly) |
| HDMI signal source switching (Inputs panel) | Working (powered by `com.hisense.tv.tiletransition`) |
| Virtual keyboard (Sogou TV Input Method) | Working (`com.sohu.inputmethod.sogou.tv`) |
| ADB sideloading and package installer | Working (`com.hisense.vidaaassistant`) |
| Wi-Fi, Ethernet, and Bluetooth remote pairing | Working |
