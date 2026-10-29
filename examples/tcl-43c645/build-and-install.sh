#!/usr/bin/env bash
# Build launcher/ and install it on a TCL 43C645 / 60C635 as Home, with this folder's config.json
# (dock, Live TV, remote keys) and the remote-key services on.
# To change the dock order, edit dock_apps in config.json and run this again.
# Uses the stock adb binary. Safe to re-run: Android turns accessibility services off on every
# reinstall, so they are switched on again each time.
#
# Usage: TV_HOST=192.168.1.50 examples/tcl-43c645/build-and-install.sh
set -euo pipefail
cd "$(dirname "$0")/../../launcher"
TV="${TV_HOST:?set TV_HOST to the TV IP}:${TV_PORT:-5555}"
export JAVA_HOME="${JAVA_HOME:-$(ls -d "$HOME"/Android/jdk/jdk-17* 2>/dev/null | head -1)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
# On WSL2 the adb daemon can hang while forking; run "adb -P 15037 nodaemon server" in another
# terminal and set ADB_PORT=15037.
[ -n "${ADB_PORT:-}" ] && ADB="$ADB -P $ADB_PORT"
A="$ADB -s $TV"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

# The wrapper jar directly, so a checkout with CRLF line endings in gradlew still builds.
java -Xmx64m -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleRelease -q --console=plain
APK=app/build/outputs/apk/release/app-release.apk

$ADB connect "$TV" >/dev/null
$A push "$APK" /data/local/tmp/tvhome.apk
OUT=$($A shell pm install -r /data/local/tmp/tvhome.apk | tr -d '\r')
$A shell rm -f /data/local/tmp/tvhome.apk
echo "$OUT"
[ "$OUT" = Success ] || { echo "install failed, Home left as it was" >&2; exit 1; }

# This TV's dock, Live TV app and remote keys (examples/tcl-43c645/config.json), overlaid on
# config.xml by the launcher at start. A new theme_revision makes it drop on-TV layout choices.
DIR=/sdcard/Android/data/com.gathin.tvhome/files
$A shell mkdir -p "$DIR"
$A push ../examples/tcl-43c645/config.json "$DIR/config.json" >/dev/null
$A shell "printf '%s' $(date +%s) > $DIR/theme_revision"

START=$($A shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity | tr -d '\r')
echo "$START" | grep -q 'Status: ok' || { echo "launcher did not start, Home left as it was" >&2; exit 1; }

$A shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION
$A shell cmd package compile -m speed -f com.gathin.tvhome >/dev/null
# Stock Home and the Google TV setup wizard (which claims Home once launcherx is off). A no-op when
# debloat.txt already disabled setupwraith.
$A shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
$A shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
$A shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
HOME_NOW=$($A shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 | tr -d '\r')
echo "Home: $HOME_NOW"
case "$HOME_NOW" in
  com.gathin.tvhome/*) ;;
  *) echo "Home is not the new launcher; putting launcherx back" >&2
     $A shell pm enable com.google.android.apps.tv.launcherx
     $A shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
     exit 1 ;;
esac

# InputKeyService: Input button and the Netflix/YouTube/Prime buttons (app_keys).
# HomeKeyService: the TCL and gear buttons open Settings (settings_key_keycodes).
# The reinstall unbinds them, and they bind again only when the list changes: clear it first.
$A shell settings put secure enabled_accessibility_services "''"
sleep 1
$A shell settings put secure enabled_accessibility_services \
  com.gathin.tvhome/.InputKeyService:com.gathin.tvhome/.HomeKeyService
$A shell settings put secure accessibility_enabled 1
$A shell input keyevent HOME
echo "Installed. Check the remote buttons with the real remote."
