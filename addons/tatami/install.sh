#!/usr/bin/env bash
# Build Tatami, install it on the TV, make it the TV's screensaver and open it once so you see it.
#
# Usage: TV_HOST=192.168.1.50 ./install.sh                 build, install, make it the screensaver, show it
#        TV_HOST=192.168.1.50 ./install.sh --no-show       the same, without opening Tatami at the end
#        TV_HOST=192.168.1.50 SKIP_BUILD=1 ./install.sh    install the APK already built (tools/docker-build.sh addons/tatami)
#        TV_HOST=192.168.1.50 ./install.sh --uninstall     previous screensaver back, app removed
#
# In a cleanyourtv checkout this is addons/tatami/install.sh and uses that checkout's tools/. Anywhere else it uses
# the cleanyourtv checkout in CLEANYOURTV (default: ../cleanyourtv next to this folder).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
case "$#:${1:-}" in 0:|1:--no-show|1:--uninstall) ;; *) echo "usage: $0 [--no-show | --uninstall]" >&2; exit 2 ;; esac
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
if [ -f "$HERE/../../tools/tv.py" ]; then TOOLS="$HERE/../../tools"; else TOOLS="${CLEANYOURTV:-$HERE/../cleanyourtv}/tools"; fi
[ -f "$TOOLS/tv.py" ] || { echo "no cleanyourtv tools at $TOOLS: clone https://github.com/Gathin23/cleanyourtv next to this folder, or set CLEANYOURTV to your checkout" >&2; exit 1; }
# Windows Python cannot open /c/... paths, so hand it C:/... where Git Bash has them.
TOOLS="$(cd "$TOOLS" && (pwd -W 2>/dev/null || pwd))"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.pbanddev.tatami
DREAM=$PKG/.FloorPlanDream
# The screensaver the TV had before the first run, so --uninstall can put it back.
BEFORE=/data/local/tmp/cyt-tatami-before
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  tv "if [ -f $BEFORE ]; then { read c; read e; } < $BEFORE; if [ \"\$c\" = null ]; then settings delete secure screensaver_components >/dev/null; else settings put secure screensaver_components \"\$c\"; fi; [ \"\$e\" = null ] || settings put secure screensaver_enabled \"\$e\"; rm -f $BEFORE; fi; pm uninstall $PKG; echo screensaver: \$(settings get secure screensaver_components)"
  exit 0
fi

if [ -z "${SKIP_BUILD:-}" ]; then
  (cd "$HERE" && ./gradlew assembleRelease -q) || {
    echo "build failed (JDK 17 and Android SDK 34 needed; or build with tools/docker-build.sh addons/tatami and run with SKIP_BUILD=1)" >&2; exit 1; }
fi
APK="$HERE/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "no $APK: build it first (./gradlew assembleRelease, or tools/docker-build.sh addons/tatami)" >&2; exit 1; }

# Wake first: on some sets an install waits while a screensaver is up.
tv 'input keyevent WAKEUP' >/dev/null
"$PY" -I "$TOOLS/b64push.py" "$APK" /data/local/tmp/tatami.apk >/dev/null
OUT="$(tv 'pm install -r /data/local/tmp/tatami.apk; rm -f /data/local/tmp/tatami.apk')"
case "$OUT" in *Success*) ;; *) echo "$OUT" >&2; echo "install failed: Tatami is not on the TV" >&2; exit 1 ;; esac
# Compiled ahead of time, so the first frames are smooth. Not every Android version has the command.
tv "cmd package compile -m speed -f $PKG" >/dev/null 2>&1 || true
tv "[ -f $BEFORE ] || { settings get secure screensaver_components; settings get secure screensaver_enabled; } > $BEFORE; settings put secure screensaver_components $DREAM; settings put secure screensaver_enabled 1" >/dev/null
# TCL's TclAppBoot keeps sideloaded apps from starting, a screensaver too, unless they may auto-start (AUTO_START on
# G08 builds, APP_AUTO_START on G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
tv "appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" >/dev/null

# When the screensaver starts is the TV's own setting. Say what it is and how to change it; never change it here.
MS="$(tv 'settings get system screen_off_timeout' | tr -d '\r' | tail -n 1)" || MS=""
SHOWPY="tools/${PY#"$TOOLS/"}"
if [[ "$MS" =~ ^-?[0-9]+$ ]]; then
  if [ "$MS" -lt 0 ] || [ "$MS" -ge 2147483647 ]; then
    echo "The TV never starts its screensaver by itself (Settings > Device Preferences > Screen saver > When to start)."
  else
    MIN=$(( (MS + 59999) / 60000 ))
    if [ "$MIN" -ge 60 ] && [ $(( MIN % 60 )) -eq 0 ]; then WHEN="$(( MIN / 60 )) h"; else WHEN="$MIN min"; fi
    echo "The screensaver starts after $WHEN with no key press (Settings > Device Preferences > Screen saver > When to start)."
  fi
  echo "To make it 5 min, from your cleanyourtv checkout: TV_HOST=$TV_HOST $SHOWPY -I tools/tv.py 'settings put system screen_off_timeout 300000'"
fi

[ "${1:-}" = --no-show ] || tv "am start -n $PKG/.PreviewActivity" >/dev/null
echo "Installed: Tatami is the screensaver. Undo with: $0 --uninstall"
