#!/usr/bin/env bash
# Build the news screensaver and install it on the TV. Then run setup.sh to choose the feeds and make it
# the TV's screensaver.
#
# Usage: TV_HOST=192.168.1.50 addons/news-screensaver/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/news-screensaver/build-and-install.sh --uninstall
set -euo pipefail
# Git Bash: a Windows path, which the venv's Windows Python can open.
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.newsdream
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  # Put the screensaver from before setup.sh back first, then remove the app.
  "$HERE/setup.sh" --off
  tv "pm uninstall $PKG"
  exit 0
fi

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/newsdream.apk >/dev/null
tv "pm install -r /data/local/tmp/newsdream.apk; rm -f /data/local/tmp/newsdream.apk"
# TCL's TclAppBoot blocks the app's provider until it may auto-start; elsewhere the op doesn't exist.
tv "appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" >/dev/null
echo "Installed. Now: TV_HOST=$TV_HOST addons/news-screensaver/setup.sh"
