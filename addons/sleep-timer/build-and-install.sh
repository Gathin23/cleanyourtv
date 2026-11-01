#!/usr/bin/env bash
# Build Sleep Timer, install it on the TV and switch on its accessibility service (only the lock-screen action,
# which puts the TV to standby) and the right to open its warning over other apps.
#
# Usage: TV_HOST=192.168.1.50 addons/sleep-timer/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/sleep-timer/build-and-install.sh --uninstall
# Try it without waiting (adb only): a 70 s timer, the warning after 10 s:
#        TV_HOST=192.168.1.50 tools/tv.py 'am broadcast -n io.github.luyzfernando08.sleeptimer/.AlarmReceiver -a io.github.luyzfernando08.sleeptimer.TEST --ei seconds 70'
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.luyzfernando08.sleeptimer
SVC=$PKG/$PKG.SleepService
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  # Take the service out of the enabled list, keeping any others, then remove the app.
  tv "s=\$(settings get secure enabled_accessibility_services); s=\$(echo \":\$s:\" | sed 's#:$SVC:#:#; s#^:##; s#:\$##'); if [ -z \"\$s\" ]; then settings delete secure enabled_accessibility_services; else settings put secure enabled_accessibility_services \$s; fi; pm uninstall $PKG"
  exit 0
fi

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/sleeptimer.apk >/dev/null
tv "pm install -r /data/local/tmp/sleeptimer.apk; rm -f /data/local/tmp/sleeptimer.apk"
# Add the service to the enabled list without switching off the ones already there.
tv "s=\$(settings get secure enabled_accessibility_services); case :\$s: in *:$SVC:*) ;; *) if [ -z \$s ] || [ \$s = null ]; then s=$SVC; else s=\$s:$SVC; fi; settings put secure enabled_accessibility_services \$s;; esac; settings put secure accessibility_enabled 1"
# Android 10 and later only let an app open its warning from the background when it may draw over other apps;
# Android 12 and later need the exact-alarm grant for the timer to fire on the minute.
tv "appops set $PKG SYSTEM_ALERT_WINDOW allow; appops set $PKG SCHEDULE_EXACT_ALARM allow 2>/dev/null; true"
# TCL's TclAppBoot refuses to bind SleepService while the app is not running unless it may auto-start (AUTO_START
# on G08 builds, APP_AUTO_START on G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
tv "appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" >/dev/null
echo "Installed. Pick a time on the Sleep Timer card."
