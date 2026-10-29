#!/usr/bin/env bash
# Build Headphones, install it on the TV and allow its Bluetooth access, so the TV never has to ask.
#   TV_HOST=<tv ip> addons/headphones/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.maxhayim.headphones
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/headphones.apk
OUT=$(tv 'pm install -r /data/local/tmp/headphones.apk; rm -f /data/local/tmp/headphones.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# Bluetooth access on Android 12+ (older versions grant it at install), and TCL's auto-start, which
# otherwise keeps the quiet guard from waking when headphones drop.
tv "pm grant $PKG android.permission.BLUETOOTH_CONNECT 2>/dev/null; cmd appops set $PKG APP_AUTO_START allow 2>/dev/null; echo permissions granted"
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Headphones is open on the TV; press Home and answer Show card for the card."
