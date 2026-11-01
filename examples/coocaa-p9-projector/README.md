# Coocaa P9 Projector (MediaTek mt5862, Android 9 / Coocaa OS)

Notes for the Coocaa P9 (酷开 P9) LCD smart projector running Coocaa OS on Android 9 (MediaTek mt5862 platform).
Verified across cold reboots with 16 packages disabled.

## Device Information

| Field | Value |
|---|---|
| Model | `Projector` (Retail: Coocaa P9 / 酷开 P9) |
| Brand / Manufacturer | MediaTek / Coocaa (Skyworth) |
| SoC / Board | MediaTek `mt5862` (`mt5862_bga_1g`) |
| Android / SDK | `9` / `28` (PPR2.180905.006.A1 dev-keys) |
| Userspace ABI | `armeabi-v7a` (32-bit) |
| SELinux | `permissive` |
| RAM | 1 GB (~823 MB usable) |

## How to Enable ADB (Crucial Step)

The secret sequence to reveal Developer Options on Coocaa P9 is distinct from typical Skyworth TVs:

1. Open **设置 (Settings)** ➔ **通用 (General)**.
2. Move cursor to focus on **关于本机 (About this TV)**.
3. While focused on 关于本机, press **LEFT** on the remote **7 times** (`LEFT LEFT LEFT LEFT LEFT LEFT LEFT`).
4. Return to the 通用 (General) menu: **开发者选项 (Developer options)** will now appear below 关于本机.
5. Enter 开发者选项, turn on **USB 调试 (USB debugging)**.
6. Connect via ADB over port 5555 (`adb connect <ip>:5555`) and accept the authorization prompt on the projection screen.

## Critical Warnings

- **NEVER disable `com.tianci.movieplatform`:** On Coocaa OS, the boot chain is hardcoded to this launcher package. Disabling it will result in a boot hang on the COOCAA logo. Keep it enabled.
- **NEVER disable `com.coocaa.home.plug.projection`:** This is the projector-specific hardware service responsible for autofocus, electric focus, and keystone correction.

## Skip Boot Video Ads

The 15-second boot ad can be skipped permanently without root:

```bash
tools/tv.py 'setprop persist.sys.bootvideo.time 1'
```

*Stock value before modification was unset / empty (system defaults to 15s video at `/vendor/stream/bootvideo.mp4`).*

## Launcher Handover & Undo

The reference launcher works smoothly when built with application ID `com.coocaa.tvhome`:

1. Build the launcher:
   ```bash
   (cd launcher && ./gradlew assembleRelease -PlauncherApplicationId=com.coocaa.tvhome)
   ```
2. Install the APK over ADB.
3. Switch default homepage properties and Home activity:
   ```bash
   tools/tv.py 'setprop persist.service.homepage.pkg com.coocaa.tvhome'
   tools/tv.py 'setprop persist.service.homepage.cls com.gathin.tvhome.MainActivity'
   tools/tv.py 'cmd package set-home-activity com.coocaa.tvhome/com.gathin.tvhome.MainActivity'
   ```
4. Remote Home key will now cleanly open CleanYourTV launcher.

### Undo Launcher Handover

To restore the stock Coocaa launcher:

```bash
tools/tv.py 'setprop persist.service.homepage.pkg com.tianci.movieplatform'
tools/tv.py 'setprop persist.service.homepage.cls com.coocaa.homepage.vast.projector.ProjectorHomeActivity'
tools/tv.py 'cmd package set-home-activity com.tianci.movieplatform/com.coocaa.homepage.vast.projector.ProjectorHomeActivity'
```

*Note: Unlike the Realtek board in `skyworth-8r40-k5c`, modifying `persist.service.homepage.*` on this MediaTek mt5862 board did NOT stop `adbd` from starting after a cold reboot.*
