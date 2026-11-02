#!/usr/bin/env bash
# Build Now Playing, install it on the TV, and turn on its notification access and its remote helper.
#   TV_HOST=<tv ip> addons/now-playing/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.raqs31.nowplaying
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/now-playing.apk
OUT=$(tv 'pm install -r /data/local/tmp/now-playing.apk; rm -f /data/local/tmp/now-playing.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# Notification access, which is what lets an app see other apps' media sessions. Added next to any listener
# already on (the launcher's, for one), never in place of it.
LISTENER=$PKG/$PKG.SessionAccess
tv "cmd notification allow_listener $LISTENER 2>/dev/null; true" >/dev/null
OUT=$(tv "s=\$(settings get secure enabled_notification_listeners); case \"\$s\" in *$LISTENER*) ;; null|'') settings put secure enabled_notification_listeners $LISTENER ;; *) settings put secure enabled_notification_listeners \"\$s:$LISTENER\" ;; esac; settings get secure enabled_notification_listeners")
case "$OUT" in *"$LISTENER"*) echo "notification access on" ;; *) echo "ERROR: notification access not on: $OUT" >&2; exit 1 ;; esac

# TCL's firmware binds neither service after a reboot without its auto-start app-op; other TVs ignore it.
tv "cmd appops set $PKG AUTO_START allow 2>/dev/null; cmd appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" >/dev/null

# The remote helper (the Home card), added to whatever accessibility services are already on. A service that
# was already listed is unbound by the reinstall and binds again only once the list changes.
SVC=$PKG/.CardService
tv "s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$SVC*) settings put secure enabled_accessibility_services \"\$s\"; settings put secure accessibility_enabled 1; sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$s\" ;; null|'') settings put secure enabled_accessibility_services $SVC ;; *) settings put secure enabled_accessibility_services \"\$s:$SVC\" ;; esac; settings put secure accessibility_enabled 1; echo remote helper enabled"

tv "am start -n $PKG/.PlayerActivity >/dev/null; echo opened"
echo "Installed. Now Playing is open on the TV; press Home and answer Show card for the card."
