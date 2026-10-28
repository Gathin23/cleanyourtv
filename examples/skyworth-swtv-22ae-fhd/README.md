# Skyworth SWTV-22AE-FHD (umeda)

Tested setup for this Skyworth Google TV model.

## Device info
- **Model:** SWTV-22AE-FHD
- **Codename:** umeda
- **Manufacturer:** Skyworth
- **SoC/Platform:** MediaTek
- **OS:** Google TV / Android TV 11 (SDK 30)
- **Firmware:** RTM7.230903.136
- **Network ADB:** Port 5555

This device info is a **second unit** of this model: the list in this folder (`debloat.txt`) was
written on Android 14 (SDK 34), firmware `UKNV.260211.001`, and this Android 11 unit applied the same
list. An entry this build does not ship reads the same as one it does, so run `pm list packages` and
comment out what your set does not have before applying it.

## Stock launcher
- **Launcher:** `com.google.android.apps.tv.launcherx` (`.home.HomeActivity`)
- When restoring, pass it to `tools/restore.sh` as the second argument; when building/installing, pass `STOCK_LAUNCHER=com.google.android.apps.tv.launcherx` to `launcher/build-and-install.sh`.

## Live TV
- **Live TV app/package:** `com.mediatek.wwtv.tvcenter`  
  Set this as `live_tv_package` and hide it in launcher config.xml if desired.

## Notes on debloat
- **Keep these (TV needs them):**
  - `com.smartdevice.global.service` – handles SkwGlobalKeyReceiver, OneKeyMenuDialog, remote keys
  - `com.skyworth.backligthadjust` – backlight control
  - `com.smartdevice.bleconnectservice` – SkyAutoPair Bluetooth remote pairing
  - `com.mediatek.*` – Live TV, tuners, video player services
  - `com.google.android.katniss` – Google Assistant (kept per user choice)
  - `com.google.android.apps.mediashell` – Chromecast built-in (kept per user choice)
  - `com.google.android.inputmethod.latin` – Gboard on-screen keyboard
- **Setup wizard:** `com.google.android.tungsten.setupwraith` is disabled in the provided list. Disabling it frees Home on Google TV but may break Chromecast built-in/casting in some cases. If you need casting, re-enable it. Also, if your Wi-Fi was owned by setupwraith, you may need to forget/rejoin the network first.
- **Factory/demo/test:** `com.coocaa.demonow.lightining`, `fusion.android.tv.demo`, `com.smartdevice.factory`, `mediatek.factorymenu.ui` disabled.

## Apply / Restore
From repo root (on same Wi-Fi as TV):

```bash
# Probe/verify
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --probe

# Apply debloat (backs up to ~/.cleanyourtv/)
TV_HOST=<tv ip> tools/disable.sh examples/skyworth-swtv-22ae-fhd/debloat.txt

# Restore to original (uses backup + stock launcher)
TV_HOST=<tv ip> tools/restore.sh examples/skyworth-swtv-22ae-fhd/debloat.txt com.google.android.apps.tv.launcherx

# Build/install CleanYourTV launcher and set as Home
STOCK_LAUNCHER=com.google.android.apps.tv.launcherx TV_HOST=<tv ip> launcher/build-and-install.sh
```

## Verified
- Debloat applied cleanly
- CleanYourTV launcher (`com.gathin.tvhome`) installed and set as default HOME
- Stock `com.google.android.apps.tv.launcherx` disabled
