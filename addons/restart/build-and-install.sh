#!/usr/bin/env bash
# Build Restart, install it on the TV and switch on its accessibility service (it opens the power menu and presses
# Restart, only after you chose Restart in the app).
#
# Usage: TV_HOST=192.168.1.50 addons/restart/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/restart/build-and-install.sh --uninstall
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.jemychao.restart
SVC=$PKG/$PKG.RestartService
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  # Take the service out of the enabled list, keeping any others, then remove the app.
  tv "s=\$(settings get secure enabled_accessibility_services); s=\$(echo \":\$s:\" | sed 's#:$SVC:#:#; s#^:##; s#:\$##'); if [ -z \"\$s\" ]; then settings delete secure enabled_accessibility_services; else settings put secure enabled_accessibility_services \$s; fi; pm uninstall $PKG"
  exit 0
fi

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/restart.apk >/dev/null
tv "pm install -r /data/local/tmp/restart.apk; rm -f /data/local/tmp/restart.apk"
# Add the service to the enabled list without switching off the ones already there. Take it out first: after an update
# Android 9 can keep the service bound to the replaced APK, where it opens the power menu but reads no windows.
tv "s=\$(settings get secure enabled_accessibility_services); s=\$(echo \":\$s:\" | sed 's#:$SVC:#:#; s#^:##; s#:\$##'); if [ -z \"\$s\" ] || [ \"\$s\" = null ]; then settings delete secure enabled_accessibility_services; else settings put secure enabled_accessibility_services \$s; fi; sleep 1; if [ -z \"\$s\" ] || [ \"\$s\" = null ]; then s=$SVC; else s=\$s:$SVC; fi; settings put secure enabled_accessibility_services \$s; settings put secure accessibility_enabled 1"
echo "Installed. Press Home and answer Show card for the Restart card, or open Restart from the apps."
