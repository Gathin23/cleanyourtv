#!/usr/bin/env bash
# Send Game Day its calendars from a computer, instead of typing long links with the remote. One calendar per line
# in calendars.txt beside this script (or the file you name): "sport | name | link", optionally "| colour". It
# replaces the list on the TV, waits while the TV fetches each calendar, and prints how each one loaded. Links are
# never printed. Install the add-on first (README.md).
#
# Usage: TV_HOST=192.168.1.50 addons/game-day/setup.sh                    # calendars.txt from this folder
#        TV_HOST=192.168.1.50 addons/game-day/setup.sh ~/my-teams.txt     # any calendars file
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
ARG="${1:-}"
case "$ARG" in
  "") FILE="$HERE/calendars.txt" ;;
  /*|?:*) FILE="$ARG" ;;
  *) FILE="$(pwd -W 2>/dev/null || pwd)/$ARG" ;;
esac
[ -f "$FILE" ] || { echo "no calendars file $FILE (copy calendars.example.txt to calendars.txt and edit it)" >&2; exit 1; }
cd "$(dirname "$0")/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.justinnewman.gameday
URI=content://$PKG.files
TMP=/data/local/tmp/gameday-calendars.txt
tv() { "$PY" -I tv.py "$@"; }

tv "pm path $PKG; true" | grep -q package: || { echo "Game Day is not on the TV: build and install it first (README.md)" >&2; exit 1; }
echo "Game Day is installed."
# TCL firmware (TclAppBoot) refuses to start an app for another app's request without its AUTO_START app-op, so
# neither this script nor Home could reach Game Day. Allow it where the op exists; elsewhere this does nothing.
tv "appops set $PKG AUTO_START allow 2>/dev/null; true" >/dev/null
# Git Bash: the venv's Windows Python needs a Windows path for the file.
"$PY" -I b64push.py "$(cygpath -w "$FILE" 2>/dev/null || printf '%s' "$FILE")" "$TMP" | grep -q md5 \
  || { echo "sending the calendars failed" >&2; exit 1; }
tv "content write --uri $URI/calendars.txt < $TMP; rm -f $TMP" >/dev/null

echo "Checking the calendars on the TV:"
RESULT=
for _ in $(seq 1 45); do
  sleep 2
  RESULT="$(tv "content read --uri $URI/result.txt 2>/dev/null; true" | tr -d '\r')"
  [ "${RESULT##*$'\n'}" = done ] && break
done
[ "${RESULT##*$'\n'}" = done ] || { echo "the TV did not finish in 90 seconds: open Game Day on the TV to see each calendar" >&2; exit 1; }

STATUS=0
N=0
while IFS= read -r LINE; do
  IFS='|' read -r KIND A B C D <<< "$LINE"
  case "$KIND" in
    ok) E=entries; [ "$C" = 1 ] && E=entry
        case "$D" in nothing*) ;; *) D="next $D" ;; esac
        printf '  %-22s %-14s \033[32mok\033[0m  %s %s, %s\n' "$A" "$B" "$C" "$E" "$D"; N=$((N + 1)) ;;
    fail) printf '  %-22s %-14s \033[31m%s\033[0m\n' "$A" "$B" "$C"; N=$((N + 1)); STATUS=1 ;;
    error) printf '  \033[31m%s\033[0m\n' "${LINE#error|*|}"; STATUS=1 ;;
  esac
done <<< "$RESULT"
if grep -q '^error|' <<< "$RESULT"; then
  echo "Nothing changed on the TV."
else
  echo "Sent $N calendars to the TV. Links are not printed."
fi
exit "$STATUS"
