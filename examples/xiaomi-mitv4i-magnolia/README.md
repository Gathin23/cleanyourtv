# Xiaomi MiTV4I (magnolia, Amlogic)

Verified debloat list and launcher setup for **Xiaomi MiTV4I** (codename `magnolia`, Amlogic SoC, 1 GB RAM, Android TV 9, firmware build `PI.3698 release-keys`).

---

## Device Specifications

- **Model:** `MiTV4I`
- **Product / Codename:** `magnolia`
- **SoC:** Amlogic (armeabi-v7a)
- **RAM:** ~1 GB (927 MB physical)
- **OS:** Android TV 9 (API level 28)
- **Build Fingerprint:** `Xiaomi/magnolia/magnolia:9/PI/3698:user/release-keys`
- **Stock Launcher:** `com.google.android.tvlauncher`

---

## What Was Verified

### 1. Debloat
All packages are disabled via `pm disable-user --user 0` (nothing is uninstalled, completely reversible):
- **Xiaomi & Google Telemetry:** `com.miui.tv.analytics`, `com.xiaomi.statistic`, `com.google.android.feedback`
- **PatchWall & Promos:** `com.mitv.tvhome.atv`, `com.mitv.tvhome.mitvplus`, `com.xm.webcontent`, `co.sensara.tv.mitv`, `co.sensara.tv.mitv.res`, `android.autoinstalls.config.xiaomi.amelie`, `android.autoinstalls.config.xioami.dangal`
- **Recommendations:** `com.google.android.tvrecommendations`, `com.google.android.leanbacklauncher.recommendations`
- **Background Cleaners & Services:** `com.xiaomi.mitv.tvmanager`, `com.mitv.milinkservice`, `com.mitv.tvlock`, `com.xiaomo.tv.milegal`, `com.xiaomi.android.tvsetup.partnercustomizer`, `com.google.android.tv.bugreportsender`
- **Preloaded Apps:** `com.google.android.play.games`, `com.google.android.videos`, `com.xiaomi.mimusic2`, `com.mitv.gallery`, `com.mitv.videoplayer`

### 2. Preserved System & Core Packages
- **Hardware Inputs & Live TV:** `com.droidlogic.droidlivetv` (Live TV app) and `com.droidlogic.tvinput` (HDMI 1, HDMI 2, HDMI 3, AV). Never disable these.
- **Audio & Sound Settings:** `com.droidlogic.tv.soundeffectsettings`, `com.xiaomi.android.TV.audio`, `mitv.service`.
- **Google Core:** Google Play Store, Play Services, Gboard keyboard, and Chromecast built-in (`com.google.android.apps.mediashell`).
- **Firmware Updates:** `com.xiaomi.mitv.updateservice` is commented out in `debloat.txt` so updates work; uncomment if you prefer to freeze updates.

### 3. Launcher Handover
- Built reference launcher (`com.gathin.tvhome`) with platform 34 and minSdk 26 (APK size ~537 KB, well under the 576 KB size gate).
- Made default home activity with `cmd package set-home-activity com.gathin.tvhome/.MainActivity`.
- Disabled stock `com.google.android.tvlauncher` only after confirming Home focus.
- Remote control behaviour:
  - D-pad navigation, Back, Volume, and Power work natively.
  - The PatchWall remote button defaults to Home.

---

## Memory Impact

Measured on real hardware before and after debloat + launcher handover:

| Metric | Stock | Debloated + CleanYourTV | Change |
|---|---|---|---|
| **Available RAM** | ~336 MB | **~426 MB** | **+90 MB (+27%)** |
| **Swap / ZRAM Used** | ~143 MB / 256 MB (56%) | **~40 MB / 256 MB (15%)** | **-72% swap pressure** |
| **Committed Memory** | ~24 GB virtual | **~16 GB virtual** | **-33% background strain** |

---

## How to Apply & Restore

### Apply Debloat
```bash
TV_HOST=<tv ip> tools/disable.sh examples/xiaomi-mitv4i-magnolia/debloat.txt
```
Or on Windows PowerShell:
```powershell
tools\disable.ps1 -TvHost <tv ip> examples\xiaomi-mitv4i-magnolia\debloat.txt
```

### Restore Everything
```bash
TV_HOST=<tv ip> tools/restore.sh examples/xiaomi-mitv4i-magnolia/debloat.txt
```
Or on Windows PowerShell:
```powershell
tools\restore.ps1 -TvHost <tv ip> examples\xiaomi-mitv4i-magnolia\debloat.txt
```

### Re-enable an Individual Package
```bash
tools/tv.py --tv <tv ip> "pm enable --user 0 <package_name>"
```
