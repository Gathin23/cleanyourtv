#!/usr/bin/env bash
# Put theme folders on the TV for the Theme row in Launcher settings, and keep this TV's own keys apart from
# every theme. See themes/README.md, "Switching themes on the TV".
#
# Usage: TV_HOST=192.168.1.50 tools/theme-catalog.sh themes/aurora themes/glass ~/my-theme
#        TV_HOST=192.168.1.50 tools/theme-catalog.sh --device ~/my-tv/device.json   # keys laid over every theme
#        TV_HOST=192.168.1.50 tools/theme-catalog.sh --device none                  # remove device.json
#        TV_HOST=192.168.1.50 tools/theme-catalog.sh --clear                        # remove every catalog theme
#
# A theme folder lands in themes/<folder name>/ on the TV, replacing one of that name; the name is lower-case
# letters, digits, '-' and '_'. Adding themes changes nothing on screen until one is picked on the TV, so Home is
# not restarted. --device and --device none restart Home, because the key helpers read the keys when they start.
# --clear leaves the active theme and device.json alone.
#
# Exit status: 0 done; 1 a push or the restart failed on the TV; 2 nothing was sent - a command line this script
# cannot read, a folder without a valid config.json, a name it refuses, TV_HOST unset or no tools/venv.
set -euo pipefail
usage() {
  echo "usage: theme-catalog.sh <theme folder>... | --device <device.json>|none | --clear" >&2
  exit 2
}
[ "$#" -gt 0 ] || usage
MODE=themes
case "$1" in
  --clear) [ "$#" -eq 1 ] || usage; MODE=clear ;;
  --device) [ "$#" -eq 2 ] || usage; MODE=device ;;
  -*) usage ;;
esac
# Resolve folders and files against the caller's directory before moving into tools/ (as theme.sh does).
here="$(pwd -W 2>/dev/null || pwd)"
abs() { case "$1" in /*|[A-Za-z]:[\\/]*) printf '%s' "${1%/}" ;; *) printf '%s/%s' "$here" "${1%/}" ;; esac; }
cd "$(dirname "$0")"
if [ -z "${TV_HOST:-}" ]; then
  echo "set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 themes/aurora. Nothing was sent to the TV." >&2
  exit 2
fi
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt." \
  "Nothing was sent to the TV." >&2; exit 2; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
URI=content://$PKG.theme
TMP=/data/local/tmp/cyt-catalog
tv() { "$PY" -I tv.py "$@"; }
# The same write path as theme.sh: through the launcher's ThemeProvider first, so the file is the launcher's own
# (a file the shell creates under Android/data is one the launcher cannot open on Android 14), and cp where the
# firmware has no `content write`.
stage() { echo "$TMP-${1//\//-}"; }
put() { echo "rm -f $DIR/$1 2>/dev/null; { o=\$(content write --uri $URI/$1 < $(stage "$1") 2>&1) && case \"\$o\" in *'Unsupported operation'*|*'Error while accessing provider'*) false ;; esac; } || { mkdir -p $(dirname "$DIR/$1") 2>/dev/null; cp $(stage "$1") $DIR/$1 2>/dev/null; }; rm -f $(stage "$1")"; }
# A folder goes through the provider first too: on Android 14 the shell may not delete the launcher's files.
drop() { echo "content delete --uri $URI/$1 >/dev/null 2>&1; rm -rf $DIR/$1 2>/dev/null; true"; }
json_object() {
  "$PY" -I -c '
import json, sys
try:
    with open(sys.argv[1], encoding="utf-8-sig") as handle:
        print("object" if isinstance(json.load(handle), dict) else "not an object")
except (OSError, ValueError):
    print("invalid")
' "$1" 2>/dev/null
}

if [ "$MODE" = clear ]; then
  tv "$(drop themes)" >/dev/null
  echo "Removed the theme catalog. The active theme is unchanged."
  exit 0
fi

if [ "$MODE" = device ]; then
  if [ "$2" = none ]; then
    tv "$(drop device.json)" >/dev/null
  else
    FILE="$(abs "$2")"
    [ "$(json_object "$FILE")" = object ] || { echo "$2 is not a JSON object of theme keys." \
      "Nothing was sent to the TV." >&2; exit 2; }
    tv --push "$FILE" "$(stage device.json)" >/dev/null
    tv "$(put device.json)" >/dev/null
  fi
  "$PY" -I theme_restart.py
  echo "device.json is $([ "$2" = none ] && echo removed || echo on the TV)."
  exit 0
fi

# Every folder is checked before anything is sent, so a refused one cannot leave half a catalog behind.
for ARG in "$@"; do
  case "$ARG" in -*) usage ;; esac
  THEME="$(abs "$ARG")"
  NAME="$(basename "$THEME")"
  [[ "$NAME" =~ ^[a-z0-9][a-z0-9_-]{0,39}$ ]] || { echo "unsupported theme name: $NAME (lower-case letters," \
    "digits, '-' and '_'; rename the folder). Nothing was sent to the TV." >&2; exit 2; }
  [ "$(json_object "$THEME/config.json")" = object ] || { echo "no valid config.json in $ARG." \
    "Nothing was sent to the TV." >&2; exit 2; }
  if [ -d "$THEME/wallpapers" ]; then
    for IMAGE in "$THEME"/wallpapers/*; do
      [ -f "$IMAGE" ] || continue
      case "$IMAGE" in *.[jJ][pP][gG]|*.[jJ][pP][eE][gG]|*.[pP][nN][gG]|*.[wW][eE][bB][pP]) ;; *) continue ;; esac
      PIC="$(basename "$IMAGE")"
      if [[ ! "$PIC" =~ ^[A-Za-z0-9_.-]+$ || "$PIC" == *..* ]]; then
        echo "unsupported wallpaper filename: $ARG/wallpapers/$PIC. Nothing was sent to the TV." >&2
        exit 2
      fi
    done
  fi
done

for ARG in "$@"; do
  THEME="$(abs "$ARG")"
  NAME="$(basename "$THEME")"
  T="themes/$NAME"
  tv "$(drop "$T")" >/dev/null
  if [ -f "$THEME/wallpaper.jpg" ]; then
    "$PY" -I b64push.py "$THEME/wallpaper.jpg" "$(stage "$T/wallpaper.jpg")"
    tv "$(put "$T/wallpaper.jpg")" >/dev/null
  fi
  for FILE in "$THEME"/wallpapers/*; do
    [ -f "$FILE" ] || continue
    case "$FILE" in
      *.[jJ][pP][gG]|*.[jJ][pP][eE][gG]|*.[pP][nN][gG]|*.[wW][eE][bB][pP]|*/captions.txt) ;;
      *) continue ;;
    esac
    REL="$T/wallpapers/$(basename "$FILE")"
    "$PY" -I b64push.py "$FILE" "$(stage "$REL")"
    tv "$(put "$REL")" >/dev/null
  done
  # config.json last: the TV lists a theme only once its config.json is there.
  tv --push "$THEME/config.json" "$(stage "$T/config.json")" >/dev/null
  tv "$(put "$T/config.json")" >/dev/null
  echo "$NAME"
done
echo "Pick one in Launcher settings > Theme. Remove the catalog with: tools/theme-catalog.sh --clear"
