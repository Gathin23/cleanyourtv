#!/usr/bin/env bash
# Put a TCL C755 (G08, Android 14) back to stock after the changes in this folder.
# Usage: ./restore-all.sh <tv-ip:port>   (Wireless debugging: pair first, use the current connect port)
#
# ASSUMPTION: the TV had no disabled packages before this work (true on the unit this was written for).
# Check `adb shell pm list packages -d` from your own backup first: this re-enables EVERY disabled package.
set -u
DEV="${1:?usage: restore-all.sh <tv-ip:port>}"
A="adb -s $DEV shell"
adb connect "$DEV" >/dev/null 2>&1
echo "== stock launcher and setup wizard first"
for p in com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith; do $A pm enable "$p"; done
echo "== re-enable every disabled package"
for p in $($A pm list packages -d | sed 's/package://' | tr -d '\r'); do $A pm enable "$p"; done
echo "== stock launcher as Home"
$A cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
echo "== stock screensaver"
$A settings put secure screensaver_components com.google.android.backdrop/com.google.android.backdrop.Backdrop
echo "== remove the custom launcher and the weather add-on"
$A pm uninstall com.gathin.tvhome
$A pm uninstall io.github.sumyabazar1.weather
echo "Done. Reboot the TV: adb -s $DEV reboot"
