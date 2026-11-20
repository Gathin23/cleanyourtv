# Vivensis VX Smart (`HYR2B-Vivensis`)

- **Manufacturer:** SkyworthDigital (`HYR2B-Vivensis`, Realtek board)
- **OS:** Android TV 12 (SDK 31), build `STTB.220726.001.C2.2.4_20250613 release-keys`
- **Stock Home & Satellite Live TV:** `com.vivensis.launcher` (`com.skyworthdigital.launcher.LauncherActivity` for Home, `com.skyworthdigital.tv.TvActivity` for satellite TV channels)
- **Channel search / DTV setup:** `com.vivensis.dtvsetup`

## Critical Notes for Vivensis VX Smart

1. **Never disable `com.vivensis.launcher` or `com.vivensis.dtvsetup`:**
   `com.vivensis.launcher` contains both the stock launcher (`LauncherActivity`) and the satellite TV player (`TvActivity`), as well as the `BOOT_COMPLETED` tuner initialization receiver (`TunerInputController$IntentReceiver`). Leave it enabled and pass `STOCK_LAUNCHER=none` to `launcher/build-and-install.sh`.

2. **Holding Home across reboots (`stock_home_redirect` + `HOME_KEY_SERVICE=1`):**
   On reboot, the firmware restores `com.vivensis.launcher/com.skyworthdigital.launcher.LauncherActivity` as the default Home activity. Setting `"stock_home_redirect": "com.vivensis.launcher"`, `"home_long_press_home": true` and `"restore_key_services": true` in `examples/vivensis-vx-smart/config.json` (or `launcher/app/src/main/res/values/config.xml`) with `HOME_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1` redirects `LauncherActivity` and the remote's Home button straight to `com.gathin.tvhome`, while leaving `TvActivity` (Live TV) untouched.

3. **Adopted USB storage (`/mnt/expand/`) drops Home at boot:**
   If the box has a USB flash drive formatted as internal/adopted storage, Android installs new APKs onto `/mnt/expand/<uuid>/` by default. Because the USB drive mounts ~13 seconds after `PackageManagerService` and `AccessibilityManagerService` start at boot, Android logs `Removing dangling preferred activity: ComponentInfo{com.gathin.tvhome/com.gathin.tvhome.MainActivity}` and unbinds `HomeKeyService` on every reboot.
   - Move `com.gathin.tvhome` to internal flash storage (`/data/app`) after installing:
     ```bash
     TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm move-package com.gathin.tvhome internal'
     ```

## Apply & Undo

`debloat.txt` is the list: 11 packages (Globoplay and YouTube Music, off on this set, are commented out).

```bash
# 1. Apply the debloat list
TV_HOST=<tv ip> tools/disable.sh examples/vivensis-vx-smart/debloat.txt

# 2. Build and install the launcher (keeping com.vivensis.launcher enabled)
TV_HOST=<tv ip> STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 launcher/build-and-install.sh

# 3. Push the Vivensis config (6 columns, dock, stock_home_redirect)
TV_HOST=<tv ip> tools/theme.sh examples/vivensis-vx-smart

# 4. Undo debloat
TV_HOST=<tv ip> tools/restore.sh examples/vivensis-vx-smart/debloat.txt
```
