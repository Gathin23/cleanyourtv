#!/usr/bin/env bash
# Put the TV back the way it was before tools/disable.sh, using the state recorded at that time:
# packages that were already disabled stay disabled, Home goes back to the component that owned it,
# and the animation scales go back to their recorded values. Pass the same list you ran disable with.
#
# Usage: TV_HOST=192.168.1.50 tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt [stock.launcher.package]
#
# Exit status: 0 the recorded states are back; 1 the restore did not finish; 2 the command line is not a
# list plus an optional stock launcher package, so nothing was sent to the TV.
set -euo pipefail
LIST="${1:?usage: restore.sh <debloat list> [stock launcher package]}"
# A list and the optional stock launcher, and nothing else. Only the first two words were read, so a
# line with a word appended - the stock package named twice, a second list path, `--reset` copied over
# from theme.sh - restored the TV while that word was dropped without a word, on the one script an
# owner runs when the TV already looks wrong. restore.ps1 refuses it on its own (PowerShell binds two
# positional arguments), so this is the twin that had to say it; recovery.py answers the same words
# with the same reading when a caller hands them to it directly.
if [ "$#" -gt 2 ]; then
  shift 2
  echo "restore.sh takes a debloat list and an optional stock launcher package: $* would not have been" \
    "used, so this is not a command line it can read. Nothing was sent to the TV." >&2
  exit 2
fi
# Resolve the list against the caller's directory before moving into the repo. Git Bash's pwd says
# /c/Users/..., which the Windows Python cannot open with MSYS_NO_PATHCONV=1 set; pwd -W gives
# C:/Users/..., the same path (and so the same record) as the PowerShell scripts.
# A folder that is not there keeps the path as typed. A list path one folder short - the reader typed
# examples/my-tv where the folder is examples/my-tv-android-11 - used to end right here: bash printed
# "cd: examples/my-tv: No such file or directory" with this script's own line number, named a folder
# where the reader had typed a list, and exited 1, while recovery.py's "could not read <list> (No such
# file or directory). Nothing was sent to the TV" - the message written for exactly this - was never
# reached. The pwd -W form is still used when the folder is there, which is what keeps the record's key
# the same; the PowerShell twin's Resolve-Path already names the list.
if LIST_DIR="$(cd "$(dirname "$LIST")" 2>/dev/null && { pwd -W 2>/dev/null || pwd; })"; then
  LIST="$LIST_DIR/$(basename "$LIST")"
fi
cd "$(dirname "$0")/.."
STOCK="${2:-com.google.android.tvlauncher}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
# The same hint as disable.sh. Without it, a missing venv fell through to the Windows path above, and
# bash only said tools/venv/Scripts/python.exe was not there, once for each side of the pipe below.
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
# recovery.py reads and validates the list itself, prints the batch for tv.py and says on stderr what
# it will leave alone. Without a record it warns and falls back to enabling the whole list. A refused
# plan never reaches the TV because recovery.py prints nothing until the whole plan is built, so
# tv.py reads an empty batch; pipefail only makes this script exit non-zero afterwards.
"$PY" -I tools/recovery.py --commands "$LIST" "$STOCK" | "$PY" -I tools/tv.py --batch
