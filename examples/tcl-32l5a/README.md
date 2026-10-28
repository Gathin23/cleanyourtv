# TCL 32L5A (Android TV 11)

**Device Model:** TCL 32L5A  
**Platform:** C06 / C06_2K_GB  
**OS Version:** Android TV 11 (SDK 30, build AR2101)  
**RAM:** 1 GB  

---

## 1. Overview

This directory contains the debloat configuration and documentation for the **TCL 32L5A** Android TV set. The TV has been debloated of manufacturer telemetry, background bloat, and ad services, and the stock launcher (`com.google.android.tvlauncher`) has been replaced with a clean **Apple TV 4K-style native launcher**.

---

## 2. Debloat Details

### Packages Disabled
- `com.tcl.usercenter` — TCL account and sign-in service
- `com.tcl.guard` — TGuard background cleaner
- `com.tcl.partnercustomizer` — Promo rows on stock launcher
- `com.tcl.waterfall.overseas` — TCL web-video feed / TCL Channel
- `com.tcl.esticker` — Store demo sticker app
- `com.tcl.copydatatotv` — Copy data promo utility
- `com.tcl.t_solo` — TCL promotional service
- `com.internet.tvbrowser` — Preloaded browser
- `com.tcl.browser` — TCL Browser
- `com.google.android.feedback` — User feedback collector
- `com.google.android.play.games` — Google Play Games TV service
- `com.google.android.tvrecommendations` — Google recommendations/ads feed

### Packages Kept Intact
- **Live TV & Hardware Inputs:** `com.tcl.tv`, `com.tcl.tvinput` (HDMI 1, HDMI 2, AV, Tuner)
- **Remote & Bluetooth:** `com.tcl.tcl_bt_rcu_service`, `com.tcl.autopair`
- **Settings & Plumbing:** `com.tcl.settings`, `com.android.tv.settings`, `com.tcl.system.server`, `com.tcl.dashboard`
- **Keyboards & Input Methods:** `com.tcl.inputmethod.international`, `com.google.android.inputmethod.latin`
- **Core Google Services:** Google Play Store (`com.android.vending`), Play Services (`com.google.android.gms`), Google Assistant (`com.google.android.katniss`), Chromecast (`com.google.android.apps.mediashell`)
- **Streaming Apps:** Netflix, YouTube, YouTube Music, Prime Video, Spotify, Crunchyroll, MovieBox TV, NetMirror TV, Kick, Tamasha TV, GES Live TV

---

## 3. How to Re-enable Any Disabled App

### Re-enable a Single Package
Over ADB or `tools/tv.py`:
```powershell
python tools/tv.py "pm enable com.tcl.usercenter"
```

### Restore All Debloated Packages
Run the restore script:
```powershell
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost <tv ip> examples\tcl-32l5a\debloat.txt
```

---

## 4. How to Re-build and Re-install the Launcher

To re-build and re-install the launcher with updated settings or key mappings:
```powershell
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost <tv ip> -InputKeyService -HomeKeyService
```

If Gradle does not find a JDK 17, point `JAVA_HOME` at one first:
```powershell
$env:JAVA_HOME='<path to JDK 17>'
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost <tv ip> -InputKeyService -HomeKeyService
```

---

## 5. How to Change Dock Order or App Pinnings

To customize the 4 apps pinned to the dock at the bottom of the screen:

1. Open `launcher/app/src/main/res/values/config.xml`.
2. Edit the `dock_apps` array:
   ```xml
   <string-array name="dock_apps">
       <item>com.netflix.ninja</item>
       <item>com.google.android.youtube.tv</item>
       <item>com.crunchyroll.crunchyroid</item>
       <item>com.spotify.tv.android</item>
   </string-array>
   ```
3. Rebuild and reinstall with the command in section 4 to deploy the change.

Alternatively, you can place a `config.json` on the TV under `/sdcard/Android/data/com.gathin.tvhome/files/config.json`:
```json
{
  "dock_apps": [
    "com.netflix.ninja",
    "com.google.android.youtube.tv",
    "com.crunchyroll.crunchyroid",
    "com.spotify.tv.android"
  ]
}
```

---

## 6. How to Change or Set Wallpaper

To set a custom wallpaper background:

1. Place your JPEG image named `wallpaper.jpg` in `/sdcard/Android/data/com.gathin.tvhome/files/`.
2. Push a `config.json` file to the same folder containing:
   ```json
   {
     "wallpaper": true,
     "wallpaper_dim": 30
   }
   ```
3. Restart the launcher or press Home on the remote.

---

## 7. Optional Ad-blocking Private DNS Undo

If Private DNS was configured to AdGuard (`dns.adguard-dns.com`), you can revert it to default automatic mode at any time with:
```powershell
python tools/tv.py "settings put global private_dns_mode opportunistic"
```

---

## 8. Closing Network ADB and Securing the TV

For security on plaintext port 5555, close network ADB once complete:

1. On the TV, go to **Settings → Device Preferences → Developer options**.
2. Turn **USB debugging** OFF.
3. Turn **Network ADB debugging** OFF.
4. Select **Revoke USB debugging authorizations**.
5. Reboot the TV.
