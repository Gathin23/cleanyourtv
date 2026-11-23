#!/usr/bin/env bash
# Build Live TV mode, install it on the TV and switch its accessibility helper on.
#
# Usage: TV_HOST=192.168.1.50 addons/live-tv-mode/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/live-tv-mode/build-and-install.sh --off        # helper off, app kept
#        TV_HOST=192.168.1.50 addons/live-tv-mode/build-and-install.sh --uninstall
# Android switches an app's accessibility helper off whenever the app is installed again or force-stopped, so run
# this again after either (or switch it on under Settings > Accessibility > Live TV mode).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.dpetrasgr.livetvmode
SVC=$PKG/.LiveTvService
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

# Takes the helper off the enabled list and leaves every other service on it.
helper_off() {
  tv "s=\$(settings get secure enabled_accessibility_services); n=\$(printf '%s' \"\$s\" | tr ':' '\n' | grep -v '^$SVC\$' | paste -sd: -); settings put secure enabled_accessibility_services \"\$n\"; echo helper off"
}

case "${1:-}" in
  --uninstall) helper_off; tv "pm uninstall $PKG"; exit 0 ;;
  --off) helper_off; exit 0 ;;
  "") ;;
  *) echo "unknown option: $1 (use --off or --uninstall)" >&2; exit 1 ;;
esac

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/livetvmode.apk >/dev/null
OUT=$(tv "pm install -r /data/local/tmp/livetvmode.apk; rm -f /data/local/tmp/livetvmode.apk")
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "pm install failed; nothing else was changed" >&2; exit 1 ;; esac
# TCL's TclAppBoot keeps apps from starting unless they may auto-start (AUTO_START on G08 builds, APP_AUTO_START on
# G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
tv "appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" >/dev/null
# Append the helper to the enabled list, keeping the services already there. A helper already listed was dropped
# by the reinstall and binds again only when the list changes, so it is taken off and written back.
tv "s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$SVC*) settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$s\" ;; null|'') settings put secure enabled_accessibility_services $SVC ;; *) settings put secure enabled_accessibility_services \"\$s:$SVC\" ;; esac; settings put secure accessibility_enabled 1"
sleep 2
if tv "dumpsys accessibility" | grep -q "$PKG"; then
  echo "Installed, and the helper is bound. Press Home: Live TV opens. Settings: the Live TV mode card or tile."
else
  echo "Installed, but the helper is not bound yet: switch it on under Settings > Accessibility > Live TV mode." >&2
  exit 1
fi
