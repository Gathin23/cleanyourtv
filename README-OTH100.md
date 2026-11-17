# CleanYourTV Guide for SEI Robotics OTH-100 (Hathway Play Box)

## What Was Done
1. **Debloated 35 packages**:
   - Operator telemetry and tracking (`com.nes.datashare`, `com.nes.saleservice`, `com.seirobotics.printlog`)
   - Boot ads and background push installers (`com.nes.skywayclient`, `android.autoinstalls.config.sei`)
   - Google TV recommendations & bug reporters (`com.google.android.tvrecommendations`, `com.google.android.leanbacklauncher.recommendations`, `com.google.android.feedback`, `com.google.android.tv.bugreportsender`)
   - Voice assistant (`com.google.android.katniss`, `com.google.android.speech.pumpkin`)
   - Chromecast built-in (`com.google.android.apps.mediashell`) & Backdrop screensaver
   - System/Remote OTA updater (`com.nes.update`, `com.nes.remoteota.six`)
   - Unused system bloat & old launchers (`com.android.smspush`, `com.droidlogic.FileBrower`, `app.lumoslabs.chillhub`, `app.lumoslabs.chillio`, `com.shubh.anililitv`, etc.)
   - Stock Google TV launcher (`com.google.android.tvlauncher`)
2. **Private DNS configured**:
   - AdGuard Private DNS enabled (`dns.adguard-dns.com`) for system-wide tracker and ad blocking.
3. **Custom Apple TV Style Launcher Installed**:
   - Package: `com.gathin.tvhome` (Clean, pitch-black Apple TV 4K UI with frosted glass dock, ambient glow, and high-speed compilation).
   - Dock Apps: **SmartTube**, **Disney+ Hotstar**, **Netmirror**, and **TCL Browser**.

---

## How to Test & Navigate
- Use the TV Remote D-pad:
  - **Left / Right**: Move between apps on the dock or top bar.
  - **Down**: Scrolls to the application grid below.
  - **Up**: Climbs to the top bar (Inputs, Network status, TV Settings, and Clock).
  - **Home**: Instantly returns to the Apple TV style home screen.
  - **OK**: Launches the focused app with zero lag.

---

## How to Re-enable Any App
If you ever want an app back, run:
```powershell
adb -s 192.168.0.121:5555 shell pm enable --user 0 <package_name>
```
For example, to re-enable File Manager+:
```powershell
adb -s 192.168.0.121:5555 shell pm enable --user 0 com.alphainventor.filemanager
```
To re-enable Chromecast:
```powershell
adb -s 192.168.0.121:5555 shell pm enable --user 0 com.google.android.apps.mediashell
```

---

## How to Customize Dock Order or Wallpaper
The launcher configuration lives in `launcher/app/src/main/res/values/config.xml` or can be styled using themes in `themes/`.
To change the dock apps or add more:
1. Open `launcher/app/src/main/res/values/config.xml`.
2. Edit `<string-array name="dock_apps">` with your preferred package names.
3. Re-run `launcher/build-and-install.ps1 -TvHost 192.168.0.121 -StockLauncher none`.

---

## Full System Restore
To undo everything and restore the factory launcher and all packages:
```powershell
powershell -ExecutionPolicy Bypass -File .\restore-my-tv.ps1 -TvHost 192.168.0.121
```

---

## Closing ADB (Security Step)
When you are satisfied with the interface:
1. On your TV, navigate to **Settings → Device Preferences → Developer Options**.
2. Toggle **USB debugging** (and **Network / Wireless debugging** if present) to **OFF**.
3. Select **Revoke USB debugging authorizations**.
