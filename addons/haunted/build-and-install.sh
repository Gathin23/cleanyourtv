#!/usr/bin/env bash
# Build Haunted, install it on the TV and make it the TV's screensaver.
#
# Usage: TV_HOST=192.168.1.50 addons/haunted/build-and-install.sh
#        TV_HOST=192.168.1.50 START=1 addons/haunted/build-and-install.sh   # and show it now
#        TV_HOST=192.168.1.50 addons/haunted/build-and-install.sh --uninstall   # previous screensaver back, app removed
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.haunted
DREAM=$PKG/.HauntedDream
# The screensaver the TV had before the first run, so --uninstall can put it back.
BEFORE=/data/local/tmp/cyt-haunted-before
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  tv "if [ -f $BEFORE ]; then { read c; read e; } < $BEFORE; if [ \"\$c\" = null ]; then settings delete secure screensaver_components >/dev/null; else settings put secure screensaver_components \"\$c\"; fi; [ \"\$e\" = null ] || settings put secure screensaver_enabled \"\$e\"; rm -f $BEFORE; fi; pm uninstall $PKG; echo screensaver: \$(settings get secure screensaver_components)"
  exit 0
fi

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/haunted.apk >/dev/null
tv "pm install -r /data/local/tmp/haunted.apk; rm -f /data/local/tmp/haunted.apk"
tv "[ -f $BEFORE ] || { settings get secure screensaver_components; settings get secure screensaver_enabled; } > $BEFORE; settings put secure screensaver_components $DREAM; settings put secure screensaver_enabled 1" >/dev/null
echo "Installed: Haunted is the screensaver. Undo with: addons/haunted/build-and-install.sh --uninstall"
[ -z "${START:-}" ] || tv "am start -n com.android.systemui/.Somnambulator >/dev/null; sleep 3; dumpsys dreams | grep mCurrentDreamName"
