# Vidda VL7R-PRO (China, Android 14 on MT9655): Projector Optical Subsystem and Home Resolution

Owner report for a China-market Vidda VL7R-PRO 4K tri-color laser projector. The tested package list
lives in [`examples/vidda-vl7r-pro/debloat.txt`](../../examples/vidda-vl7r-pro/debloat.txt); what is here
is the firmware architecture, the optical engine mechanisms specific to laser projectors, and how Home
handover and remote key remapping operate on this MT9655 platform.

## Observed Firmware

| Field | Observed value |
|---|---|
| Retail model | Vidda VL7R-PRO (4K Laser Projector) |
| Manufacturer / Brand | `Hisense` / `Vidda` |
| `ro.product.model` / `.name` / `.device` | `VIDAA_TV` / `VIDAA_TV` / `MT9655` |
| Board / Platform | `LASER_MICRO_3DTOF` / `mt5877` |
| Hardware SoC | `mt9952` (MediaTek MT9655) |
| Android / API | `14` / `34` |
| Build display ID | `UQ1A.240105.004.A1 release-keys` |
| Build fingerprint | `Hisense/VIDAA_TV/MT9655:14/UQ1A.240105.004.A1/07.02.70.202512281739:user/release-keys` |
| Build description | `LASER_MICRO_3DTOF-user 14 UQ1A.240105.004.A1 07.02.70.202512281739 release-keys` |
| Security patch | `2024-08-05` |
| ABI | `arm64-v8a` |
| RAM | 3.7 GB (`MemTotal: 3890148 kB`) |
| `ro.adb.secure` | `0` |
| `service.adb.tcp.port` | `5555` |
| Total installed packages | 137 |
| Disabled after debloat | 52 |

As with the Hisense MT9655 TV family, `ro.product.model` reports only generic `VIDAA_TV`. The laser
projector identity is recorded in `ro.build.description` (`LASER_MICRO_3DTOF-user ...`).

## Network ADB Access

Enabling the USB debugging switch in Developer Options simultaneously activates **network ADB on port 5555**.
Because `ro.adb.secure=0`, the system omits the RSA authorization dialog: any machine on the local network
obtains immediate rootless shell access (`uid=2000`).

Ensure USB debugging is turned off when modifications are complete, and verify using `tools/adb-closed.py <projector ip>`.

## The Projector Optical Engine: `com.hisense.screen`

On flat-panel TVs, package lists frequently disable `com.hisense.screen` under the assumption that it
merely hosts vendor casting helpers. **On Vidda laser projectors, this package is the physical optical engine.**

Located at `/system_ext/app/Screen/Screen.apk`, `com.hisense.screen` encapsulates:

- **`ElectricFocusingActivity`:** Electric manual focusing interface driven by remote keys.
- **`CalibrationFocusActivity`:** High-precision autofocus pattern generator and ToF calibration loop.
- **`KeystoneActivity`:** 4-point, 8-point manual, and camera-assisted automatic trapezoidal correction.
- **`AutomaticEntryActivity`:** Screen edge detection and motorized obstacle avoidance.
- **`LenPositionActivity` & `LenZoomActivity`:** Optical motorized zoom and lens displacement drivers.
- **`ProjectorSensorService`:** Background monitoring for ToF distance sensors, anti-glare human-body
  detection (reducing laser brightness when someone steps in front of the lens), and gyro motion detection.

### Observed Failure if Disabled
Disabling `com.hisense.screen` cuts off IPC to the optical motor daemon:
- The physical focus key on the remote controller stops responding completely.
- Autofocus triggers at power-on fail silently.
- Keystone calibration cannot be opened.

**Rule:** `com.hisense.screen` MUST remain active on all Vidda/Hisense projector configurations.

### Framework `android.hardware.camera2` vs App `com.android.camera2`
A common misconception is that the package `com.android.camera2` is required for the ToF sensor.
Inspection of `/product/app/Camera2/Camera2.apk` shows it is strictly the vanilla AOSP camera UI app,
exposing only `CameraActivity` and `VideoCamera`, with **zero background services and zero AIDL interfaces**.
In fact, the vendor system service (`com.jamdeo.tv.service`) disables `com.android.camera2` on boot
(`enabled=2`).
The actual ToF sensor and autofocus processing pipeline runs entirely through:
1. MediaTek and Hisense hardware HALs (`vendor.mediatek.camera.provider-V1-external-service`,
   `vendor.hisense.hardware.cameracontrol@1.0-service`)
2. Android native `cameraserver` daemon
3. Android system framework classes (`android.hardware.camera2.*` in `framework.jar`)
4. System optical controller (`com.hisense.screen`)

`com.android.camera2` plays no role in projector autofocus or sensor telemetry.

## Home Handover Mechanics

Four candidate activities register for `android.intent.action.MAIN` + `android.intent.category.HOME`:

| Package | Activity | Manifest Priority |
|---|---|---|
| `com.jamdeo.tv.vod` (Stock JuUI Launcher) | `com.ju.main.launcher.LauncherActivity` | `50` |
| Third-party launcher (Aerial TV `app.orchard.tv`) | `app.orchard.tv/.MainActivity` | `0` |
| `com.keylab.speech.view.vidaa` (JuUI Second Screen) | `com.ju.secondscreen.HomeActivity` | `-150` |
| `com.hisense.widget.pro` (Card Home) | `com.ju.secondscreen.HomeActivity` | `-150` |
| `com.android.settings` (AOSP Fallback) | `.FallbackHome` | `-1000` |

### Two-Step Takeover
1. Set the persistent vendor property:
   ```bash
   adb shell setprop persist.vt.use_home_pkg app.orchard.tv
   ```
2. Disable the stock launcher:
   ```bash
   adb shell pm disable-user --user 0 com.jamdeo.tv.vod
   ```
With `com.jamdeo.tv.vod` disabled, Aerial TV (`priority=0`) becomes the highest-priority enabled activity,
winning every Home resolution at cold boot and upon remote button press. `com.hisense.widget.pro` (`priority=-150`)
remains enabled as a fail-safe fallback.

## Factory Mode M vs Mode U: The OTA Constraint

The factory engineering menu can be accessed via:
`Settings → Sound → Balance → enter 1969 on remote`

- **Factory Mode M (Design):**
  - Exposes the unadorned AOSP `com.android.settings`.
  - Keeps ADB daemon continuously listening.
  - **Fatal drawback:** Mode M suppresses and rejects all Over-The-Air (OTA) firmware upgrade broadcasts.
- **User Mode U (Production):**
  - OTA firmware updates function normally.
  - AOSP settings is protected and disabled by system policy (`enabled=2`).
  - Native settings features remain accessible via the vendor JuUI settings UI (`com.hisense.tv.globalui`).

**Recommendation:** Maintain the projector in **Mode U**. All launcher replacements, key mappings, and
debloating tweaks work reliably without requiring Mode M.

## Remote Key Remapping: Voice Key (Scancode 166 / Keycode 4381)

The physical remote control connects via Bluetooth. Its voice button sends Linux scancode 166
(`HISENSE_VOICE_NANOSIC`), recognized by Android as `KEYCODE 4381`.

In stock firmware, pressing this button triggers JuUI's voice assistant UI (`com.keylab.speech.view.vidaa`).
To remap keycode 4381 cleanly using **Key Mapper**:
1. Disable the 6 vendor voice assistant packages:
   - `com.keylab.speech.core.vidaa`
   - `com.keylab.speech.view.vidaa`
   - `com.hispeech.ai.area`
   - `com.intelligent.speech`
   - `com.hisense.msvoicetome`
   - `com.vt.mic.service`
2. Keep `com.hisense.vidaaassistant` enabled (required for APK installation permissions and package management).
3. Grant Key Mapper required permissions in Mode U:
   ```bash
   adb shell dumpsys deviceidle whitelist +io.github.sds100.keymapper
   adb shell cmd appops set io.github.sds100.keymapper RUN_IN_BACKGROUND allow
   adb shell cmd appops set io.github.sds100.keymapper SYSTEM_ALERT_WINDOW allow
   ```
Key Mapper can now bind keycode 4381 to any application or shortcut without voice assistant popups.

## Indispensable Packages

- `com.hisense.screen`: Projector focus motor, autofocus calibration, and keystone correction.
- `com.hisense.vidaaassistant`: Sideloaded package installer and permission verifier.
- `com.sohu.inputmethod.sogou.tv`: The sole on-screen keyboard included in the system image.
- `com.hisense.tv.tiletransition`: Hosts `TileAnimationService`, which the vendor Inputs panel binds to
  when switching HDMI ports. Disabling it leaves HDMI switching inert.
- `com.jamdeo.updateservice` & `com.hisense.usbupgrade`: System update engines (functional in Mode U).
