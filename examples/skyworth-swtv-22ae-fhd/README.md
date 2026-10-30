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

## Third unit: sold as Spanio (2K NSTV)
Same firmware (`UKNV.260211.001`, Android 14) and `ro.product.odm.model` `SWTV-22AE-FHD`; `ro.product.model` reads
`2K NSTV` and the brand string is the maker's own. Tested on 2026-10-31, one long session, with a reboot after the
last change; not yet several days. Everything in `debloat.txt` was applied except three lines, see below.

- **Remote app button:** with `com.smartdevice.dailyshortcut`, `com.smartdevice.dailymanage` and
  `com.smartdevice.myapp` disabled the button did nothing. The kernel logs it as `KEY_EDIT`, Skyworth's
  `SkwGlobalKeyReceiver` receives it, and the owner re-enabled all three; the button then opened Skyworth's panel.
  Those three are commented out in the list for that reason.
- **Remote gear button:** it sends Android's `KEYCODE_NOTIFICATION` (83), which only the stock Google TV launcher
  answered. With the stock launcher disabled it did nothing. The launcher's `HomeKeyService` with
  `settings_key_keycodes` set to `[83]` opened Settings, but any bound key-filtering service made a held volume
  key step once per press (also with the helper in its own process, and with `consume_volume_keys`). The owner kept
  smooth volume and left the gear button unmapped.
- **Casting:** not re-tested after setupwraith was disabled.
- **Private DNS** (`dns.adguard-dns.com`) worked: YouTube still loaded.

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
