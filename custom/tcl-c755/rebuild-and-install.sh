#!/usr/bin/env bash
# Rebuild the launcher and the weather add-on in Docker, install both, and push the theme.
# Usage: custom/tcl-c755/rebuild-and-install.sh <tv-ip:port>   (Wireless debugging, paired first)
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
DEV="${1:?usage: rebuild-and-install.sh <tv-ip:port>}"
export TV_ADB_BINARY="$(command -v adb)" TV_HOST="$DEV"
adb connect "$DEV" >/dev/null
( cd "$REPO" && tools/docker-build.sh && tools/docker-build.sh addons/weather )
for pair in "launcher/app/build/outputs/apk/release/app-release.apk launcher" \
            "addons/weather/app/build/outputs/apk/release/app-release.apk weather"; do
  set -- $pair
  adb -s "$DEV" push "$REPO/$1" "/data/local/tmp/$2.apk" >/dev/null
  adb -s "$DEV" shell pm install -r "/data/local/tmp/$2.apk"
  adb -s "$DEV" shell rm -f "/data/local/tmp/$2.apk"
done
( cd "$REPO" && tools/theme.sh custom/tcl-c755/theme )
