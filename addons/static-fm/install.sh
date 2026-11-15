#!/usr/bin/env bash
# Build Static FM, install it on the TV, let it read the TV's sound and open it.
#   TV_HOST=<tv ip> addons/static-fm/install.sh
#   TV_HOST=<tv ip> SCREENSAVER=1 addons/static-fm/install.sh   # also make it the screensaver
#   TV_HOST=<tv ip> addons/static-fm/install.sh --uninstall
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
PY=../../tools/venv/bin/python
[ -x "$PY" ] || PY=../../tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.cleanyourtv.staticfm
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

if [ "${1:-}" = --uninstall ]; then
  tv "case \$(settings get secure screensaver_components) in $PKG/*) settings delete secure screensaver_components;; esac; pm uninstall $PKG"
  exit 0
fi

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/static-fm.apk >/dev/null
OUT=$(tv 'pm install -r /data/local/tmp/static-fm.apk; rm -f /data/local/tmp/static-fm.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# The Visualizer on the output mix needs RECORD_AUDIO; granting it here saves the prompt on the TV.
tv "pm grant $PKG android.permission.RECORD_AUDIO" || echo "Could not grant RECORD_AUDIO; the app asks on first open."
# Notification access, so the picture can name the track that is playing.
tv "cmd notification allow_listener $PKG/$PKG.TrackListener" || echo "Could not give notification access; no track line will show."
if [ "${SCREENSAVER:-}" = 1 ]; then
  tv "settings put secure screensaver_components $PKG/.GrainDream; settings put secure screensaver_enabled 1"
fi
tv "am start -n $PKG/.GrainActivity >/dev/null; echo opened"
echo "Installed. Left/Right: style, Up/Down: inks, OK: how much it follows the music, Back: leave."
