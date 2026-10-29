#!/usr/bin/env bash
# Put an Epson LS800 back the way it came: re-enable every package in debloat.txt, give Home back to the
# stock launcher, and undo the extras from examples/epson-ls800/README.md (input names, theme).
# The Projector Operation Log toggle (Projector Settings > Information) is not touched; switch it back on
# there if you want it.
#
# Usage, from the repo root: TV_HOST=<projector ip> examples/epson-ls800/restore.sh [--uninstall-launcher]
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${TV_HOST:?set TV_HOST to the projector IP address}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
tv() { "$PY" -I tools/tv.py "$@"; }

tools/restore.sh examples/epson-ls800/debloat.txt com.google.android.tvlauncher
tools/theme.sh --reset || true
tv 'settings delete secure tv_input_custom_labels'
if [ "${1:-}" = --uninstall-launcher ]; then
  tv 'pm uninstall com.gathin.tvhome'
fi
tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1'
echo "Restored. Press Home on the remote; restart the projector if the old home screen does not come up."
