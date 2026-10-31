# Acer AR32HDIGU2841AT 32" HD (Google TV 14)

Tested on an Acer AR32HDIGU2841AT 32" HD Google TV (`ro.product.model=R3G`, `ro.product.device=R3_GTV`, manufacturer MOKA) powered by the Realtek RTD2841A board (`rtd2841a`), 1.5 GB RAM, running Google TV 14 on build `UKR1.260612` (incremental `2624`).

## What this folder contains

| File | What it is |
|---|---|
| `debloat.txt` | The 28 packages that were disabled, grouped and commented |
| `config.json` | Dock configuration with 5 streaming apps, frosted glass dock and glow |
| `tv.json` | Entry for the CleanYourTV supported TV models index |

## How to Apply

```sh
TV_HOST=<tv ip> tools/disable.sh examples/acer-ar32hdigu2841at/debloat.txt
```
On Windows:
```powershell
tools\disable.ps1 -TvHost <tv ip> examples\acer-ar32hdigu2841at\debloat.txt
```

## How to Restore

```sh
TV_HOST=<tv ip> tools/restore.sh examples/acer-ar32hdigu2841at/debloat.txt com.google.android.apps.tv.launcherx
```
On Windows:
```powershell
tools\restore.ps1 -TvHost <tv ip> examples\acer-ar32hdigu2841at\debloat.txt -Stock com.google.android.apps.tv.launcherx
```

## Critical Notes for this TV

1. **Keep `launcherx` and `setupwraith` enabled:**
   - `com.google.android.tungsten.setupwraith` owns the saved Wi-Fi credentials. Disabling it drops Wi-Fi and ADB.
   - On this MOKA firmware, disabling `com.google.android.apps.tv.launcherx` causes `setupwraith.RecoveryActivity` to loop on boot, locking out the user interface (see `docs/devices/acer-r4g.md`).
   - The launcher is set as default and takes the Home button safely via `HomeKeyService` with `stock_home_redirect=com.google.android.apps.tv.launcherx`.

2. **Cold Boot Package State:**
   - Realtek / MOKA's `FallbackHome` runs `SetPmodServiceJob` on cold boot, which re-enables 21 packages unless the factory `P_MODE` flag is set.
   - 7 packages remain permanently disabled across cold boots: `com.apps.usbmediaplayer`, `com.apps.mk.wd`, `android.autoinstalls.config.scbc.device`, `com.android.tv.feedbackconsent`, `com.realtek.leanbacklauncher.partnercustomizer`, `com.mxtech.videoplayer.television`, and `com.apps.wifihotspot`.
