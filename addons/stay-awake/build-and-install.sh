#!/usr/bin/env bash
# Build Stay Awake and install it on the TV.
#
# Usage: TV_HOST=192.168.1.50 addons/stay-awake/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/stay-awake/build-and-install.sh --uninstall
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.callmejm.stayawake

if [ "${1:-}" = --uninstall ]; then
  "$PY" -I "$TOOLS/tv.py" "pm uninstall $PKG"
  exit 0
fi

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/stayawake.apk >/dev/null
"$PY" -I "$TOOLS/tv.py" "pm install -r /data/local/tmp/stayawake.apk; rm -f /data/local/tmp/stayawake.apk"
echo "Installed. Open Stay Awake and choose Turn on."
