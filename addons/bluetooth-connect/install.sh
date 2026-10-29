#!/usr/bin/env bash
# Build Bluetooth Connect, install it on the TV and let it switch its own key service on.
#   TV_HOST=<tv ip> addons/bluetooth-connect/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=com.gathin.btconnect
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/bluetooth-connect.apk
OUT=$(tv 'pm install -r /data/local/tmp/bluetooth-connect.apk; rm -f /data/local/tmp/bluetooth-connect.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# Nearby devices on Android 12+; Android 11 and older grant BLUETOOTH at install.
tv "pm grant $PKG android.permission.BLUETOOTH_CONNECT 2>/dev/null; echo permission granted"
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Bluetooth Connect is open on the TV; press Home and answer Show card for the card."
