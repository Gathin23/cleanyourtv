# RockTek GX1

Tested on RockTek GX1 (`UHD Google TV STB`), Google TV 14, firmware `UTT1.250214.001.C1.0.4_20251128`.

## Device profile

- **Board:** `HPR3A1325_4K` (Realtek RTD1325 platform)
- **Manufacturer:** SkyworthDigital (`SDT`)
- **RAM / Storage:** 4 GB LPDDR4 / 32 GB eMMC
- **Display output:** 4K UHD physical (3840x2160) with 1080p UI canvas (1920x1080, density 320)

## Preserved features

- **Google TV mobile remote:** `com.google.android.tv.remote.service` remains enabled. You can connect your phone using the Google TV or Google Home app.
- **Front panel LED indicator:** `com.sdt.demo.control` and `com.sdt.frontpanelledsservice` remain enabled. LED mode and color switching function normally.
- **Remote pairing:** `com.sdt.mecoolrcupair` and `com.sdt.globalkey` remain enabled.
- **Dolby Audio:** `com.dolby.android.audio.service` remains enabled.

## Home handover

The stock Google TV launcher is `com.google.android.apps.tv.launcherx`.
The first-run setup wizard is `com.google.android.tungsten.setupwraith`.
Both were disabled only after `com.gathin.tvhome` was installed and confirmed active as default Home across a reboot.

Before applying `setupwraith`, check which package owns the saved Wi-Fi network with `dumpsys wifi | grep -o 'cname=[^ ]*'`. `tools/disable.sh` and `disable.ps1` refuse to disable it while it owns that network; forget and rejoin Wi-Fi in TV Settings first.

## Remote buttons

The Settings gear button sends Android keycode 83 (`KEYCODE_NOTIFICATION`).
`HomeKeyService` handles this keycode and opens TV Settings directly. Install with `HOME_KEY_SERVICE=1` (or `-HomeKeyService` in PowerShell), then push this folder's `config.json`.
