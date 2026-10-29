#!/usr/bin/env bash
# Build Startup & Branding, install it on the TV and open it. It changes nothing on the TV but its own settings:
# no permissions, no Home change, nothing at boot.
#   TV_HOST=<tv ip> addons/startup-branding/install.sh
#   TV_HOST=<tv ip> addons/startup-branding/install.sh --uninstall   # removes the app, its settings and pictures
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.maxhayim.startupbranding
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }
if [ "${1:-}" = "--uninstall" ]; then
  tv "pm uninstall $PKG"
  exit 0
fi
[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=$ANDROID_HOME" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain
"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/startup-branding.apk
OUT=$(tv 'pm install -r /data/local/tmp/startup-branding.apk; rm -f /data/local/tmp/startup-branding.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Startup & Branding is open on the TV; on the Clean Your TV launcher, answer Show for its card."
