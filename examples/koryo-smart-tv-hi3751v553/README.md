# Koryo 55" 4K Smart TV (CVTE TP.HV553.PC821B, HiSilicon Hi3751V553)

Notes from debloating and optimizing a Koryo 55-inch 4K Smart TV built on a Guangzhou Shirui (CVTE) board with a HiSilicon "bigfish" chipset.

```
Model:              SMART_TV (Koryo 55" 4K LED, model ID CS343254)
Board:              CVTE TP.HV553.PC821B
SoC:                HiSilicon Hi3751V553 (ARM Cortex-A53 quad-core, Mali-450 GPU)
Panel:              BOE HV550QUB-N5A (3840x2160, 60 Hz)
RAM:                850 MB usable / 1 GB physical (low_ram)
Storage:            8 GB eMMC flash
Android:            7.0 (API 24, NRD90M release-keys, build 20190306_160824, display 20180808_165024)
Stock Launcher:     com.cvte.tv.launcher.fall (NebulaFallActivity)
Live TV / Inputs:   com.cvte.tv.setting/.TvSettingActivity
```

---

## Performance Summary

Before debloating, the TV experienced severe UI stuttering and background out-of-memory kills with only **67 MB to 75 MB** of free RAM.

After applying this debloat list:
- **Free RAM increased to ~200 MB to 220 MB** (a ~300% increase in available memory).
- System UI navigation and remote input response became instant.
- Official Google Play Store operates without system memory exhaustion.

---

## Optimization Commands

### 1. Full Device Backup
Before modifying package states, create a complete snapshot of all packages and settings:
```bash
adb shell pm list packages -f > backup/packages.txt
adb shell pm list packages -d > backup/disabled.txt
adb shell getprop > backup/getprop.txt
```

### 2. Apply the Debloat List
Use the repository's disable script to disable background bloatware via safe `pm disable-user --user 0` calls:
```bash
# Linux / macOS
TV_HOST=<tv ip> tools/disable.sh examples/koryo-smart-tv-hi3751v553/debloat.txt

# Windows PowerShell
.\tools\disable.ps1 -TvHost <tv ip> examples\koryo-smart-tv-hi3751v553\debloat.txt
```

### 3. Official Google Play Store
The official Google Play Store (`com.android.vending`) is pre-installed as a system package (`/system/priv-app/Google_Play_Store/Google_Play_Store.apk`). If it is not immediately visible on the home screen, launch it once to initialize Google Account login:
```bash
adb shell am start -n com.android.vending/com.google.android.finsky.tvmainactivity.TvMainActivity
```
Enter your Google credentials on-screen using the TV remote to download or update TV apps directly.

---

## Critical Do's and Don'ts

### DO NOT
- **DO NOT attempt to flash Android 8, 9, or custom ROMs**: This CVTE board uses custom HiSilicon V553 kernel drivers and LVDS/V-by-One panel parameters for the BOE `HV550QUB_N5A` T-CON. Flashing generic Android TV firmware or aftermarket ROMs will permanently brick the display controller.
- **DO NOT disable `com.cvte.tv.ucservice` or `com.cvte.tv.appstore`**: The stock launcher relies on `com.cvte.tv.ucservice` for package verification and application intent launching. Disabling either package causes the launcher to crash with an unhandled exception whenever any app tile is selected.
- **DO NOT disable `com.cvte.tv.launcher.fall` or `com.cvte.directboot.application`**: 
  - On this board, `com.android.server.policy.TVWindowManagerBase` hardcodes physical remote button dispatch (Home and Source) through CVTE's custom framework.
  - If the stock launcher is disabled, the remote's hardware Source button stops opening the HDMI/AV input switcher, and input selection becomes inaccessible.
  - Keep the stock launcher enabled; the memory savings come from disabling the background daemons, not the launcher shell itself.
- **DO NOT start or disable factory test packages** (`com.cvte.fac.menu`, `com.cvte.gaochuang.factory.at`): Starting them can flip a persistent factory burn-in mode flag in NVRAM that cannot be easily reset from ADB.
- **DO NOT use `pm uninstall`**: Always use `pm disable-user --user 0`. Uninstalled system packages cannot be restored without a factory reset or partition reflash.

### DO
- **DO keep hardware services intact**: Keep `com.cvte.tvservice` and `com.cvte.tv.setting` enabled; they control HDMI 1/2 switching, AV inputs, picture aspect ratio, and audio hardware drivers.
- **DO switch inputs from the stock home screen**: Use the on-screen Source tile or the physical remote's Source key to select HDMI inputs, and use the **Back** button to dismiss the input menu.
- **DO install ARMv7 32-bit APKs**: The HiSilicon Hi3751V553 SoC runs in 32-bit mode (`armeabi-v7a`). 64-bit APKs (`arm64-v8a`) will fail installation with `INSTALL_FAILED_NO_MATCHING_ABIS`.
