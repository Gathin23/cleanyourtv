#!/usr/bin/env bash
# Build Remote buttons, install it on the TV, let it read the key log and open apps over other apps, and switch its
# service on. Pick the buttons afterwards on the Remote buttons card.
#
# Usage: TV_HOST=192.168.1.50 addons/remote-buttons/build-and-install.sh
#        SKIP_BUILD=1 TV_HOST=192.168.1.50 addons/remote-buttons/build-and-install.sh   # APK built in Docker
#        TV_HOST=192.168.1.50 addons/remote-buttons/build-and-install.sh --uninstall
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.knochango.remotebuttons
SVC="$PKG/.KeyLogService"
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

# Takes the service off the accessibility list and leaves every other service on it.
service_off='s=$(settings get secure enabled_accessibility_services); n=$(echo "$s" | tr ":" "\n" | grep -v "^'"$PKG"'/" | grep -v "^null$" | paste -sd: -); settings put secure enabled_accessibility_services "$n"; [ -z "$n" ] && settings put secure accessibility_enabled 0; true'

case "${1:-}" in
  --uninstall) echo "$service_off" | tv --batch >/dev/null; tv "pm uninstall $PKG"; exit 0 ;;
  "") ;;
  *) echo "unknown option: $1" >&2; exit 1 ;;
esac

if [ "${SKIP_BUILD:-}" != "1" ]; then
  (cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; or build in Docker: tools/docker-build.sh addons/remote-buttons, then SKIP_BUILD=1)" >&2; exit 1; }
fi
APK="$HERE/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "no $APK; build it first" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$APK" /data/local/tmp/remotebuttons.apk >/dev/null
tv "pm install -r /data/local/tmp/remotebuttons.apk; rm -f /data/local/tmp/remotebuttons.apk"
# The window manager's key lines are how a press is seen; without this logcat shows the app only its own lines.
tv "pm grant $PKG android.permission.READ_LOGS"
# Android 10 and later let an app open another from the background only when it may draw over other apps.
tv "appops set $PKG SYSTEM_ALERT_WINDOW allow"
# TCL's TclAppBoot keeps apps from starting at boot unless they may auto-start (AUTO_START on G08 builds,
# APP_AUTO_START on G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
tv "appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" >/dev/null
# Written straight after an install, the service is dropped from the list before Android has registered it.
sleep 5
echo 's=$(settings get secure enabled_accessibility_services); case "$s" in *'"$SVC"'*) ;; null|"") settings put secure enabled_accessibility_services '"$SVC"' ;; *) settings put secure enabled_accessibility_services "$s:'"$SVC"'" ;; esac; settings put secure accessibility_enabled 1' | tv --batch >/dev/null
echo "Installed. Open the Remote buttons card, choose Add a button and press the button on the remote."
