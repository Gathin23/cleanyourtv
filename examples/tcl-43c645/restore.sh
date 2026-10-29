#!/usr/bin/env bash
# Undo examples/tcl-43c645: re-enable every package in debloat.txt, give Home back to the Google TV
# launcher and switch the launcher's accessibility services off. Nothing was uninstalled, so this
# brings the TV back to how it shipped. Pass --uninstall to remove the custom launcher as well.
#
# Usage: TV_HOST=192.168.1.50 examples/tcl-43c645/restore.sh [--uninstall]
# Re-enable one app only:  adb shell pm enable <package>
set -euo pipefail
cd "$(dirname "$0")"
TV="${TV_HOST:?set TV_HOST to the TV IP}:${TV_PORT:-5555}"
ADB="${ADB:-${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb}"
[ -n "${ADB_PORT:-}" ] && ADB="$ADB -P $ADB_PORT"
A="$ADB -s $TV"
$ADB connect "$TV" >/dev/null

# Package names only; the list is checked so nothing else reaches the TV's shell.
PKGS=$(tr -d '\r' < debloat.txt | sed -E 's/#.*$//' | awk 'NF {print $1}')
for p in $PKGS; do
  [[ "$p" =~ ^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$ ]] || { echo "skipping bad entry: $p" >&2; continue; }
  # One pm call per shell: chained pm commands can stop silently after the first.
  $A shell pm enable "$p" | tr -d '\r'
done

$A shell settings put secure enabled_accessibility_services "''"
# launcherx is not in debloat.txt: build-and-install.sh disables it.
$A shell pm enable com.google.android.apps.tv.launcherx | tr -d '\r'
$A shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
if [ "${1:-}" = --uninstall ]; then
  $A shell pm uninstall com.gathin.tvhome
fi
$A shell input keyevent HOME
echo "Home: $($A shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)"
