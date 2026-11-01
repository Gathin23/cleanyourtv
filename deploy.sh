#!/bin/bash
# Copyright (c) 2026 Ilham
# All Rights Reserved.
# This from project cleanyourtv by Gathin32 custom by Ilham Asyari
# Auto-detect connected ADB device or fallback to known TV IPs
CONNECTED_DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')

if [ -z "$CONNECTED_DEVICE" ]; then
    TV_IP="${1:-192.168.1.28:5555}"
    echo "Connecting to TV at $TV_IP..."
    adb connect "$TV_IP"
    CONNECTED_DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
fi

APK="launcher/app/build/outputs/apk/release/app-deblouting-ps5.apk"
if [ ! -f "$APK" ]; then
    APK="launcher/app/build/outputs/apk/debug/app-deblouting-ps5.apk"
fi
if [ ! -f "$APK" ]; then
    APK="app-deblouting-ps5.apk"
fi

if [ -n "$CONNECTED_DEVICE" ]; then
    echo "Target device: $CONNECTED_DEVICE"
    echo "Installing $APK..."
    adb -s "$CONNECTED_DEVICE" install -r "$APK"
    adb -s "$CONNECTED_DEVICE" shell appops set com.norma.tvlauncher GET_USAGE_STATS allow
    adb -s "$CONNECTED_DEVICE" shell settings put system sound_effects_enabled 0 2>/dev/null || true
    
    # Automatically lock Norma Launcher as default system home activity
    echo "Setting Norma Launcher as default home launcher..."
    adb -s "$CONNECTED_DEVICE" shell cmd package set-home-activity --user 0 com.norma.tvlauncher/.MainActivity 2>/dev/null || true
    
    # Configure Norma Launcher as default screensaver
    echo "Setting Norma Launcher as default screensaver..."
    adb -s "$CONNECTED_DEVICE" shell settings put secure screensaver_components com.norma.tvlauncher/.ClockDream 2>/dev/null || true
    adb -s "$CONNECTED_DEVICE" shell settings put secure screensaver_enabled 1 2>/dev/null || true

    # Enable accessibility service for Home Key remote interception
    echo "Configuring remote Home key handler..."
    adb -s "$CONNECTED_DEVICE" shell settings put secure enabled_accessibility_services com.norma.tvlauncher/.HomeKeyService 2>/dev/null || true
    adb -s "$CONNECTED_DEVICE" shell settings put secure accessibility_enabled 1 2>/dev/null || true

    echo "Starting launcher..."
    adb -s "$CONNECTED_DEVICE" shell am force-stop com.norma.tvlauncher
    adb -s "$CONNECTED_DEVICE" shell am start -n com.norma.tvlauncher/.MainActivity
    echo "Done! Launcher successfully updated and locked as default on TV."
else
    echo "TV is not reachable over ADB right now. Please ensure the TV screen is on and connected to Wi-Fi."
fi
