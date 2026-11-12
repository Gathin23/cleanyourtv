#!/usr/bin/env bash
# Build Remote buttons, install it on the TV, grant what it needs and turn its service on.
#   TV_HOST=<tv ip> addons/remote-buttons/install.sh
# Your own button map (optional): TV_HOST=<tv ip> addons/remote-buttons/install.sh keys.txt
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
KEYS="${1:-}"
if [ -n "$KEYS" ]; then KEYS="$(cd "$(dirname "$KEYS")" && pwd)/$(basename "$KEYS")"; [ -f "$KEYS" ] || { echo "no such file: $1" >&2; exit 2; }; fi
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.abner97.remotebuttons
SVC=$PKG/.KeyLogService
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/remote-buttons.apk
OUT=$(tv 'pm install -r /data/local/tmp/remote-buttons.apk; rm -f /data/local/tmp/remote-buttons.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# READ_LOGS: the key log. SYSTEM_ALERT_WINDOW: opening an app while another is in front. AUTO_START: TCL firmware
# refuses to bind an app's accessibility service without it; other TVs ignore the op.
tv "pm grant $PKG android.permission.READ_LOGS; appops set $PKG SYSTEM_ALERT_WINDOW allow; appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; echo granted"

if [ -n "$KEYS" ]; then
  tv "mkdir -p /sdcard/Android/data/$PKG/files"
  "$PY" -I ../../tools/b64push.py "$KEYS" "/sdcard/Android/data/$PKG/files/keys.txt"
fi

# Enabled straight after `pm install -r`, Android can drop a service it has not registered yet.
sleep 4
# Added to the services already on, not in place of them.
tv "s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$SVC*) ;; null|'') settings put secure enabled_accessibility_services $SVC ;; *) settings put secure enabled_accessibility_services \"\$s:$SVC\" ;; esac; settings put secure accessibility_enabled 1; echo enabled"
sleep 2
case "$(tv 'dumpsys accessibility')" in
  *"Remote buttons"*) echo "Bound. Press a mapped button on the remote; the card (Show card on Home) shows the map." ;;
  *) echo "WARNING: the service is not bound yet. Turn on Remote buttons in Settings > Accessibility." >&2 ;;
esac
