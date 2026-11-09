#!/usr/bin/env bash
# Put the TV back the way it was before tools/disable.sh, using the state recorded at that time:
# packages that were already disabled stay disabled, Home goes back to the component that owned it,
# and the animation scales go back to their recorded values. Pass the same list you ran disable with.
#
# Usage: TV_HOST=192.168.1.50 tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt [stock.launcher.package]
#
# Exit status: 0 the recorded states are back; 1 the restore did not finish, or the TV read back a package
# still disabled or another Home; 2 the command line is not a
# list plus an optional stock launcher package - no list word at all, an empty one, or a dash-led word
# that is a flag somewhere else - so nothing was sent to the TV.
set -euo pipefail
LIST="${1:-}"
if [ -z "$LIST" ]; then
  echo "restore.sh takes a debloat list: pass the same one you ran disable.sh with. Nothing was sent to the TV." >&2
  echo "usage: TV_HOST=<tv ip> tools/restore.sh <debloat list> [stock launcher package]" >&2
  exit 2
fi
# A dash-led word is a flag this script does not have - `--help`, `-h`, `--reset` copied over from
# theme.sh - and it was read as the list: recovery.py named a file called --help, and the run went on to
# contact the TV anyway, so the reader asking for the usage got "could not read --help (No such file or
# directory)" and a status that depended on whether the TV answered. The twin refuses these on its own
# (PowerShell reads a dash-led word as a parameter name), so this is the script that had to say it.
case "$LIST" in
  -*)
    echo "restore.sh takes a debloat list and an optional stock launcher package, and $LIST is a flag it does not have: nothing was sent to the TV." >&2
    echo "usage: TV_HOST=<tv ip> tools/restore.sh <debloat list> [stock launcher package]" >&2
    exit 2 ;;
esac
# The same word one place later. The second word is the optional stock launcher package, and the flag
# that gets pasted there - `--reset` copied over from theme.sh, `-h` - passed the list check because the
# list itself was fine; recovery.py then answered "not a package name: '--reset'" and exited 1, the status
# this script keeps for a restore that started and did not finish, with no usage line and nothing saying
# the TV was left alone, so a reader who typed a flag read it as a failed restore. restore.ps1 refuses it
# on its own: PowerShell reads a dash-led word as a parameter name.
case "${2:-}" in
  -*)
    echo "restore.sh takes a debloat list and an optional stock launcher package, and $2 is a flag it does not have. Nothing was sent to the TV." >&2
    echo "usage: TV_HOST=<tv ip> tools/restore.sh <debloat list> [stock launcher package]" >&2
    exit 2 ;;
esac
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
# plan never reaches the TV: recovery.py prints nothing until the whole plan is built, and set -e
# stops here with its status.
PLAN="$("$PY" -I tools/recovery.py --commands "$LIST" "$STOCK")"
printf '%s\n' "$PLAN" | "$PY" -I tools/tv.py --batch
# Read the TV back, as disable.sh does: tv.py --batch exits 0 when the TV refuses a line, so a pm enable
# or a Home the firmware did not take ended the run with 0. A package the plan enables that is still
# disabled for user 0, or a Home that does not resolve to the planned component, fails the run. A read
# that fails or gives no component only says the check did not run.
FAILED=""
WANT_ON="$(printf '%s\n' "$PLAN" | sed -n -E 's/^pm enable ([^[:space:]]+)$/\1/p')"
if [ -n "$WANT_ON" ]; then
  if OFF="$("$PY" -I tools/tv.py 'pm list packages -d --user 0' 2>/dev/null)"; then
    STILL_OFF="$(printf '%s\n' "$OFF" | tr -d '\r' | sed -n -E 's/^package:([^[:space:]]+).*/\1/p' \
      | grep -xF "$WANT_ON" || true)"
    if [ -n "$STILL_OFF" ]; then
      { echo "Still disabled after the restore, although the plan enables them:"
        printf '%s\n' "$STILL_OFF" | sed 's/^/  /'
        echo "The TV did not take pm enable for these (its answer is in the output above). Run"
        echo "tools/tv.py 'pm enable <package>' once by hand for each."; } >&2
      FAILED=1
    fi
  else
    echo "Could not check which packages are still disabled: compare 'pm list packages -d --user 0'" \
      "with the list by hand." >&2
  fi
fi
# Only a component the plan names; the no-record fallback asks the TV to resolve the stock launcher.
WANT_HOME="$(printf '%s\n' "$PLAN" \
  | sed -n -E 's/^cmd package set-home-activity --user 0 ([^$[:space:]][^[:space:]]*)$/\1/p' | tail -n 1)"
if [ -n "$WANT_HOME" ]; then
  NOW="$("$PY" -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
    2>/dev/null | tr -d '\r' | tail -n 1)" || NOW=""
  # pkg/pkg.Activity and pkg/.Activity are the same component.
  short() { p=${1%%/*}; a=${1#*/}; case "$1" in */"$p".*) a=".${a#"$p".}" ;; esac; printf '%s\n' "$p/$a"; }
  if ! printf '%s\n' "$NOW" | grep -qE '^[A-Za-z_][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+$'; then
    echo "Could not check Home: the TV did not name the activity that answers it. Check it with" \
      "tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'." >&2
  elif [ "$(short "$NOW")" != "$(short "$WANT_HOME")" ]; then
    echo "Home did not go back: the TV resolves $NOW, and the record has $WANT_HOME. Run" \
      "tools/tv.py 'cmd package set-home-activity --user 0 $WANT_HOME' once by hand." >&2
    FAILED=1
  fi
fi
[ -z "$FAILED" ] || exit 1
