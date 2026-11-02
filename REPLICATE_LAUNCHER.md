# Replicate Norma Launcher (Android TV — PS5 & Apple TV 4K Style)

This guide allows anyone who clones this repository to replicate and install **Norma Launcher**, a sleek, ultra-fast, ad-free Android TV / Google TV launcher featuring PlayStation 5 and Apple TV 4K inspired aesthetics, custom bottom dock apps, dynamic 1080p backdrops, Islamic Prayer countdown screensaver, native TV input switcher, and screen time statistics.

**Original Upstream Project**: CleanYourTV by Gathin  
**Norma Launcher Author & Enhancements**: **Ilham Asyari**  
**Package Name**: `com.norma.tvlauncher`  
**License**: MIT License (Attribution required for forks and modifications)

---

## ⚡ Option 1: Interactive Terminal Setup (`./install.sh`) — *Zero AI Required*

You don't need any AI assistant to install and run this launcher. Just open your Terminal (macOS/Linux) or Command Prompt / Git Bash (Windows) in the repository folder and run:

```bash
./install.sh
```

The script will interactively guide you:
1. Automatically discovers ADB in your PATH or Android SDK.
2. Detects connected TVs or prompts for your TV's Wi-Fi IP address (e.g., `192.168.1.150`).
3. **Interactive Multi-Selection Dock Wizard**: Choose which apps to pin to your bottom dock. The layout and column count adjust automatically.
4. Installs the pre-built `norma-tv-launcher.apk` (or compiles release build from source via Gradle).
5. Automatically locks Norma Launcher as your default home activity (`set-home-activity`).
6. Configures the default screensaver to Norma Screensaver (`ClockDream`) and enables remote Home key intercept (`HomeKeyService`).
7. Grants `GET_USAGE_STATS` permission for weekly screen time and storage usage stats.
8. Launches Norma Launcher immediately on your TV!

---

## 📦 Option 2: Pre-built APK (`norma-tv-launcher.apk`) — Single Command or USB

If you already have the APK or want to sideload without compiling:

### Via ADB:
```bash
# 1. Connect to your TV over Wi-Fi
adb connect <YOUR_TV_IP>:5555

# 2. Install Norma Launcher APK
adb install -r norma-tv-launcher.apk

# 3. Grant usage stats & system home defaults
adb shell appops set com.norma.tvlauncher GET_USAGE_STATS allow
adb shell cmd package set-home-activity --user 0 com.norma.tvlauncher/.MainActivity
adb shell settings put secure screensaver_components com.norma.tvlauncher/.ClockDream
adb shell settings put secure screensaver_enabled 1

# 4. Launch Norma Launcher
adb shell am start -n com.norma.tvlauncher/.MainActivity
```

### Via USB / TV File Manager (Zero PC Required):
1. Copy `norma-tv-launcher.apk` to a USB flash drive.
2. Plug the USB drive into your Android TV / Google TV.
3. Open any TV file manager (**X-plore File Manager**, **FX File Explorer**) or the **Downloader** app to install the APK.
4. Press the **HOME** button on your remote. A dialog will prompt you to set Norma Launcher as your default home screen.

---

## 🤖 Option 3: Replicate with a Single AI Prompt (For Custom Dock Apps)

Copy and paste the prompt below into your AI coding assistant (Claude Code, Antigravity, Cursor, ChatGPT) while connected to the same Wi-Fi network as your TV:

```markdown
I want to replicate and customize Norma Launcher (com.norma.tvlauncher) on my Android TV.

Before making changes, please ask me:
1. "Which apps would you like pinned to your bottom dock, in order? (e.g., YouTube, Netflix, Disney+, Prime Video, Vidio, Apple TV, HBO Max, Spotify, etc.)"
2. "Do you have custom full-screen wallpapers for these apps (saved in wallpapers/), or would you like matching cinematic key art wallpapers mapped?"
3. "Would you like pure single-page mode (dock + top action buttons only) or keep the top tabs?"

Once I provide my list of apps:
1. Connect to the TV over ADB (port 5555) and verify installed packages (`pm list packages`).
2. Update `launcher/app/src/main/res/values/config.xml`:
   - Set `<string-array name="dock_apps">` with the exact package names in order.
   - Set `<integer name="columns">` to match the number of dock apps.
3. Configure the visual assets for each dock app:
   - Unfocused icon: Clean borderless logo in `launcher/app/src/main/assets/icons/<name>-small-icons.png` (50×50dp).
   - Focused banner: Leanback banner (92×52dp).
   - Cinematic wallpaper: Full 1080p backdrop in `launcher/app/src/main/res/drawable-nodpi/wallpaper_<pkg_or_name>.jpg` that smoothly cross-fades when focused.
4. Maintain performance optimizations:
   - Instant dock switching (0ms input lag, native SoundPool audio feedback).
   - Bottom-right stats card displaying "Weekly Screen Time" and "Storage Usage".
   - Deterministic D-pad navigation: UP jumps to top action pills (Live TV / Inputs, Wi-Fi, Settings), and DOWN returns to dock.
5. Build the release APK (`./gradlew assembleRelease`), install over ADB, grant `GET_USAGE_STATS`, configure default launcher, and launch `com.norma.tvlauncher/.MainActivity`.
6. Take a screenshot from the TV (`screencap -p`) and pull it to verify pixel-perfection.
```

---

## 🛠 Option 4: Step-by-Step Manual Customization

### 1. Configure Your Dock Apps
Open [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml) and edit `dock_apps` and `columns`:

```xml
<string-array name="dock_apps">
    <item>com.google.android.youtube.tv</item>
    <item>com.apple.atve.androidtv.appletv</item>
    <item>com.netflix.ninja</item>
    <item>com.wbd.stream</item>
    <item>com.amazon.amazonvideo.livingroom</item>
    <item>com.disney.disneyplus</item>
    <item>com.vidio.android.tv</item>
</string-array>

<!-- Number of columns matching the number of dock items -->
<integer name="columns">7</integer>
```

### 2. Add App Assets & Wallpapers
For each app on your dock:
1. **Unfocused Small Icon** (Optional):
   Place a transparent or dark square logo (50×50dp) in `launcher/app/src/main/assets/icons/<app>-small-icons.png`. The launcher automatically applies 14dp rounded corners.
2. **Focused Show Wallpaper**:
   Place a high-resolution 1920×1080 JPEG in:
   - `launcher/app/src/main/res/drawable-nodpi/wallpaper_<name>.jpg`
   - and `wallpapers/<name>.jpg`
   *(⚠️ Filename must be strictly lowercase letters, numbers, and underscores only. No dashes, no spaces, e.g. `wallpaper_netflix.jpg`).*

3. **Map in `MainActivity.java`**:
   Link the package to the drawable ID:
   ```java
   DOCK_WALLPAPERS.put("com.netflix.ninja", R.drawable.wallpaper_netflix);
   ```
   *(Or rely on the launcher's automatic dynamic fallback for files matching `wallpaper_<name>.jpg`).*

### 3. Build & Install
```bash
# 1. Connect to TV
adb connect 192.168.1.xxx:5555

# 2. Build release APK
cd launcher
JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleRelease

# 3. Install & Lock as default
adb install -r app/build/outputs/apk/release/norma-tv-launcher.apk
adb shell cmd package set-home-activity --user 0 com.norma.tvlauncher/.MainActivity
adb shell settings put secure screensaver_components com.norma.tvlauncher/.ClockDream
adb shell settings put secure screensaver_enabled 1
adb shell appops set com.norma.tvlauncher GET_USAGE_STATS allow
adb shell am start -n com.norma.tvlauncher/.MainActivity
```

---

## 🌟 Key Features in Norma Launcher

- **PS5 & Apple TV 4K Style Glass Dock**: Sleek translucent glass highlight that snaps instantly between apps with zero frame drops.
- **Cinematic Show Art**: Full-bleed 1080p show backdrops with hardware-accelerated Ken Burns animation when focusing on streaming services.
- **Dual Screensaver System**:
  - **Countdown Solat (Default)**: OLED pitch-black screen with large circular timer, real-time countdown to next prayer, 5 daily prayer times schedule, location tag, and anti-burn-in drift.
  - **Clocks / Slideshow**: Floating minimalist clock with animated ambient glow or personal photo slideshow from `/sdcard/Screensaver/`.
  - Seamless toggle directly from the Settings popup menu with 100% Android TV daydream state synchronization.
- **Live TV & Input Switcher**: Quick-access popup to switch directly to **Live TV**, **HDMI 1**, **HDMI 2**, and **AV**.
- **Interactive In-App Dock Management**: Add apps, remove apps, reorder dock tiles, or change dock wallpapers directly using the built-in internal storage file browser.
- **Default Launcher Safety**: Automatic boot-up receiver (`BootReceiver`), remote home button intercept (`HomeKeyService`), and first-install setup dialog modal.
- **Console Audio Engine**: Native low-latency `SoundPool` playing authentic PlayStation 5 navigation clicks, enter, and back sounds (toggleable in Settings).
- **Clean Bottom-Right Stats**: Displays Weekly Screen Time and Storage Usage for the focused app.

---

## 📜 License & Fork Terms

This project is licensed under the **MIT License**.

- **Base upstream project**: [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by **Gathin**.
- **Norma Launcher modifications & features**: Copyright (c) 2026 **Ilham Asyari**.

If you fork this repository or modify these changes:
1. Maintain attribution to **Gathin** for the base CleanYourTV engine.
2. Maintain attribution to **Ilham Asyari** for Norma Launcher, the PS5 theme adaptations, Islamic Prayer screensaver, custom dock wallpapers, file browser, default launcher integration, and sound effects.
3. Keep the copyright notices in [LICENSE](LICENSE) intact.
