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

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
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
  # Some TVs cannot resolve the provider until its app is running (as reported for Smart Home in #1752).
  tv "am start -n $PKG/.MainActivity >/dev/null; sleep 2; echo opened"
  # tv.py does not pass through the remote shell status, and Android's content command may print an
  # error while exiting 0. Accept only an exit-0 write with no diagnostic output.
  OUT=$(tv "ok=0; for attempt in 1 2 3; do if write_out=\$(content write --user 0 --uri content://$PKG.config/config.json < $STAGE 2>&1) && [ -z \"\$write_out\" ]; then ok=1; break; fi; [ -z \"\$write_out\" ] || printf '%s\n' \"\$write_out\"; [ \"\$attempt\" -eq 3 ] || sleep 1; done; if rm -f $STAGE && [ \"\$ok\" -eq 1 ]; then echo __AD_BLOCKING_CONFIG_OK__; fi")
  case "$OUT" in
    *__AD_BLOCKING_CONFIG_OK__*) echo "config pushed" ;;
    *) echo "$OUT" >&2; echo "ERROR: config write failed (or staged config could not be removed)" >&2; exit 1 ;;
  esac
fi
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Ad Blocking is open on the TV; press Home and answer Show card for the card."
