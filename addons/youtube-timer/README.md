# YouTube Timer

An offline daily time-limit add-on for YouTube on Android TV with a 4-digit PIN lock screen and time extension choices (+30 min, +1 hour, unlock today).

For the [Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md), so once you answer **Show card** its tile reads "YouTube Timer" with a red glow; clicking the tile opens the settings panel to change the PIN, cycle daily limits, or reset today's timer.

## Features
- **Daily Time Limit:** Default 60 minutes per day (customizable to 15m, 30m, 45m, 60m, 90m, or 120m).
- **Auto PIN Lock:** Automatically displays a dark glass PIN lock screen over YouTube when the daily limit is reached.
- **Remote-Friendly Keypad:** Supports both on-screen D-pad buttons and physical remote number keys (0-9).
- **Time Extensions:** Entering the correct PIN allows unlocking for 30 minutes, 1 hour, or the rest of the day.
- **PIN Protections & Cooldown:** The PIN is masked in the UI. Changing the daily limit, resetting watch time, or changing the PIN requires entering the 4-digit PIN. 5 wrong attempts trigger a 60-second cooldown with a live countdown timer.
- **30-Minute Usage Graph:** Displays an interactive half-hour breakdown of today's YouTube watch time with D-pad left/right scrubbing and exact minute readouts.
- **Privacy-First & Offline:** 100% offline with zero internet access, analytics, or background telemetry.

## Permissions
- `PACKAGE_USAGE_STATS` (System AppOps permission, granted via ADB):
  ```bash
  adb shell appops set com.gathin.addons.youtubetimer android:get_usage_stats allow
  ```
- `SYSTEM_ALERT_WINDOW` (System AppOps permission, granted via ADB):
  ```bash
  adb shell appops set com.gathin.addons.youtubetimer SYSTEM_ALERT_WINDOW allow
  ```
- `BIND_ACCESSIBILITY_SERVICE` (Local accessibility service to detect YouTube in the foreground without inspecting window content):
  ```bash
  adb shell settings put secure enabled_accessibility_services com.gathin.addons.youtubetimer/.TimerAccessibilityService
  adb shell settings put secure accessibility_enabled 1
  ```

## Build and Install
```bash
cd addons/youtube-timer && ./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell appops set com.gathin.addons.youtubetimer SYSTEM_ALERT_WINDOW allow
adb shell appops set com.gathin.addons.youtubetimer android:get_usage_stats allow
adb shell settings put secure enabled_accessibility_services com.gathin.addons.youtubetimer/.TimerAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

