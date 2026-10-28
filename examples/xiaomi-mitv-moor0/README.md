# Xiaomi MiTV-MOOR0 (`venom`, Google TV / Android 11)

Tested on a **Xiaomi MiTV-MOOR0** (`ro.product.device = venom`, MediaTek, 1.8 GB RAM, 1920x1080 @ 320 dpi), Android 11 (SDK 30), firmware `RTM5.220609.003.1246`.

## Apply and undo

From the repository root:

```sh
# 1. Disable bloatware (including setupwraith once Wi-Fi ownership is verified)
TV_HOST=<tv ip> tools/disable.sh examples/xiaomi-mitv-moor0/debloat.txt

# 2. Build and install the launcher, and disable launcherx
TV_HOST=<tv ip> launcher/build-and-install.sh

# 3. Apply the 5-column dock and Live TV configuration
TV_HOST=<tv ip> tools/theme.sh examples/xiaomi-mitv-moor0
```

On Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost <tv ip> examples\xiaomi-mitv-moor0\debloat.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost <tv ip>
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost <tv ip> examples\xiaomi-mitv-moor0
```

To restore everything back to the original state:

```sh
TV_HOST=<tv ip> tools/restore.sh examples/xiaomi-mitv-moor0/debloat.txt com.google.android.apps.tv.launcherx
```

To re-enable a single app at any time:

```sh
TV_HOST=<tv ip> tools/tv.py 'pm enable --user 0 <package>'
```

## Notes on this firmware

- **HOME claimants:** Four activities claim `android.intent.category.HOME` out of the box:
  1. `com.google.android.apps.tv.launcherx/.home.HomeActivity` (`priority=2`)
  2. `com.google.android.tungsten.setupwraith/.RecoveryActivity` (`priority=1`)
  3. `com.duokan.factorytest/.FactoryLauncher` (`priority=0`)
  4. `com.android.tv.settings/.system.FallbackHome` (`priority=-1000`)

  Both `com.duokan.factorytest` and `com.google.android.tungsten.setupwraith` are included in [`debloat.txt`](debloat.txt) so that `com.gathin.tvhome/.MainActivity` owns Home cleanly. On this unit all saved Wi-Fi networks belonged to `cname=android.uid.system:1000` (`dumpsys wifi | grep -o 'cname=[^ ]*'`); `tools/disable.sh` checks this automatically before disabling `setupwraith`.
- **Inputs & Live TV:** `com.mitv.livetv` owns `.input.SelectInputActivity` and `.tv.MainActivity`. [`config.json`](config.json) sets `"live_tv_package": "com.mitv.livetv"` and hides the duplicate tile from the grid (`"hidden_apps": ["com.mitv.livetv"]`). The launcher's **Inputs** pill lists HDMI 1–3 (with CEC/custom labels and connection state), AV, and Live TV via `com.mediatek.tvinput` and `com.mediatek.tv.service`.
- **Dock & wallpaper:** Edit [`config.json`](config.json) (`columns` and `dock_apps`), or place a `wallpaper.jpg` in this folder, and re-run `tools/theme.sh examples/xiaomi-mitv-moor0` (or hold OK on the Settings pill on the TV to reorder tiles or pick a wallpaper on-device).
- **Remote buttons:** As on [`xiaomi-mitv-moor2`](../xiaomi-mitv-moor2/debloat.txt), once `launcherx` is disabled, if the physical Settings (`KEYCODE_SETTINGS`, 176), Input (`KEYCODE_TV_INPUT`, 178) or YouTube (`KEYCODE_BUTTON_3`, 190) buttons on the remote stop responding, pass `HOME_KEY_SERVICE=1 INPUT_KEY_SERVICE=1` (PowerShell: `-HomeKeyService -InputKeyService`) to `launcher/build-and-install`. Note that binding a key-filtering accessibility service on this MediaTek Google TV 11 platform makes a held Volume button step once per press instead of ramping continuously.
