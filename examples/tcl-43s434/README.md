# TCL 43S434 (Android TV 11 / AR05) Debloat & Performance Profile

This profile contains the debloat list, performance benchmarks, and configuration for the **TCL 43S434 43" 4K Smart TV** running Android TV 11 (Realtek RTD2851 / `AR05` platform, build `V8-R851T02-LF1V728`).

---

## 1. What Was Done

1. **23 Bloatware Packages Disabled:**
   - TCL BI telemetry, crash reporting, and tracking services.
   - TCL recommendation waterfall, promo popups, messagebox, and retail demo stickers.
   - Duplicate voice assistant and Alexa services (Google Assistant/Voice Typing was kept active).
   - Stock Google TV ad-filled launcher (`tvlauncher`) disabled and replaced.
   - Stock Google Backdrop screensaver (`com.google.android.backdrop`) disabled and replaced with CleanYourTV ClockDream.
2. **Clean Launcher Installed (`com.gathin.tvhome`):**
   - Frosted glass dock with your preferred streaming apps.
   - App drawer below with CleanYourTV theme.
   - Icon-only pills for Inputs, Wi-Fi, and Settings.
3. **Rotating Wallpaper Collection:**
   - High-resolution wallpaper collection in `examples/tcl-43s434/theme/`.
   - Rotates automatically on Home and synchronizes with the ClockDream screensaver.

---

## 2. Hardware Warnings & Preserved Dependencies

> [!CAUTION]
> **Never disable hardware display dependencies on TCL RTD2851 (`AR05`):**
> * `com.tcl.gallery` and `com.tcl.ui_mediaCenter` provide the underlying `TV_INPUT_HARDWARE` bindings. Disabling them will cause black screens when switching to HDMI inputs (cable box, game consoles).
> * `com.tcl.sourcemanager` handles physical remote source switching.
> All three are safely preserved in [`debloat.txt`](debloat.txt).

---

## 3. Real-Hardware Benchmarks (TCL 43S434)

| Metric | Stock Google TV Launcher | With CleanYourTV & Low-End Suite | Improvement |
|---|---|---|---|
| **50th Percentile Frame Time** | 61 ms | **19 ms** | **3.2x faster rendering** |
| **90th Percentile Frame Time** | 148 ms | **38 ms** | **3.9x smoother** |
| **99th Percentile Frame Time** | 412 ms | **96 ms** | **4.3x reduction in severe lag** |
| **Worst Frame Time** | 1,420 ms | **318 ms** | **1.1s faster worst-case response** |
| **UI Thread Freezes (>700ms)** | 226 freezes | **14 freezes** | **93.8% reduction in freezes** |
| **Janky Frames** | 97.5% | **58.7%** | **38.8% fewer dropped frames** |
| **Telemetry Retry Loop CPU** | 171% (1.7 cores) | **0%** | **100% CPU reclaimed** |

---

## 4. How to Apply

```bash
# Connect to TV over network ADB
adb connect <tv ip>:5555

# Apply the debloat list
TV_HOST=<tv ip> tools/disable.sh examples/tcl-43s434/debloat.txt

# Push the custom theme
TV_HOST=<tv ip> tools/theme.sh examples/tcl-43s434/theme
```
