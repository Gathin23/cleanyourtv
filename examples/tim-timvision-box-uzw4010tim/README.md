# TIM Box (Technicolor Jade / uzw4010tim)

Technicolor's Android TV 8.0 Oreo set-top box for Telecom Italia (TIM) (`uzw4010tim`, Synaptics/Broadcom BG4CT SoC, 2 GB RAM, 8 GB Flash, integrated DVB-T/T2 tuner), debloated and running CleanYourTV with the rotating "Scenes" landscape wallpaper theme.

Everything is disabled safely at user level (`pm disable-user --user 0`); nothing is permanently removed or uninstalled from system partitions.

---

## Operating Modes: Complete Debloat vs. Hybrid DTT

On the TIM Box, `timvision.launcher` serves two distinct roles:
1. It is the stock system operator launcher (registered with `category.HOME` at `priority=2`).
2. It hosts the hardware-accelerated Live TV video player for antenna digital terrestrial channels (`com.canal.player.ui.tv.dvb.BoxPlayerV2Activity`).

CleanYourTV supports two configurations:

### Option 1: Complete Debloat (Pure Clean)
* **How it works:** `timvision.launcher` is disabled alongside all other operator packages.
* **Home key:** CleanYourTV is the sole launcher on the system. The physical remote HOME button opens CleanYourTV in 0ms with zero intercept overhead.
* **Live TV:** Use streaming IPTV apps like **OpenTV** (`app.opentv`, preconfigured in dock slot 3) to stream all Italian TV channels (Rai, Mediaset, Discovery, etc.) in HD without needing a physical antenna.

### Option 2: Hybrid DTT Setup (Keep Terrestrial Antenna TV)
* **How it works:** `timvision.launcher` remains enabled so the hardware tuner and scanned DTT channels function normally.
* **Home key:** Handled by CleanYourTV's `HomeKeyService` with a double-layer guard:
  1. **Key Interception:** The service captures `KEYCODE_HOME` on the remote input layer and consumes it before the system can dispatch the operator launcher.
  2. **8ms Fail-safe Overlay:** If the firmware attempts to display the TIM home screen, CleanYourTV projects an instantaneous pitch-black overlay before the first frame renders, brings CleanYourTV to front via `ActivityManager.moveTaskToFront`, and dismisses the overlay once CleanYourTV resumes. The user never sees the TIMVISION home screen.
* **Opening TV:** The 4th slot of the dock displays **Digitale Terrestre**. Clicking it directly launches the live TV viewer. CleanYourTV detects that the launch was intentional, allowing the player to run undisturbed.
* **Channel Tuning:** The **Sintonia DTT** tile (`com.technicolor.android.dtvscan.project`) is positioned directly below the dock in the app grid.

---

## Quick Setup

### 1. Enable ADB on the TIM Box
1. Open *Impostazioni (Settings)* → *Informazioni (About)*.
2. Scroll to *Build* and press **OK** on the remote 7 times until Developer mode is activated.
3. Go back to *Opzioni sviluppatore (Developer options)* and turn on **Debug USB**.
4. Find the box's IP address in *Impostazioni* → *Rete*.

### 2. Run Debloat
From your computer terminal (with `TV_HOST` set to your box IP):

```bash
# Connect ADB
adb connect <TIMBOX_IP>:5555

# Apply debloat packages
tools/disable.sh examples/tim-timvision-box-uzw4010tim/debloat.txt
```

*(Note: For Mode 1 Complete Debloat, ensure `timvision.launcher` is uncommented in `debloat.txt`. For Mode 2 Hybrid DTT, keep it commented out).*

### 3. Install CleanYourTV & Grant Permissions
```bash
# Install the CleanYourTV launcher
adb install -r -d launcher/app/build/outputs/apk/release/app-release.apk

# Grant required permissions for overlay, task management and settings restoration
adb shell "pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS"
adb shell "pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION"
adb shell "appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow"

# Enable HomeKeyService for Home key protection
adb shell "settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService"
adb shell "settings put secure accessibility_enabled 1"

# Set CleanYourTV as default Home
adb shell "cmd package set-home-activity com.gathin.tvhome/.MainActivity"
adb shell "am start -n com.gathin.tvhome/.MainActivity"
```

### 4. Push TIM Box Config & Scenes Theme
```bash
# Push the TIM Box preset config
adb push examples/tim-timvision-box-uzw4010tim/config.json /sdcard/Android/data/com.gathin.tvhome/files/config.json

# (Optional) Push the 14 rotating Scenes wallpapers
adb push themes/scenes/wallpapers /sdcard/Android/data/com.gathin.tvhome/files/
```

---

## Dock Layout

| Slot | App | Package | Description |
|---|---|---|---|
| 1 | **Nuvio** | `com.nuvio.tv` | Media center / streaming |
| 2 | **SmartTube** | `org.smarttube.stable` | Ad-free YouTube with SponsorBlock |
| 3 | **OpenTV** | `app.opentv` | Italian HD live streaming channels |
| 4 | **Digitale Terrestre** | `timvision.launcher` | Antenna DTT hardware player (Mode 2) |
| 5 | **NordVPN** | `com.nordvpn.android` | VPN client (appears automatically on install) |

---

## Switching Modes

### Switch to Complete Debloat (Mode 1):
```bash
adb shell pm disable-user --user 0 timvision.launcher
```

### Switch to Hybrid DTT Setup (Mode 2):
```bash
adb shell pm enable timvision.launcher
adb shell "settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1"
```

---

## Full Restore (Factory State)

Run the included restore script to re-enable all 38 debloated packages and restore the stock TIM launcher:

**Windows (PowerShell):**
```powershell
.\restore_timbox.ps1 -TvHost <TIMBOX_IP>
```

**Linux / macOS (Bash):**
```bash
TV_HOST=<TIMBOX_IP> ./restore_timbox.sh
```
