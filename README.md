<p align="center">
  <img src="launcher/app/src/main/res/drawable-nodpi/banner.png" alt="Norma Launcher Banner" width="800">
</p>

# Norma Launcher — Android TV / PS5 & Apple TV 4K Edition
**Clean, ad-free, ultra-responsive TV launcher customized and maintained by [Ilham Asyari](https://github.com/ilhamasy).**  
*Forked from [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin with extensive custom features, Islamic prayer screensaver, PS5 UI sounds, dynamic dock wallpapers, in-app file browser, and system launcher locking.*

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Google%20TV-green.svg)](https://android.com/tv)
[![Package](https://img.shields.io/badge/Package-com.norma.tvlauncher-orange.svg)](launcher/app/build.gradle)
[![Release](https://img.shields.io/badge/Release-v1.1.0-brightgreen.svg)](release-1.1.0)

---

## 📸 Live Screen Captures from TV

Here are actual full 1080p screenshots captured directly from an Android TV running **Norma Launcher**:

| | |
|---|---|
| ![Home YouTube](sample%20capture/screenshot_01_home_youtube.png) | ![Netflix](sample%20capture/screenshot_02_dock_netflix.png) |
| **Home Screen (YouTube Focused)**<br>Glass dock with weekly screen time and storage stats | **Netflix (Stranger Things 1080p Backdrop)**<br>Hardware-accelerated Ken Burns zoom & drift |
| ![Prime Video](sample%20capture/screenshot_03_dock_prime_video.png) | ![Disney+](sample%20capture/screenshot_04_dock_disney.png) |
| **Prime Video (The Boys Backdrop)**<br>Crisp cross-fading cinematic show art | **Disney+ (Avengers Backdrop)**<br>Full-bleed backdrop with top and left gradient scrims |
| ![Countdown Solat](sample%20capture/screenshot_08_screensaver_solat.png) | ![Clocks Screensaver](sample%20capture/screenshot_09_screensaver_clock.png) |
| **Countdown Solat Screensaver (Default)**<br>Circular progress timer, live countdown, and prayer schedule | **Clocks Screensaver (OLED Black)**<br>Floating minimal typography clock with ambient glow |
| ![Settings Menu](sample%20capture/screenshot_11_settings_popup.png) | ![Default Launcher Setup](sample%20capture/screenshot_12_default_launcher_prompt.png) |
| **Settings Popup Menu**<br>Toggle screensavers, sound effects, dock apps, and TV settings | **First-Time Default Launcher Setup**<br>Prompts user to lock Norma Launcher as default home app |
| ![Add Apps Modal](sample%20capture/screenshot_07_add_apps_modal.png) | ![All Apps Modal](sample%20capture/screenshot_05_all_apps_modal.png) |
| **Add Apps on Docks Modal**<br>Visual indicators for apps already pinned to the dock | **All Apps Library**<br>Full-screen grid with D-pad wrap-around navigation |
| ![Edit Mode](sample%20capture/screenshot_10_edit_mode.png) | ![Quick Settings](sample%20capture/screenshot_06_quick_settings.png) |
| **Dock Reorder / Edit Mode**<br>Move and rearrange apps directly with remote D-pad | **Quick Settings Panel**<br>System status, inputs, and device information |

---

## 🌟 Key Features

* 🚀 **0ms Input Lag Glass Dock**: High-performance bottom dock inspired by Apple TV 4K and PlayStation 5 with zero animation stutter on 1GB RAM Android TVs.
* 🌙 **Dual Screensaver with 100% State Sync**:
  * **Countdown Solat (Default)**: Pitch-black OLED background, animated circular timer ring, live countdown (`HH:mm:ss`) to next prayer, 5 daily prayer times bar (Subuh, Dzuhur, Ashar, Maghrib, Isya), city tag, and anti-burn-in drift every 60s.
  * **Clocks / Photo Slideshow**: Minimal typography clock with drifting ambient glow, or family photo slideshow from `/sdcard/Screensaver/`.
  * Instant switching via Settings popup, fully synced with Android TV's system daydream component (`com.norma.tvlauncher/.ClockDream`).
* 📺 **Native Live TV & Input Switcher**: Quick-access popup to jump directly to **Live TV**, **HDMI 1**, **HDMI 2**, or **AV**.
* 🔊 **Authentic PS5 Sound Effects**: Built-in native `SoundPool` engine playing official PlayStation 5 UI sounds (navigation clicks, enter, back) with toggle in Settings.
* 🛡️ **Default Launcher Protection**:
  * Auto-lock via `cmd package set-home-activity`.
  * `BootReceiver` for auto-launch on TV restart/power-on.
  * `HomeKeyService` accessibility service to catch remote Home button presses.
  * Guided setup dialog prompt on first install.
* 🖼️ **In-App Dock Customization**: Add apps, remove apps, reorder dock tiles, or change dock wallpapers using a built-in internal storage file browser.
* 📊 **Clean App Stats**: Displays weekly screen time and storage usage for the currently focused app.

---

## 📺 Prerequisites: Enable Debugging on Your TV

Make sure your computer and TV are connected to the **same Wi-Fi network**:

1. On your TV remote, open **Settings ⚙️** → **Device Preferences** (or **System**) → **About**.
2. Scroll to **Android TV OS build** (or **Build**) and press **OK 7 times** until you see *"You are now a developer!"*.
3. Go back → open **Developer options**.
4. Turn **ON** both **USB debugging** and **Network debugging** (or **Wireless debugging**).
5. Note your TV's IP address: **Settings ⚙️** → **Network & Internet** → your Wi-Fi name (e.g. `192.168.1.68`).

---

## 🚀 3 Ways to Install

### ⚡ Method 1: Interactive Terminal Wizard (`./install.sh`) — *Recommended*
*No AI or manual configuration required. A guided CLI wizard that sets up everything automatically.*

```bash
# 1. Clone repository
git clone https://github.com/ilhamasy/cleanyourtv-ps5-theme.git
cd cleanyourtv-ps5-theme

# 2. Run the installer
./install.sh
```

The script will:
1. Detect ADB and connect to your TV automatically.
2. Let you choose which apps to pin to your dock with interactive multi-selection.
3. Install `norma-tv-launcher.apk` (or recompile from source with Gradle).
4. Lock Norma Launcher as your default home activity.
5. Set Norma screensaver as default daydream component.
6. Grant usage stats permissions and launch Norma Launcher immediately on your TV screen!

---

### 📦 Method 2: Sideload Pre-built APK (`norma-tv-launcher.apk`)

#### Via 1-Line ADB:
```bash
adb connect <YOUR_TV_IP>:5555
adb install -r norma-tv-launcher.apk
adb shell cmd package set-home-activity --user 0 com.norma.tvlauncher/.MainActivity
adb shell settings put secure screensaver_components com.norma.tvlauncher/.ClockDream
adb shell settings put secure screensaver_enabled 1
adb shell appops set com.norma.tvlauncher GET_USAGE_STATS allow
adb shell am start -n com.norma.tvlauncher/.MainActivity
```

#### Via USB Flash Drive (Zero PC Required):
1. Copy `norma-tv-launcher.apk` onto a USB flash drive.
2. Plug the USB flash drive into your TV.
3. Open any file manager on your TV (**X-plore**, **FX File Explorer**) or the **Downloader** app.
4. Select `norma-tv-launcher.apk` and click **Install**.
5. Press the **HOME** button on your remote and follow the on-screen prompt to set Norma Launcher as default!

---

### 🤖 Method 3: Single AI Prompt Customization
If you use an AI coding assistant (Claude Code, Antigravity, Cursor, ChatGPT):

```markdown
I want to replicate and customize Norma Launcher (com.norma.tvlauncher) on my Android TV.

Before making changes, please ask me:
1. "Which apps would you like pinned to your bottom dock, in order? (e.g., YouTube, Netflix, Disney+, Prime Video, Vidio, Apple TV, HBO Max, Spotify, etc.)"
2. "Do you have custom full-screen wallpapers for these apps (saved in wallpapers/), or would you like matching cinematic key art wallpapers mapped?"
3. "Would you like pure single-page mode (dock + top action buttons only) or keep the top tabs?"

Once I provide my list of apps:
1. Connect to the TV over ADB (port 5555) and verify installed packages (pm list packages).
2. Update launcher/app/src/main/res/values/config.xml with my chosen apps and column count.
3. Configure 1080p show backdrops in launcher/app/src/main/res/drawable-nodpi/wallpaper_<app>.jpg.
4. Build release APK (./gradlew assembleRelease), install over ADB, lock default launcher, configure screensaver, and launch com.norma.tvlauncher/.MainActivity.
```

---

## 🛠️ Customization Guide

### 🖼️ Adding Custom 1080p Wallpapers
1. Drop 1920×1080 JPEG images into `launcher/app/src/main/res/drawable-nodpi/`:
   * ⚠️ Use strictly lowercase letters and underscores: `wallpaper_netflix.jpg`, `wallpaper_youtube.jpg`.
2. Link them in [`MainActivity.java`](launcher/app/src/main/java/com/norma/tvlauncher/MainActivity.java):
   ```java
   DOCK_WALLPAPERS.put("com.netflix.ninja", R.drawable.wallpaper_netflix);
   ```
   *(Or rely on automatic name matching: `wallpaper_<appname>.jpg` matches package names automatically).*

### 🌙 Dual Screensaver Toggle
* Press **Settings ⚙️** on the top bar.
* Click **Screensaver: Countdown Solat** to switch to **Screensaver: Clocks** (and vice versa).
* The setting is persisted across reboots and synchronized with the system screensaver daydream.

### 🔊 PS5 UI Sound Effects
* Toggle sounds ON or OFF directly from the **Settings ⚙️** popup.
* Audio assets are stored in `sounds/` and compiled into `launcher/app/src/main/res/raw/`.

---

## 🔄 Rollback to Stock Launcher

Rolling back is 100% safe and takes under 15 seconds:
* **From TV**: Go to TV **Settings ⚙️** → **Apps** → **See all apps** → **Norma Launcher** → **Uninstall**. Press **HOME** on your remote to return to your stock launcher.
* **Via Terminal**: Run `./uninstall.sh`.
* **Via ADB**: `adb uninstall com.norma.tvlauncher`.

---

## 📜 License & Author Attribution

This software is released under the **MIT License**.

* **Base Upstream Engine**: [CleanYourTV](https://github.com/Gathin23/cleanyourtv) — Copyright (c) 2026 **Gathin**.
* **Norma Launcher / PS5 Theme & Enhancements**: Copyright (c) 2026 **[Ilham Asyari](https://github.com/ilhamasy)**.

### Notice for Forks and Modifications:
If you fork this repository or modify these changes:
1. **Maintain Author Attribution**: Keep the attribution to **Ilham Asyari** for Norma Launcher and the custom enhancements (PS5 theme, Islamic Prayer screensaver, custom dock wallpapers, file browser, default launcher system integration, and sound effects).
2. **Upstream Attribution**: Maintain copyright attribution to **Gathin** for the original CleanYourTV project.
3. Include the full copyright notices from [LICENSE](LICENSE) in all copies or substantial portions of the software.
