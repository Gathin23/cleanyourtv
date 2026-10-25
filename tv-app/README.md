# CleanYourTV — Standalone Android TV App

`tv-app` is a standalone, native Android TV application for CleanYourTV. It operates directly on compatible Android TV and Google TV devices (such as TCL Smart TV Pro) without requiring an external computer or server.

## Features
- **Remote D-Pad Navigation**: TV-first dark UI designed for 10ft TV viewing distance.
- **Local ADB Protocol Integration**: Pure Kotlin ADB protocol implementation connecting to local device ADB daemon over loopback/network.
- **Safety Engine**: Hard-locks Google Assistant, YouTube, Play Services, Settings, active launcher, and network/system UI components.
- **Baseline Backup & Restore**: Captures app state before cleanup and stores serializable backups locally in private app storage.
- **Local Diagnostic JSON Export**: Exports package state and scan summaries without transmitting data off-device.

## Developer Instructions
- Build debug APK: `cd tv-app && ./gradlew assembleDebug`
- Run unit tests: `cd tv-app && ./gradlew test`
