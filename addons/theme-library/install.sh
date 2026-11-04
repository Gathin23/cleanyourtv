#!/usr/bin/env bash
# Build Theme Library, install it on the TV, allow its storage access so the TV never has to ask, and open it.
#   TV_HOST=<tv ip> addons/theme-library/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.rahulk847.themelibrary
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

SDK=$(tv 'getprop ro.build.version.sdk' | tr -dc '0-9')
if [ -n "$SDK" ] && [ "$SDK" -ge 30 ]; then
  echo "This TV runs Android SDK $SDK: Android 11 and newer close the launcher's theme folder to other apps," >&2
  echo "so Theme Library cannot work here. Use tools/theme.sh instead. Nothing was installed." >&2
  exit 1
fi

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/themelibrary.apk
OUT=$(tv 'pm install -r /data/local/tmp/themelibrary.apk; rm -f /data/local/tmp/themelibrary.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

tv "pm grant $PKG android.permission.READ_EXTERNAL_STORAGE; pm grant $PKG android.permission.WRITE_EXTERNAL_STORAGE; echo storage granted"
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Theme Library is open on the TV and has saved the theme on screen; press Home and answer Show card for the card."
