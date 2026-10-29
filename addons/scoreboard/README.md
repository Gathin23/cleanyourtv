# Streaming App Usage Scoreboard

An app usage leaderboard for Android TV that tracks launch counts and foreground watch time for streaming and media apps (YouTube, Netflix, Disney+, Prime Video, Max, Plex, Peacock, Paramount+, Spotify, Twitch, Tubi, Pluto TV, etc.).

For the [Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md), so once you answer **Show card** its tile reads "Scoreboard" with an amber glow; on any other Android TV home it functions as a standalone Leanback application.

## Features
- **Strict Streaming Filter:** Excludes background system services, settings, and package installers to focus purely on entertainment and streaming media.
- **D-Pad Remote Navigation:** Highlight any app row and press **OK / Center** on your TV remote to immediately launch that streaming service.
- **Top 10 Leaderboard:** Displays rankings with gold (🥇), silver (🥈), and bronze (🥉) medal badges along with total launch metrics.

## Permissions
- `PACKAGE_USAGE_STATS` (System AppOps permission, granted via ADB):
  ```bash
  adb shell appops set com.poppy.scoreboard GET_USAGE_STATS allow
  ```

## Build and Install
```bash
cd addons/scoreboard && ./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell appops set com.poppy.scoreboard GET_USAGE_STATS allow
```
