#!/usr/bin/env bash
# Build the Apple TV style launcher, install it on the Nebula D2426, make it Home, apply the debloat list
# (which disables the stock launcher only once the new one can take Home) and push this projector's dock config.
#
#   TV_HOST=<projector ip> examples/anker-nebula-d2426/build-and-install.sh
#
# Needs: ADB on (Developer options > USB debugging, and network/Wireless debugging), JDK 17 or newer, the
# Android SDK (Android Studio's default location is found), and tools/venv (see the main README).
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the projector IP, e.g. TV_HOST=192.168.68.51 $0}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
export TV_HOST

# launcher/build-and-install.sh looks for a JDK 17; any 17+ works, so fall back to the newest one here.
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"
  [ -n "$JAVA_HOME" ] && export JAVA_HOME
fi

# STOCK_LAUNCHER=none: the stock launcher is disabled by the debloat list below instead, so that
# restore.sh records it and turns it back on.
# While the stock launcher is still enabled it outranks the new one, so that script reports that Home did not
# switch; that is expected on a first install. A failed build or install is not, so check the package is there.
STOCK_LAUNCHER=none "$ROOT/launcher/build-and-install.sh" || true
case "$("$ROOT/tools/venv/bin/python" -I "$ROOT/tools/tv.py" 'pm path com.gathin.tvhome')" in
  *package:*) ;;
  *) echo "ERROR: the launcher is not installed; nothing was disabled." >&2; exit 1 ;;
esac

"$ROOT/tools/disable.sh" "$HERE/debloat.txt"
"$ROOT/tools/venv/bin/python" -I "$ROOT/tools/tv.py" \
  "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity"
"$ROOT/tools/theme.sh" "$HERE"

HOME_NOW="$("$ROOT/tools/venv/bin/python" -I "$ROOT/tools/tv.py" \
  'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n 1 | tr -d '\r')"
echo "Home resolves to: $HOME_NOW"
case "$HOME_NOW" in
  com.gathin.tvhome/*) echo "Done. Press Home on the remote." ;;
  *) echo "ERROR: Home is not the new launcher; run examples/anker-nebula-d2426/restore.sh to undo." >&2; exit 1 ;;
esac
echo "Netflix here is the phone app: add it once from Home > App library (bottom pill) so the dock can show it."
