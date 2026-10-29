#!/usr/bin/env bash
# Build Ad Blocking, install it on the TV and, optionally, push its Pi-hole or AdGuard Home login.
#   TV_HOST=<tv ip> addons/ad-blocking/install.sh [config.json]   # {"kind": "pihole", "url": "http://192.168.1.2", "password": "..."}
set -euo pipefail
CONFIG="${1:-}"
if [ -n "$CONFIG" ]; then
  [ -f "$CONFIG" ] || { echo "no such file: $CONFIG" >&2; exit 1; }
  CONFIG="$(cd "$(dirname "$CONFIG")" && pwd)/$(basename "$CONFIG")"
  if command -v cygpath >/dev/null 2>&1; then CONFIG="$(cygpath -m "$CONFIG")"; fi
fi
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.maxhayim.adblocking
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=$ANDROID_HOME" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/ad-blocking.apk
OUT=$(tv 'pm install -r /data/local/tmp/ad-blocking.apk; rm -f /data/local/tmp/ad-blocking.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# The server and login go in as a file through the app's provider (only the ADB shell may write it), so the
# password never sits on a command line on the TV. The app reads it once and deletes it.
if [ -n "$CONFIG" ]; then
  STAGE=/data/local/tmp/ad-blocking-config.json
  tv --push "$CONFIG" "$STAGE" >/dev/null
  tv "content write --uri content://$PKG.config/config.json < $STAGE; rm -f $STAGE; echo config pushed"
fi
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Ad Blocking is open on the TV; press Home and answer Show card for the card."
