#!/usr/bin/env bash
# Put the Nebula D2426 back the way it was before cleanyourtv (needs ADB on, like the setup did).
#
#   TV_HOST=<projector ip> examples/anker-nebula-d2426/restore.sh                 # undo everything, keep the new launcher installed
#   TV_HOST=<projector ip> examples/anker-nebula-d2426/restore.sh --remove-launcher  # ...and uninstall it too
#
# 1. Re-enables every package in debloat.txt (tools/restore.sh replays the state recorded before the first
#    disable, so anything that was already off stays off) and hands Home back to the stock launcher.
# 2. Puts the Ambient screensaver back (it was com.google.android.backdrop/.Backdrop).
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the projector IP, e.g. TV_HOST=192.168.68.51 $0}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
PY="$ROOT/tools/venv/bin/python"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
tv() { "$PY" -I "$ROOT/tools/tv.py" "$@"; }

"$ROOT/tools/restore.sh" "$HERE/debloat.txt" com.google.android.tvlauncher

# One command per shell call: chained pm/settings commands can stop silently after the first.
tv "settings put secure screensaver_components com.google.android.backdrop/.Backdrop"

if [ "${1:-}" = "--remove-launcher" ]; then
  tv "pm uninstall com.gathin.tvhome"
fi

echo "Disabled packages now:"
tv "pm list packages -d"
echo "Home resolves to:"
tv "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME" | tail -n 1
