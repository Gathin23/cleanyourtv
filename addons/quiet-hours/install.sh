#!/usr/bin/env bash
# Build Quiet Hours and install it on the TV; TCL's auto-start lets the cap come back after a reboot.
#   TV_HOST=<tv ip> addons/quiet-hours/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.maxhayim.quiethours
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=$ANDROID_HOME" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/quiet-hours.apk
OUT=$(tv 'pm install -r /data/local/tmp/quiet-hours.apk; rm -f /data/local/tmp/quiet-hours.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

tv "cmd appops set $PKG APP_AUTO_START allow 2>/dev/null; echo auto-start allowed"
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Quiet Hours is open on the TV; press Home and answer Show card for the card."
