#!/usr/bin/env bash
# Build TV Static, install it on the TV, let it open over other apps and start the screen watcher, which plays
# the effect once so you can see it.
#
# Usage: TV_HOST=192.168.1.50 addons/tv-static/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/tv-static/build-and-install.sh --no-play     # install without playing it now
#        TV_HOST=192.168.1.50 addons/tv-static/build-and-install.sh --uninstall
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.luyzfernando08.tvstatic
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  tv "pm uninstall $PKG"
  exit 0
fi
PLAY=true
[ "${1:-}" = --no-play ] && PLAY=false

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/tvstatic.apk >/dev/null
tv "pm install -r /data/local/tmp/tvstatic.apk; rm -f /data/local/tmp/tvstatic.apk"
# Android 10 and later let an app open a screen from the background only when it may draw over other apps.
tv "appops set $PKG SYSTEM_ALERT_WINDOW allow"
# TCL's TclAppBoot keeps apps from starting at boot unless they may auto-start (AUTO_START on G08 builds,
# APP_AUTO_START on G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
tv "appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" >/dev/null
# Starting the watcher once takes the app out of the stopped state, without which Android withholds BOOT_COMPLETED.
tv "am start-foreground-service -n $PKG/.WatchService --ez play $PLAY"
echo "Installed. The static plays whenever the screen turns on; its settings are on the TV Static card."
