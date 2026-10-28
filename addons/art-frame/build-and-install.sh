#!/usr/bin/env bash
# Build Art Frame and install it on the TV: fetches the starter paintings if they are missing, builds, installs,
# and makes Art Frame the TV's screensaver (the one it replaces is printed, to put back with --uninstall).
#
# Usage: TV_HOST=192.168.1.50 addons/art-frame/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/art-frame/build-and-install.sh --uninstall
#
# For the remote's art button on a TCL frame TV (A300W / NXTFRAME), add to your theme's config.json app_keys:
#   "4181:com.gathin.artframe"
# and disable com.tcl.exhibit, which otherwise takes that button. See README.md.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.artframe
DREAM="$PKG/.ArtDream"
SAVED="$HERE/.screensaver-before"
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  before="$(cat "$SAVED" 2>/dev/null || true)"
  if [ -n "$before" ]; then
    tv "settings put secure screensaver_components $before"
  else
    tv "settings delete secure screensaver_components"
  fi
  tv "pm uninstall $PKG"
  echo "Removed. Take \"4181:$PKG\" out of app_keys if you added it, and re-enable com.tcl.exhibit if you disabled it."
  exit 0
fi

"$HERE/fetch-starter.sh"
(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
echo "Sending the app (about 16 MB, a minute or two)..."
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/artframe.apk >/dev/null
tv "pm install -r /data/local/tmp/artframe.apk; rm -f /data/local/tmp/artframe.apk"
# TCL's TclAppBoot holds back apps it has not allowed to auto-start; elsewhere these ops don't exist.
tv "appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; true" >/dev/null
# "Display over other apps": only used by the Wake into art setting, to open art mode as the TV wakes.
tv "appops set $PKG SYSTEM_ALERT_WINDOW allow" >/dev/null

current="$(tv "settings get secure screensaver_components" | tr -d '\r')"
if [ "$current" != "$DREAM" ]; then
  [ -f "$SAVED" ] || printf '%s\n' "$current" > "$SAVED"
  tv "settings put secure screensaver_components $DREAM"
  echo "Art Frame is now the screensaver (was: $current)."
fi
echo "Installed. Open Art Frame from the app grid, or press the art button once it is mapped (README.md)."
