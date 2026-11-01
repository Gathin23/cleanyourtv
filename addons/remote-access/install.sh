#!/usr/bin/env bash
# Build Remote Access, install it on the TV and let its button switch network debugging off.
#   TV_HOST=<tv ip> addons/remote-access/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.maxhayim.remoteaccess
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/remote-access.apk
OUT=$(tv 'pm install -r /data/local/tmp/remote-access.apk; rm -f /data/local/tmp/remote-access.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# Only this lets the app switch network debugging off; it can read everything without it.
tv "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS; echo permission granted"
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Remote Access is open on the TV; press Home and answer Show card for the card."
