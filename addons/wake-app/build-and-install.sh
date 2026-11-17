#!/usr/bin/env bash
# Build Open on wake, install it on the TV, let it open apps over other apps and start the screen watcher.
#
# Usage: TV_HOST=192.168.1.50 addons/wake-app/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/wake-app/build-and-install.sh --app com.google.android.youtube.tv
#        TV_HOST=192.168.1.50 addons/wake-app/build-and-install.sh --app <package> --link '<address>'
#        TV_HOST=192.168.1.50 addons/wake-app/build-and-install.sh --uninstall
# --app and --link set the choice from here; leave them out to pick the app on the TV (the Open on wake card).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.luyzfernando08.wakeapp
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

APP=""
LINK=""
while [ $# -gt 0 ]; do
  case "$1" in
    --uninstall) tv "pm uninstall $PKG"; exit 0 ;;
    --app) APP="${2:?--app needs a package name}"; shift 2 ;;
    --link) LINK="${2:?--link needs an address}"; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 1 ;;
  esac
done

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/wakeapp.apk >/dev/null
tv "pm install -r /data/local/tmp/wakeapp.apk; rm -f /data/local/tmp/wakeapp.apk"
# Android 10 and later let an app open another from the background only when it may draw over other apps.
tv "appops set $PKG SYSTEM_ALERT_WINDOW allow"
# TCL's TclAppBoot keeps apps from starting at boot unless they may auto-start (AUTO_START on G08 builds,
# APP_AUTO_START on G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
tv "appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" >/dev/null
# Starting the watcher once takes the app out of the stopped state, without which Android withholds BOOT_COMPLETED.
EXTRAS=""
[ -n "$APP" ] && EXTRAS="$EXTRAS --es app '$APP'"
[ -n "$LINK" ] && EXTRAS="$EXTRAS --es link '$LINK'"
tv "am start-foreground-service -n $PKG/.WatchService$EXTRAS"
echo "Installed. Pick the app on the Open on wake card (or pass --app)."
